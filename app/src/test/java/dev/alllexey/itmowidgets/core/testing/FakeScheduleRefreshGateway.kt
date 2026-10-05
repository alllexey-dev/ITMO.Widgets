package dev.alllexey.itmowidgets.core.testing

import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.ScheduleRefreshGateway
import kotlinx.datetime.LocalDate

/** Records every asked range in [requests] and answers [result]. */
class FakeScheduleRefreshGateway : ScheduleRefreshGateway {
    var result: AppResult<Unit> = AppResult.Success(Unit)
    val requests = mutableListOf<Pair<LocalDate, LocalDate>>()
    override suspend fun refreshOwnSchedule(startDate: LocalDate, endDate: LocalDate): AppResult<Unit> {
        requests += startDate to endDate
        return result
    }
}
