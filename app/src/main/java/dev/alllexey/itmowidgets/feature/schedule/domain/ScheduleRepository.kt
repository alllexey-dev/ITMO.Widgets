package dev.alllexey.itmowidgets.feature.schedule.domain

import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.ScheduleRefreshGateway
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface ScheduleRepository : ScheduleRefreshGateway {

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

    override suspend fun refreshOwnSchedule(
        startDate: LocalDate,
        endDate: LocalDate
    ): AppResult<Unit> = refreshSchedule(
        userIsu = null,
        startDate = startDate,
        endDate = endDate
    )

    suspend fun clearCaches()
}
