package dev.alllexey.itmowidgets.domain.repository

import dev.alllexey.itmowidgets.core.util.CustomDataState
import dev.alllexey.itmowidgets.domain.model.user.UserSummary
import kotlinx.coroutines.flow.Flow

interface FriendRepository {

    fun observeFriendList(): Flow<CustomDataState<List<UserSummary>>>

    suspend fun refreshFriendList()
    val currentFriends: CustomDataState<List<UserSummary>>?
}
