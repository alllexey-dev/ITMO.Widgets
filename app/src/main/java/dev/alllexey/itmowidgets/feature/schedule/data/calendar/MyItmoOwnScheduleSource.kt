package dev.alllexey.itmowidgets.feature.schedule.data.calendar

import api.myitmo.MyItmoApi
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.schedule.data.demo.DemoSchedule
import dev.alllexey.itmowidgets.feature.schedule.data.mapper.toModel
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.OwnScheduleSource
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException

/**
 * The personal schedule asked from My ITMO in pieces of at most [CHUNK_DAYS] days, one after another. Like the change
 * check, it neither fills the schedule cache nor uploads lessons to Backend.
 */
class MyItmoOwnScheduleSource @Inject constructor(
    private val api: MyItmoApi,
    private val time: AcademicTimeProvider,
    private val demo: DemoMode
) : OwnScheduleSource {

    override suspend fun read(start: LocalDate, end: LocalDate): List<DaySchedule> = withContext(Dispatchers.IO) {
        if (demo.isActive()) return@withContext DemoSchedule.ownDays(start, end, time.today())
        generateSequence(start) { it.plusDays(CHUNK_DAYS) }
            .takeWhile { !it.isAfter(end) }
            .flatMap { from -> request(from, minOf(end, from.plusDays(CHUNK_DAYS - 1))) }
            .toList()
    }

    private fun request(start: LocalDate, end: LocalDate): List<DaySchedule> {
        val response = api.getPersonalSchedule(start, end).execute()
        if (!response.isSuccessful) throw HttpException(response)
        val days = checkNotNull(response.body()?.data) { "My ITMO answered without a schedule" }
        return days.map { day ->
            DaySchedule(
                dayNumber = day.dayNumber,
                weekNumber = day.weekNumber,
                date = day.date,
                note = day.note,
                lessons = day.lessons.orEmpty().map { it.toModel() }
            )
        }
    }

    private companion object {
        const val CHUNK_DAYS = 31L
    }
}
