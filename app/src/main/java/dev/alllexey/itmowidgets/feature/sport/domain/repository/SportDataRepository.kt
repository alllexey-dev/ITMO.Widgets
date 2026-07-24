package dev.alllexey.itmowidgets.feature.sport.domain.repository

import dev.alllexey.itmowidgets.core.util.CustomDataState
import dev.alllexey.itmowidgets.core.util.DataState
import dev.alllexey.itmowidgets.feature.sport.domain.model.FriendSportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAttempts
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAutoSignLimits
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueue
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntry
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportScore
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
