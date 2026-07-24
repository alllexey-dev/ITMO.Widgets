package dev.alllexey.itmowidgets.core.friend

import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.result.AppError
import kotlinx.coroutines.flow.Flow

sealed interface FriendListState {
    data object Loading : FriendListState
    data object Disabled : FriendListState
    data class Content(val friends: List<UserSummary>) : FriendListState
    data class Error(val error: AppError) : FriendListState
}

interface FriendRepository {

    fun observeFriendList(): Flow<FriendListState>

    suspend fun refreshFriendList()

    val currentFriends: List<UserSummary>?
}
