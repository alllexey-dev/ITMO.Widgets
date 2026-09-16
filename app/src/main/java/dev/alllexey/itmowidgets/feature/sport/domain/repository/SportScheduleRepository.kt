package dev.alllexey.itmowidgets.feature.sport.domain.repository

import dev.alllexey.itmowidgets.core.util.DataState
import dev.alllexey.itmowidgets.core.util.MergedDataState
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportFilterCatalog
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportTimeSlot
import kotlinx.coroutines.flow.Flow

interface SportScheduleRepository {

    /**
     * Depends on:
     * - Sport schedule (ITMO)
     * - User (Auto/Free)Sign entries
     * - Friends sport bookings
     * - (Auto/Free)Sign queues
     */
    fun observeSportSchedule(): Flow<MergedDataState<List<SportLesson>>>

    suspend fun refreshSportSchedule()

    /**
     * Free-attendance lessons from ITMO only, without the viewer's queues and friends.
     * Emits as soon as [refreshSportSchedule] completes; screens about another user
     * must not wait for streams that only the viewer's own sport tab refreshes.
     */
    fun observeSportCatalog(): Flow<DataState<List<SportLesson>>>

    fun observeSportFilters(): Flow<DataState<SportFilterCatalog>>

    suspend fun refreshSportFilters()

    fun observeSportTimeSlots(): Flow<DataState<List<SportTimeSlot>>>

    suspend fun refreshSportTimeSlots()

}
