package dev.alllexey.itmowidgets.feature.schedule

import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.feature.schedule.domain.ScheduleRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

internal data class ScheduleRequest(val userIsu: Int?, val startDate: LocalDate, val endDate: LocalDate)

/**
 * Schedules per user, the own one in [days]; observers get the asked range of [schedulesFor] and are recorded in
 * [observed]. Refreshes are recorded in [refreshed] and answer [refreshHandler], [refreshResult] by default.
 * [peekScheduleForRange] answers only while [memorySnapshot]; [clearCaches] empties every schedule.
 */
internal class FakeScheduleRepository(days: List<DaySchedule> = emptyList()) : ScheduleRepository {
    private val schedulesByUser = mutableMapOf<Int?, MutableStateFlow<List<DaySchedule>>>()
    val days: MutableStateFlow<List<DaySchedule>> get() = schedulesFor(null)
    val observed = mutableListOf<ScheduleRequest>()
    val refreshed = mutableListOf<ScheduleRequest>()
    var refreshResult: AppResult<Unit> = AppResult.Success(Unit)
    var refreshHandler: suspend (ScheduleRequest) -> AppResult<Unit> = { refreshResult }
    var memorySnapshot = false
    var clears = 0
        private set

    init {
        this.days.value = days
    }

    fun schedulesFor(userIsu: Int?): MutableStateFlow<List<DaySchedule>> =
        schedulesByUser.getOrPut(userIsu) { MutableStateFlow(emptyList()) }

    override fun observeScheduleForRange(userIsu: Int?, startDate: LocalDate, endDate: LocalDate): Flow<List<DaySchedule>> {
        observed += ScheduleRequest(userIsu, startDate, endDate)
        return schedulesFor(userIsu).map { it.inRange(startDate, endDate) }
    }

    override fun peekScheduleForRange(userIsu: Int?, startDate: LocalDate, endDate: LocalDate): List<DaySchedule>? =
        if (memorySnapshot) schedulesFor(userIsu).value.inRange(startDate, endDate) else null

    override suspend fun refreshSchedule(userIsu: Int?, startDate: LocalDate, endDate: LocalDate): AppResult<Unit> {
        val request = ScheduleRequest(userIsu, startDate, endDate)
        refreshed += request
        return refreshHandler(request)
    }

    override suspend fun clearCaches() {
        clears += 1
        schedulesByUser.values.forEach { it.value = emptyList() }
    }

    private fun List<DaySchedule>.inRange(startDate: LocalDate, endDate: LocalDate) =
        filter { !it.date.isBefore(startDate) && !it.date.isAfter(endDate) }
}
