package dev.alllexey.itmowidgets.core.schedule

import dev.alllexey.itmowidgets.core.result.AppResult
import kotlinx.datetime.LocalDate

interface ScheduleRefreshGateway {
    suspend fun refreshOwnSchedule(
        startDate: LocalDate,
        endDate: LocalDate
    ): AppResult<Unit>
}
