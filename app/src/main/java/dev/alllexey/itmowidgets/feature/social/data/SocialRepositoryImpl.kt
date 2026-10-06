package dev.alllexey.itmowidgets.feature.social.data

import dev.alllexey.itmowidgets.client.friends.FriendsApi
import dev.alllexey.itmowidgets.client.users.UserLookupRequest
import dev.alllexey.itmowidgets.client.users.UsersApi
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.coroutines.ApplicationScope
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.model.toUserSummary
import dev.alllexey.itmowidgets.core.network.appResultOf
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.result.valueOrNull
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.social.FriendRequests
import dev.alllexey.itmowidgets.core.social.SocialRepository
import dev.alllexey.itmowidgets.feature.social.data.demo.DemoSocial
import dev.alllexey.itmowidgets.client.common.UserProfile as ClientUserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SocialRepositoryImpl @Inject constructor(
    private val backend: BackendGate,
    private val users: UsersApi,
    private val friendships: FriendsApi,
    @param:ApplicationScope private val scope: CoroutineScope,
    private val demo: DemoMode,
    private val dispatchers: AppDispatchers
) : SocialRepository, SessionDataCleaner {

    private val friends = MutableStateFlow<LoadState<List<UserProfile>>>(LoadState.Loading)
    private val requests = MutableStateFlow<LoadState<FriendRequests>>(LoadState.Loading)
    private val currentUser = MutableStateFlow<UserSummary?>(null)
    private val profiles = ConcurrentHashMap<Int, UserProfile>()
    private val userFriendsCache = ConcurrentHashMap<Int, List<UserProfile>>()
    private val cacheLock = Any()
    private var cacheGeneration = 0L
    @Volatile private var enabled: Boolean? = null

    init {
        scope.launch {
            backend.observeConnected().collect { on ->
                synchronized(cacheLock) {
                    updateEnabled(on)
                }
            }
        }
    }

    override fun observeFriends(): Flow<LoadState<List<UserProfile>>> = friends.asStateFlow()

    override fun observeRequests(): Flow<LoadState<FriendRequests>> = requests.asStateFlow()

    override fun observeCurrentUser(): Flow<UserSummary?> = currentUser.asStateFlow()

    override val currentFriends: List<UserProfile>?
        get() = synchronized(cacheLock) {
            if (enabled == true) (friends.value as? LoadState.Content)?.value else null
        }

    override fun cachedProfile(isu: Int): UserProfile? = synchronized(cacheLock) {
        if (enabled != true) return@synchronized null
        profiles[isu]?.let { return@synchronized it }
        val requestLists = (requests.value as? LoadState.Content)?.value
        currentFriends?.firstOrNull { it.isu == isu }
            ?: requestLists?.incoming?.firstOrNull { it.isu == isu }
            ?: requestLists?.outgoing?.firstOrNull { it.isu == isu }
    }

    override fun cachedUserFriends(isu: Int): List<UserProfile>? = synchronized(cacheLock) {
        if (enabled == true) userFriendsCache[isu] else null
    }

    override suspend fun refresh() {
        if (demo.isActive()) {
            synchronized(cacheLock) {
                friends.value = LoadState.Content(DemoSocial.friends())
                requests.value = LoadState.Content(DemoSocial.requests())
                currentUser.value = DemoSocial.me
            }
            return
        }
        val generation = beginRequest() ?: return

        coroutineScope {
            // The own profile only decorates screens: its failure must not hide the lists.
            val profile = async { call(generation) { users.myUserData() }.valueOrNull()?.toUserSummary() }
            val incoming = async { call(generation) { friendships.incomingFriendRequests() } }
            val outgoing = async { call(generation) { friendships.outgoingFriendRequests() } }
            val friendList = call(generation) { friendships.friends() }

            val friendState = friendList.toState { list -> list.map(ClientUserProfile::toModel) }
            publish(generation) { friends.value = friendState }
            val requestState = combineRequests(incoming.await(), outgoing.await())
            publish(generation) { requests.value = requestState }
            val user = profile.await()
            publish(generation) { currentUser.value = user }
        }
    }

    override suspend fun userFriends(isu: Int): AppResult<List<UserProfile>> =
        if (demo.isActive()) demoAnswer(DemoSocial.userFriends(isu)) { userFriendsCache[isu] = it } else gated(onSuccess = { userFriendsCache[isu] = it }) { generation ->
            call(generation) { users.userFriends(isu) }.map { list -> list.map(ClientUserProfile::toModel) }
        }

    override suspend fun profile(isu: Int): AppResult<UserProfile> =
        if (demo.isActive()) demoAnswer(DemoSocial.profile(isu)) { profiles[isu] = it } else gated(onSuccess = { profiles[isu] = it }) { generation ->
            call(generation) { users.userProfile(isu) }.map(ClientUserProfile::toModel)
        }

    override suspend fun lookup(isus: List<Int>): AppResult<List<UserProfile>> {
        val distinct = isus.distinct()
        if (distinct.isEmpty()) return AppResult.Success(emptyList())
        if (demo.isActive()) return AppResult.Success(DemoSocial.lookup(distinct))
        return gated { generation ->
            val found = mutableListOf<UserProfile>()
            for (chunk in distinct.chunked(LOOKUP_CHUNK)) {
                when (val page = call(generation) { users.lookupUsers(UserLookupRequest(chunk)) }) {
                    is AppResult.Success -> found += page.value.users.map(ClientUserProfile::toModel)
                    is AppResult.Failure -> return@gated page
                }
            }
            AppResult.Success(found)
        }
    }

    override suspend fun sendRequest(isu: Int) = act { friendships.sendFriendRequest(isu) }

    override suspend fun acceptRequest(isu: Int) = act { friendships.acceptFriendRequest(isu) }

    override suspend fun rejectRequest(isu: Int) = act { friendships.rejectFriendRequest(isu) }

    override suspend fun cancelRequest(isu: Int) = act { friendships.cancelFriendRequest(isu) }

    override suspend fun removeFriend(isu: Int) = act { friendships.removeFriend(isu) }

    override suspend fun clearSessionData() {
        synchronized(cacheLock) {
            clearBackendCaches()
            friends.value = LoadState.Loading
            requests.value = LoadState.Loading
        }
    }

    private suspend fun act(request: suspend () -> ClientUserProfile): AppResult<UserProfile> =
        if (demo.isActive()) AppResult.Failure(AppError.DemoUnavailable) else gated(onSuccess = ::applyRelationship) { generation ->
            call(generation, request).map(ClientUserProfile::toModel)
        }

    /** Moves [profile] into the list its relationship belongs to; other lists drop it. */
    private fun applyRelationship(profile: UserProfile) {
        profiles[profile.isu] = profile
        friends.value = friends.value.update { list ->
            list.without(profile.isu).withIf(profile, profile.relationship == RelationshipState.FRIENDS)
        }
        requests.value = requests.value.update { current ->
            FriendRequests(
                incoming = current.incoming.without(profile.isu)
                    .withIf(profile, profile.relationship == RelationshipState.INCOMING),
                outgoing = current.outgoing.without(profile.isu)
                    .withIf(profile, profile.relationship == RelationshipState.OUTGOING)
            )
        }
    }

    /** A demo answer: a person outside the demo set is unknown to Backend. */
    private fun <T> demoAnswer(value: T?, onSuccess: (T) -> Unit): AppResult<T> {
        if (value == null) return AppResult.Failure(AppError.NotFound)
        synchronized(cacheLock) { onSuccess(value) }
        return AppResult.Success(value)
    }

    private suspend fun <T> gated(
        onSuccess: (T) -> Unit = {},
        block: suspend (Long) -> AppResult<T>
    ): AppResult<T> {
        val generation = beginRequest() ?: return AppResult.Failure(AppError.CustomServicesDisabled)
        val result = block(generation)
        return synchronized(cacheLock) {
            if (!isCurrent(generation)) AppResult.Failure(AppError.CustomServicesDisabled)
            else result.also { if (it is AppResult.Success) onSuccess(it.value) }
        }
    }

    private suspend fun beginRequest(): Long? {
        val (generation, wasEnabled) = synchronized(cacheLock) { cacheGeneration to enabled }
        val on = backend.mayCallBackend()
        return synchronized(cacheLock) {
            // An older opt-in read must neither revive cleared data nor clear a newer connection.
            if (generation != cacheGeneration || (enabled != wasEnabled && on != enabled)) {
                return@synchronized null
            }
            updateEnabled(on)
            if (on) cacheGeneration else null
        }
    }

    private fun updateEnabled(on: Boolean) {
        enabled = on
        if (!on) forgetBackendCaches()
    }

    private fun isCurrent(generation: Long): Boolean = synchronized(cacheLock) {
        enabled == true && generation == cacheGeneration
    }

    private fun publish(generation: Long, update: () -> Unit) = synchronized(cacheLock) {
        if (isCurrent(generation)) update()
    }

    /** Called under [cacheLock], together with the opt-in state change. */
    private fun forgetBackendCaches() {
        clearBackendCaches()
        friends.value = LoadState.Disabled
        requests.value = LoadState.Disabled
    }

    private fun clearBackendCaches() {
        cacheGeneration += 1
        profiles.clear()
        userFriendsCache.clear()
        currentUser.value = null
    }

    /** Core 2.0 fails an answer without data as a broken contract, which maps to [AppError.Unknown]. */
    private suspend fun <T> call(generation: Long, request: suspend () -> T): AppResult<T> =
        withContext(dispatchers.io) {
            if (!isCurrent(generation)) return@withContext AppResult.Failure(AppError.CustomServicesDisabled)
            appResultOf { request() }
        }

    private fun combineRequests(
        incoming: AppResult<List<ClientUserProfile>>,
        outgoing: AppResult<List<ClientUserProfile>>
    ): LoadState<FriendRequests> {
        val error = (incoming as? AppResult.Failure)?.error ?: (outgoing as? AppResult.Failure)?.error
        if (error != null) return LoadState.Error(error)
        return LoadState.Content(
            FriendRequests(
                incoming = (incoming as AppResult.Success).value.map(ClientUserProfile::toModel),
                outgoing = (outgoing as AppResult.Success).value.map(ClientUserProfile::toModel)
            )
        )
    }

    private fun <T, R> AppResult<T>.toState(transform: (T) -> R): LoadState<R> = when (this) {
        is AppResult.Success -> LoadState.Content(transform(value))
        is AppResult.Failure -> LoadState.Error(error)
    }

    private fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
        is AppResult.Success -> AppResult.Success(transform(value))
        is AppResult.Failure -> this
    }

    private fun <T> LoadState<T>.update(transform: (T) -> T): LoadState<T> =
        if (this is LoadState.Content) LoadState.Content(transform(value)) else this

    private fun List<UserProfile>.without(isu: Int) = filterNot { it.isu == isu }

    private fun List<UserProfile>.withIf(profile: UserProfile, include: Boolean) =
        if (include) listOf(profile) + this else this

    private companion object {
        const val LOOKUP_CHUNK = 50
    }
}
