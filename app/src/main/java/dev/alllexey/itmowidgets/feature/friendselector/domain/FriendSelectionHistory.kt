package dev.alllexey.itmowidgets.feature.friendselector.domain

interface FriendSelectionHistory {
    suspend fun getRecentIsu(): List<Int>

    suspend fun record(isu: Int)
}
