package dev.alllexey.itmowidgets.feature.schedule.data.calendar

import dev.alllexey.itmoapi.core.requireResult
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.schedule.data.demo.DemoSchedule
import dev.alllexey.itmowidgets.feature.schedule.data.mapper.toModel
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.OwnScheduleSource
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * The personal schedule asked from My ITMO in pieces of at most [CHUNK_DAYS] days, one after another. Like the change
 * check, it neither fills the schedule cache nor uploads lessons to Backend.
 */
class MyItmoOwnScheduleSource(
    private val myItmo: MyItmoClient,
    private val time: AcademicTimeProvider,
    private val demo: DemoMode,
    private val dispatchers: AppDispatchers
) : OwnScheduleSource {

    override suspend fun read(start: LocalDate, end: LocalDate): List<DaySchedule> {
        if (demo.isActive()) return DemoSchedule.ownDays(start, end, time.today())
        return withContext(dispatchers.io) {
            val days = mutableListOf<DaySchedule>()
            var from = start
            while (from <= end) {
                days += request(from, minOf(end, from.plus(CHUNK_DAYS - 1, DateTimeUnit.DAY)))
                from = from.plus(CHUNK_DAYS, DateTimeUnit.DAY)
            }
            days
        }
    }

    private suspend fun request(start: LocalDate, end: LocalDate): List<DaySchedule> =
        myItmo.schedule.getPersonalSchedule(start, end).requireResult().map { it.toModel() }

    private companion object {
        const val CHUNK_DAYS = 31
    }
}
