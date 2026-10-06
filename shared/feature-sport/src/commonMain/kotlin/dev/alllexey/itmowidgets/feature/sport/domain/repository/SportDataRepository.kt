package dev.alllexey.itmowidgets.feature.sport.domain.repository

import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.feature.sport.domain.model.FriendSportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAttempts
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAutoSignLimits
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueue
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntry
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportScore
import kotlinx.coroutines.flow.Flow

interface SportDataRepository {

    /** [LoadState.Loading] until the first answer and again after the session is cleared. */
    fun observeSportScore(): Flow<LoadState<SportScore>>

    suspend fun refreshSportScore()

    /** [LoadState.Loading] until the first answer and again after the session is cleared. */
    fun observeSportAttempts(): Flow<LoadState<SportAttempts>>

    suspend fun refreshSportAttempts()

    fun observeSportAutoSignLimits(): Flow<LoadState<SportAutoSignLimits>>

    suspend fun refreshSportAutoSignLimits()

    fun observeSportQueueEntries(): Flow<LoadState<List<SportQueueEntry>>>

    suspend fun refreshSportQueueEntries()

    fun observeSportQueues(): Flow<LoadState<List<SportQueue>>>

    suspend fun refreshSportQueues()

    fun observeFriendsBookings(): Flow<LoadState<List<FriendSportBooking>>>

    suspend fun refreshFriendsBookings()
}
