package dev.alllexey.itmowidgets.domain.repository

import dev.alllexey.itmowidgets.core.util.CustomDataState
import dev.alllexey.itmowidgets.core.util.DataState
import dev.alllexey.itmowidgets.domain.model.sport.FriendSportBooking
import dev.alllexey.itmowidgets.domain.model.sport.SportAutoSignLimits
import dev.alllexey.itmowidgets.domain.model.sport.SportAttempts
import dev.alllexey.itmowidgets.domain.model.sport.SportQueue
import dev.alllexey.itmowidgets.domain.model.sport.SportQueueEntry
import dev.alllexey.itmowidgets.domain.model.sport.SportScore
import kotlinx.coroutines.flow.Flow

interface SportDataRepository {

    fun observeSportScore(): Flow<DataState<SportScore>>

    suspend fun refreshSportScore()

    fun observeSportAttempts(): Flow<DataState<SportAttempts>>

    suspend fun refreshSportAttempts()

    fun observeSportAutoSignLimits(): Flow<CustomDataState<SportAutoSignLimits>>

    suspend fun refreshSportAutoSignLimits()

    fun observeSportQueueEntries(): Flow<CustomDataState<List<SportQueueEntry>>>

    suspend fun refreshSportQueueEntries()

    fun observeSportQueues(): Flow<CustomDataState<List<SportQueue>>>

    suspend fun refreshSportQueues()

    fun observeFriendsBookings(): Flow<CustomDataState<List<FriendSportBooking>>>

    suspend fun refreshFriendsBookings()
}
