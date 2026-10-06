package dev.alllexey.itmowidgets.feature.social.data

import dev.alllexey.itmowidgets.client.friends.FriendsApi
import dev.alllexey.itmowidgets.client.users.UserLookupRequest
import dev.alllexey.itmowidgets.client.users.UsersApi
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.model.toUserSummary
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.result.appResultOf
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Friends, requests and the people seen through Backend. Every cache, the opt-in state and the request generation
 * live in one immutable [SocialCache] that changes only through `update {}`: the non-suspend reads
 * ([currentFriends], [cachedProfile], [cachedUserFriends]) see one consistent snapshot without a lock, and a
 * response of an older generation never lands after an opt-out or a sign-out.
 *
 * [scope] is the application scope: the opt-in is followed for the whole process.
 */
class SocialRepositoryImpl(
    private val backend: BackendGate,
    private val users: UsersApi,
    private val friendships: FriendsApi,
    private val scope: CoroutineScope,
    private val demo: DemoMode,
    private val dispatchers: AppDispatchers
) : SocialRepository, SessionDataCleaner {

    private val cache = MutableStateFlow(SocialCache())

    init {
        scope.launch {
            backend.observeConnected().collect { on -> cache.update { it.withEnabled(on) } }
        }
    }

    override fun observeFriends(): Flow<LoadState<List<UserProfile>>> =
        cache.map { it.friends }.distinctUntilChanged()

    override fun observeRequests(): Flow<LoadState<FriendRequests>> =
        cache.map { it.requests }.distinctUntilChanged()

    override fun observeCurrentUser(): Flow<UserSummary?> = cache.map { it.currentUser }.distinctUntilChanged()

    override val currentFriends: List<UserProfile>?
        get() = cache.value.currentFriends

    override fun cachedProfile(isu: Int): UserProfile? {
        val snapshot = cache.value
        if (snapshot.enabled != true) return null
        snapshot.profiles[isu]?.let { return it }
        val requestLists = (snapshot.requests as? LoadState.Content)?.value
        return snapshot.currentFriends?.firstOrNull { it.isu == isu }
            ?: requestLists?.incoming?.firstOrNull { it.isu == isu }
            ?: requestLists?.outgoing?.firstOrNull { it.isu == isu }
    }

    override fun cachedUserFriends(isu: Int): List<UserProfile>? =
        cache.value.let { if (it.enabled == true) it.userFriends[isu] else null }

    override suspend fun refresh() {
        if (demo.isActive()) {
            cache.update {
                it.copy(
                    friends = LoadState.Content(DemoSocial.friends()),
                    requests = LoadState.Content(DemoSocial.requests()),
                    currentUser = DemoSocial.me
                )
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
            publish(generation) { it.copy(friends = friendState) }
            val requestState = combineRequests(incoming.await(), outgoing.await())
            publish(generation) { it.copy(requests = requestState) }
            val user = profile.await()
            publish(generation) { it.copy(currentUser = user) }
        }
    }

    override suspend fun userFriends(isu: Int): AppResult<List<UserProfile>> {
        val remember = { state: SocialCache, list: List<UserProfile> ->
            state.copy(userFriends = state.userFriends + (isu to list))
        }
        if (demo.isActive()) return demoAnswer(DemoSocial.userFriends(isu), remember)
        return gated(remember) { generation ->
            call(generation) { users.userFriends(isu) }.map { list -> list.map(ClientUserProfile::toModel) }
        }
    }

    override suspend fun profile(isu: Int): AppResult<UserProfile> {
        val remember = { state: SocialCache, profile: UserProfile ->
            state.copy(profiles = state.profiles + (isu to profile))
        }
        if (demo.isActive()) return demoAnswer(DemoSocial.profile(isu), remember)
        return gated(remember) { generation ->
            call(generation) { users.userProfile(isu) }.map(ClientUserProfile::toModel)
        }
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
        cache.update { it.withoutBackendData().copy(friends = LoadState.Loading, requests = LoadState.Loading) }
    }

    private suspend fun act(request: suspend () -> ClientUserProfile): AppResult<UserProfile> {
        if (demo.isActive()) return AppResult.Failure(AppError.DemoUnavailable)
        return gated(SocialCache::withRelationship) { generation ->
            call(generation, request).map(ClientUserProfile::toModel)
        }
    }

    /** A demo answer: a person outside the demo set is unknown to Backend. */
    private fun <T> demoAnswer(value: T?, remember: (SocialCache, T) -> SocialCache): AppResult<T> {
        if (value == null) return AppResult.Failure(AppError.NotFound)
        cache.update { remember(it, value) }
        return AppResult.Success(value)
    }

    /**
     * Runs [block] in the generation [beginRequest] admits and folds a success into the cache with [remember],
     * unless an opt-out or a sign-out started a newer generation meanwhile.
     */
    private suspend fun <T> gated(
        remember: (SocialCache, T) -> SocialCache = { state, _ -> state },
        block: suspend (Long) -> AppResult<T>
    ): AppResult<T> {
        val generation = beginRequest() ?: return AppResult.Failure(AppError.CustomServicesDisabled)
        val result = block(generation)
        var current = false
        cache.update { state ->
            current = state.isCurrent(generation)
            if (current && result is AppResult.Success) remember(state, result.value) else state
        }
        return if (current) result else AppResult.Failure(AppError.CustomServicesDisabled)
    }

    /** The generation a Backend request may use, or null when the opt-in is off or changed during the read. */
    private suspend fun beginRequest(): Long? {
        val before = cache.value
        val on = backend.mayCallBackend()
        var admitted: Long? = null
        cache.update { state ->
            admitted = null
            // An older opt-in read must neither revive cleared data nor clear a newer connection.
            if (state.generation != before.generation || (state.enabled != before.enabled && on != state.enabled)) {
                return@update state
            }
            state.withEnabled(on).also { if (on) admitted = it.generation }
        }
        return admitted
    }

    private fun publish(generation: Long, change: (SocialCache) -> SocialCache) {
        cache.update { state -> if (state.isCurrent(generation)) change(state) else state }
    }

    /** Core 2.0 fails an answer without data as a broken contract, which maps to [AppError.Unknown]. */
    private suspend fun <T> call(generation: Long, request: suspend () -> T): AppResult<T> =
        withContext(dispatchers.io) {
            if (!cache.value.isCurrent(generation)) return@withContext AppResult.Failure(AppError.CustomServicesDisabled)
            appResultOf(Exception::toAppError) { request() }
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

    private companion object {
        const val LOOKUP_CHUNK = 50
    }
}

/**
 * One consistent state of [SocialRepositoryImpl]. [enabled] is the last opt-in seen (null before the first);
 * [generation] grows whenever Backend data is forgotten, so a response of an older generation is dropped.
 */
private data class SocialCache(
    val enabled: Boolean? = null,
    val generation: Long = 0,
    val friends: LoadState<List<UserProfile>> = LoadState.Loading,
    val requests: LoadState<FriendRequests> = LoadState.Loading,
    val currentUser: UserSummary? = null,
    val profiles: Map<Int, UserProfile> = emptyMap(),
    val userFriends: Map<Int, List<UserProfile>> = emptyMap(),
) {
    val currentFriends: List<UserProfile>?
        get() = if (enabled == true) (friends as? LoadState.Content)?.value else null

    fun isCurrent(generation: Long): Boolean = enabled == true && generation == this.generation

    /** The opt-in [on]; turning it off forgets every Backend cache and disables the lists. */
    fun withEnabled(on: Boolean): SocialCache =
        if (on) copy(enabled = true)
        else withoutBackendData().copy(enabled = false, friends = LoadState.Disabled, requests = LoadState.Disabled)

    fun withoutBackendData(): SocialCache =
        copy(generation = generation + 1, profiles = emptyMap(), userFriends = emptyMap(), currentUser = null)

    /** Moves [profile] into the list its relationship belongs to; other lists drop it. */
    fun withRelationship(profile: UserProfile): SocialCache = copy(
        profiles = profiles + (profile.isu to profile),
        friends = friends.updated { list ->
            list.without(profile.isu).withIf(profile, profile.relationship == RelationshipState.FRIENDS)
        },
        requests = requests.updated { current ->
            FriendRequests(
                incoming = current.incoming.without(profile.isu)
                    .withIf(profile, profile.relationship == RelationshipState.INCOMING),
                outgoing = current.outgoing.without(profile.isu)
                    .withIf(profile, profile.relationship == RelationshipState.OUTGOING)
            )
        }
    )

    private fun <T> LoadState<T>.updated(transform: (T) -> T): LoadState<T> =
        if (this is LoadState.Content) LoadState.Content(transform(value)) else this

    private fun List<UserProfile>.without(isu: Int) = filterNot { it.isu == isu }

    private fun List<UserProfile>.withIf(profile: UserProfile, include: Boolean) =
        if (include) listOf(profile) + this else this
}
