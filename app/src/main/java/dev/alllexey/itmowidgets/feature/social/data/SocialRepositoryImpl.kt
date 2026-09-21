package dev.alllexey.itmowidgets.feature.social.data

import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.model.ApiResponse
import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.model.toUserSummary
import dev.alllexey.itmowidgets.core.model.social.UserLookupRequest
import dev.alllexey.itmowidgets.core.network.toAppError
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.social.FriendRequests
import dev.alllexey.itmowidgets.core.social.SocialRepository
import dev.alllexey.itmowidgets.core.social.SocialState
import dev.alllexey.itmowidgets.core.model.social.UserProfile as CoreUserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

class SocialRepositoryImpl @Inject constructor(
    private val customServices: CustomServicesRepository,
    private val widgetsApi: ItmoWidgetsApi
) : SocialRepository, SessionDataCleaner {

    private val friends = MutableStateFlow<SocialState<List<UserProfile>>>(SocialState.Loading)
    private val requests = MutableStateFlow<SocialState<FriendRequests>>(SocialState.Loading)
    private val currentUser = MutableStateFlow<UserSummary?>(null)
    private val profiles = ConcurrentHashMap<Int, UserProfile>()
    private val userFriendsCache = ConcurrentHashMap<Int, List<UserProfile>>()

    override fun observeFriends(): Flow<SocialState<List<UserProfile>>> = friends.asStateFlow()

    override fun observeRequests(): Flow<SocialState<FriendRequests>> = requests.asStateFlow()

    override fun observeCurrentUser(): Flow<UserSummary?> = currentUser.asStateFlow()

    override val currentFriends: List<UserProfile>?
        get() = (friends.value as? SocialState.Content)?.value

    override fun cachedProfile(isu: Int): UserProfile? {
        profiles[isu]?.let { return it }
        val requestLists = (requests.value as? SocialState.Content)?.value
        return currentFriends?.firstOrNull { it.isu == isu }
            ?: requestLists?.incoming?.firstOrNull { it.isu == isu }
            ?: requestLists?.outgoing?.firstOrNull { it.isu == isu }
    }

    override fun cachedUserFriends(isu: Int): List<UserProfile>? = userFriendsCache[isu]

    override suspend fun refresh() {
        if (!customServices.isEnabled()) {
            currentUser.value = null
            friends.value = SocialState.Disabled
            requests.value = SocialState.Disabled
            return
        }

        coroutineScope {
            // The own profile only decorates screens: its failure must not hide the lists.
            val profile = async { call { widgetsApi.myUserData() }.valueOrNull()?.toUserSummary() }
            val incoming = async { call { widgetsApi.incomingFriendRequests() } }
            val outgoing = async { call { widgetsApi.outgoingFriendRequests() } }
            val friendList = call { widgetsApi.friends() }

            friends.value = friendList.toState { list -> list.map(CoreUserProfile::toModel) }
            requests.value = combineRequests(incoming.await(), outgoing.await())
            currentUser.value = profile.await()
        }
    }

    override suspend fun userFriends(isu: Int): AppResult<List<UserProfile>> = gated {
        call { widgetsApi.userFriends(isu) }.map { list -> list.map(CoreUserProfile::toModel) }
    }.also { result -> if (result is AppResult.Success) userFriendsCache[isu] = result.value }

    override suspend fun profile(isu: Int): AppResult<UserProfile> {
        return gated { call { widgetsApi.userProfile(isu) }.map(CoreUserProfile::toModel) }
            .also { result -> if (result is AppResult.Success) profiles[isu] = result.value }
    }

    override suspend fun lookup(isus: List<Int>): AppResult<List<UserProfile>> {
        val distinct = isus.distinct()
        if (distinct.isEmpty()) return AppResult.Success(emptyList())
        return gated {
            val found = mutableListOf<UserProfile>()
            for (chunk in distinct.chunked(LOOKUP_CHUNK)) {
                when (val page = call { widgetsApi.lookupUsers(UserLookupRequest(chunk)) }) {
                    is AppResult.Success -> found += page.value.users.map(CoreUserProfile::toModel)
                    is AppResult.Failure -> return@gated page
                }
            }
            AppResult.Success(found)
        }
    }

    override suspend fun sendRequest(isu: Int) = act { widgetsApi.sendFriendRequest(isu) }

    override suspend fun acceptRequest(isu: Int) = act { widgetsApi.acceptFriendRequest(isu) }

    override suspend fun rejectRequest(isu: Int) = act { widgetsApi.rejectFriendRequest(isu) }

    override suspend fun cancelRequest(isu: Int) = act { widgetsApi.cancelFriendRequest(isu) }

    override suspend fun removeFriend(isu: Int) = act { widgetsApi.removeFriend(isu) }

    override suspend fun clearSessionData() {
        profiles.clear()
        userFriendsCache.clear()
        currentUser.value = null
        friends.value = SocialState.Loading
        requests.value = SocialState.Loading
    }

    private suspend fun act(request: suspend () -> ApiResponse<CoreUserProfile>): AppResult<UserProfile> {
        val result = gated { call(request).map(CoreUserProfile::toModel) }
        if (result is AppResult.Success) applyRelationship(result.value)
        return result
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

    private suspend fun <T> gated(block: suspend () -> AppResult<T>): AppResult<T> {
        if (!customServices.isEnabled()) return AppResult.Failure(AppError.CustomServicesDisabled)
        return block()
    }

    private suspend fun <T> call(request: suspend () -> ApiResponse<T>): AppResult<T> {
        return try {
            val data = withContext(Dispatchers.IO) { request().data }
            if (data != null) {
                AppResult.Success(data)
            } else {
                AppResult.Failure(IllegalStateException("Backend returned no data").toAppError())
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            AppResult.Failure(error.toAppError())
        }
    }

    private fun combineRequests(
        incoming: AppResult<List<CoreUserProfile>>,
        outgoing: AppResult<List<CoreUserProfile>>
    ): SocialState<FriendRequests> {
        val error = (incoming as? AppResult.Failure)?.error ?: (outgoing as? AppResult.Failure)?.error
        if (error != null) return SocialState.Error(error)
        return SocialState.Content(
            FriendRequests(
                incoming = (incoming as AppResult.Success).value.map(CoreUserProfile::toModel),
                outgoing = (outgoing as AppResult.Success).value.map(CoreUserProfile::toModel)
            )
        )
    }

    private fun <T, R> AppResult<T>.toState(transform: (T) -> R): SocialState<R> = when (this) {
        is AppResult.Success -> SocialState.Content(transform(value))
        is AppResult.Failure -> SocialState.Error(error)
    }

    private fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
        is AppResult.Success -> AppResult.Success(transform(value))
        is AppResult.Failure -> this
    }

    private fun <T> AppResult<T>.valueOrNull(): T? = (this as? AppResult.Success)?.value

    private fun <T> SocialState<T>.update(transform: (T) -> T): SocialState<T> =
        if (this is SocialState.Content) SocialState.Content(transform(value)) else this

    private fun List<UserProfile>.without(isu: Int) = filterNot { it.isu == isu }

    private fun List<UserProfile>.withIf(profile: UserProfile, include: Boolean) =
        if (include) listOf(profile) + this else this

    private companion object {
        const val LOOKUP_CHUNK = 50
    }
}
