package dev.alllexey.itmowidgets.feature.home.domain

import dev.alllexey.itmowidgets.core.bell.BellItem
import dev.alllexey.itmowidgets.core.result.AppResult
import kotlinx.coroutines.flow.Flow

/** Every feature's bell events and which of them the user has seen. */
interface BellRepository {
    /** The events of every source: requests (no moment) first, then newest first. */
    fun observeItems(): Flow<List<BellItem>>

    fun observeSeen(): Flow<Set<String>>

    /** How many events the user has not seen: the badge on the bell. */
    fun observeUnseenCount(): Flow<Int>

    suspend fun markSeen(ids: Set<String>)

    /** Opening the bell reads every source's own unread marks and notifications. */
    suspend fun readAll()

    /** Asks the sources with remote data (friend requests). */
    suspend fun refresh(): AppResult<Unit>

    /** Answers the friend request of [isu]; null when no source showed it. */
    suspend fun answer(isu: Int, accept: Boolean): AppResult<Unit>?
}
