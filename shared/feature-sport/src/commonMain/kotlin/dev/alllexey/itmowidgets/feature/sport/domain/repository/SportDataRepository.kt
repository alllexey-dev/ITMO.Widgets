package dev.alllexey.itmowidgets.feature.sport.domain.repository

import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.feature.sport.domain.model.FriendSportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAttempts
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAutoSignLimits
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueue
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntry
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportScore
import kotlinx.coroutines.flow.Flow

interface SportDataRepository {

    fun observeSportScore(): Flow<AppResult<SportScore>>

    suspend fun refreshSportScore()

    fun observeSportAttempts(): Flow<AppResult<SportAttempts>>

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
