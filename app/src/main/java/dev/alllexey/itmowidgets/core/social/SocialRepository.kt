package dev.alllexey.itmowidgets.core.social

import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import kotlinx.coroutines.flow.Flow

/** A cached backend collection: unknown until the first refresh, then content or a typed failure. */
sealed interface SocialState<out T> {
    data object Loading : SocialState<Nothing>

    /** Custom services are switched off, so nothing was requested. */
    data object Disabled : SocialState<Nothing>

    data class Content<T>(val value: T) : SocialState<T>

    data class Error(val error: AppError) : SocialState<Nothing>
}

data class FriendRequests(
    val incoming: List<UserProfile>,
    val outgoing: List<UserProfile>
) {
    companion object {
        val EMPTY = FriendRequests(emptyList(), emptyList())
    }
}

/**
 * Friends, requests and public profiles from ITMO.Widgets Backend.
 *
 * Every call is gated on the custom-services opt-in. Relationship actions return the
 * fresh profile and fold it into the cached lists, so screens do not need to refresh
 * after acting.
 */
interface SocialRepository {

    fun observeFriends(): Flow<SocialState<List<UserProfile>>>

    fun observeRequests(): Flow<SocialState<FriendRequests>>

    /** The signed-in user as Backend sees it, or `null` while unknown. */
    fun observeCurrentUser(): Flow<UserSummary?>

    /** The last loaded friend list, for callers that cannot collect a flow. */
    val currentFriends: List<UserProfile>?

    suspend fun refresh()

    /** Accepted friends of [isu], with access and each profile scoped by Backend. */
    suspend fun userFriends(isu: Int): AppResult<List<UserProfile>>

    suspend fun profile(isu: Int): AppResult<UserProfile>

    /** Registered users among [isus], in request order; unknown ISUs are omitted. */
    suspend fun lookup(isus: List<Int>): AppResult<List<UserProfile>>

    suspend fun sendRequest(isu: Int): AppResult<UserProfile>

    suspend fun acceptRequest(isu: Int): AppResult<UserProfile>

    suspend fun rejectRequest(isu: Int): AppResult<UserProfile>

    suspend fun cancelRequest(isu: Int): AppResult<UserProfile>

    suspend fun removeFriend(isu: Int): AppResult<UserProfile>
}
