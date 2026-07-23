package dev.alllexey.itmowidgets.domain.repository

import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.domain.model.schedule.DaySchedule
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface ScheduleRepository {

    fun observeScheduleForRange(
        userIsu: Int?,
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<DaySchedule>>

    suspend fun refreshSchedule(
        userIsu: Int?,
        startDate: LocalDate,
        endDate: LocalDate
    ): AppResult<Unit>

    fun clearCaches()
}
