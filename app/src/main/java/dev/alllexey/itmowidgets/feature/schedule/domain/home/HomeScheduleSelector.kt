package dev.alllexey.itmowidgets.feature.schedule.domain.home

import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.home.HomeLessonState
import dev.alllexey.itmowidgets.core.home.HomeScheduleRow
import dev.alllexey.itmowidgets.core.navigation.toDetailsArgs
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.domain.model.toDetailsArgs
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import javax.inject.Inject

/**
 * Picks what the home schedule card shows: the rest of today, or tomorrow once
 * today is over, the same way the single-lesson widget does without its
 * look-ahead. Pure: the caller supplies the cached days and the clock.
 */
class HomeScheduleSelector @Inject constructor() {

    fun select(
        days: List<DaySchedule>,
        pending: List<PendingSportBooking>,
        now: OffsetDateTime
    ): HomeCard.Schedule {
        val today = now.toLocalDate()
        val time = now.toLocalTime()
        val todayLessons = days.firstOrNull { it.date == today }?.lessons.orEmpty()
        val completed = todayLessons.count { it.end <= time }
        val bookings = pending
            .distinctBy { it.queueKind to it.queueId }
            .filter { it.start.isAfter(now) }
            .map { it to it.start.withOffsetSameInstant(now.offset) }

        val todayRows = rows(
            date = today,
            lessons = todayLessons.filter { it.end > time },
            pending = bookings.filter { (_, local) -> local.toLocalDate() == today },
            time = time
        )
        if (todayRows.isNotEmpty()) return HomeCard.Schedule(today, tomorrow = false, todayRows, completed)

        val tomorrow = today.plusDays(1)
        val tomorrowRows = rows(
            date = tomorrow,
            lessons = days.firstOrNull { it.date == tomorrow }?.lessons.orEmpty(),
            pending = bookings.filter { (_, local) -> local.toLocalDate() == tomorrow },
            time = LocalTime.MIN
        )
        return if (tomorrowRows.isNotEmpty()) HomeCard.Schedule(tomorrow, tomorrow = true, tomorrowRows, completed)
        else HomeCard.Schedule(today, tomorrow = false, emptyList(), completed)
    }

    /** Lessons and bookings on one timeline; the first lesson is the one in focus. */
    private fun rows(
        date: LocalDate,
        lessons: List<Lesson>,
        pending: List<Pair<PendingSportBooking, OffsetDateTime>>,
        time: LocalTime
    ): List<HomeScheduleRow> {
        val timeline = buildList {
            lessons.forEach { lesson ->
                add(lesson.start to HomeScheduleRow.Lesson(lesson.toDetailsArgs(date), HomeLessonState.UPCOMING))
            }
            pending.forEach { (booking, local) ->
                add(local.toLocalTime() to HomeScheduleRow.PendingSport(booking.toDetailsArgs(), booking.isPrediction))
            }
        }.sortedBy { it.first }.map { it.second }
        val focus = timeline.indexOfFirst { it is HomeScheduleRow.Lesson }
        if (focus < 0) return timeline
        val lesson = timeline[focus] as HomeScheduleRow.Lesson
        val source = lessons.first { it.pairId == lesson.args.pairId }
        val started = source.start <= time
        val focused = if (started) {
            val total = Duration.between(source.start, source.end).toMinutes().coerceAtLeast(1)
            val elapsed = Duration.between(source.start, time).toMinutes().coerceIn(0, total)
            lesson.copy(state = HomeLessonState.CURRENT, progress = elapsed.toFloat() / total)
        } else {
            lesson.copy(state = HomeLessonState.NEXT)
        }
        return timeline.toMutableList().apply { set(focus, focused) }
    }
}
