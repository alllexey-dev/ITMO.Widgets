package dev.alllexey.itmowidgets.domain.repository

import api.myitmo.model.sport.SportFilters
import api.myitmo.model.sport.TimeSlot
import dev.alllexey.itmowidgets.core.util.DataState
import dev.alllexey.itmowidgets.core.util.MergedDataState
import dev.alllexey.itmowidgets.domain.model.sport.SportLesson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

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

    fun observeSportFilters(): Flow<DataState<SportFilters>>

    suspend fun refreshSportFilters()

    fun observeSportTimeSlots(): MutableSharedFlow<DataState<List<TimeSlot>>>

    suspend fun refreshSportTimeSlots()

}
