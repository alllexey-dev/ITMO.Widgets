package dev.alllexey.itmowidgets.domain.repository

import dev.alllexey.itmowidgets.core.model.SportAutoSignLimits
import dev.alllexey.itmowidgets.core.model.SportQueue
import dev.alllexey.itmowidgets.core.model.SportQueueEntry
import dev.alllexey.itmowidgets.core.util.CustomDataState
import dev.alllexey.itmowidgets.core.util.DataState
import dev.alllexey.itmowidgets.domain.model.sport.FriendSportBooking
import dev.alllexey.itmowidgets.domain.model.sport.SportAttempts
import dev.alllexey.itmowidgets.domain.model.sport.SportScore
import kotlinx.coroutines.flow.MutableSharedFlow

interface SportDataRepository {

    fun observeSportScore(): MutableSharedFlow<DataState<SportScore>>

    suspend fun refreshSportScore()

    fun observeSportAttempts(): MutableSharedFlow<DataState<SportAttempts>>

    suspend fun refreshSportAttempts()

    fun observeSportAutoSignLimits(): MutableSharedFlow<CustomDataState<SportAutoSignLimits>>

    suspend fun refreshSportAutoSignLimits()

    fun observeSportQueueEntries(): MutableSharedFlow<CustomDataState<List<SportQueueEntry>>>

    suspend fun refreshSportQueueEntries()

    fun observeSportQueues(): MutableSharedFlow<CustomDataState<List<SportQueue>>>

    suspend fun refreshSportQueues()

    fun observeFriendsBookings(): MutableSharedFlow<CustomDataState<List<FriendSportBooking>>>

    suspend fun refreshFriendsBookings()
}
