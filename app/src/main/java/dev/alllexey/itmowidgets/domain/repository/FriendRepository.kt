package dev.alllexey.itmowidgets.domain.repository

import dev.alllexey.itmowidgets.core.model.UserData
import dev.alllexey.itmowidgets.core.util.CustomDataState
import kotlinx.coroutines.flow.Flow

interface FriendRepository {

    fun observeFriendList(): Flow<CustomDataState<List<UserData>>>

    suspend fun refreshFriendList()
    val currentFriends: CustomDataState<List<UserData>>?
}
