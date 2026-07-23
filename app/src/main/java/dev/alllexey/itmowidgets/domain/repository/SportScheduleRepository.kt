package dev.alllexey.itmowidgets.domain.repository

import dev.alllexey.itmowidgets.core.util.DataState
import dev.alllexey.itmowidgets.core.util.MergedDataState
import dev.alllexey.itmowidgets.domain.model.sport.SportFilterCatalog
import dev.alllexey.itmowidgets.domain.model.sport.SportLesson
import dev.alllexey.itmowidgets.domain.model.sport.SportTimeSlot
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

    fun observeSportFilters(): Flow<DataState<SportFilterCatalog>>

    suspend fun refreshSportFilters()

    fun observeSportTimeSlots(): Flow<DataState<List<SportTimeSlot>>>

    suspend fun refreshSportTimeSlots()

}
