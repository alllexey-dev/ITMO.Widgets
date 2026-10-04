package dev.alllexey.itmowidgets.core.friend

import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.result.LoadState
import kotlinx.coroutines.flow.Flow

interface FriendRepository {

    fun observeFriendList(): Flow<LoadState<List<UserSummary>>>

    /**
     * The signed-in user, or `null` while it is unknown — for example when custom
     * services are disabled or the profile request failed.
     */
    fun observeCurrentUser(): Flow<UserSummary?>

    suspend fun refreshFriendList()

    val currentFriends: List<UserSummary>?
}
