package dev.alllexey.itmowidgets.feature.schedule.presentation

import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private val BIG_BREAK_THRESHOLD = 60.minutes

/**
 * The schedule list at the academic instant [now]; [timeZone] places pending sport bookings on the wall clock.
 * Recomputing with a later [now] changes only time states and the today/past flags.
 */
fun buildScheduleListUi(
    days: List<ScheduleDisplayDay>,
    now: LocalDateTime,
    timeZone: TimeZone
): List<ScheduleDayUi> {
    val states = resolveScheduleTimeline(days, now)
    return days.mapIndexed { index, day ->
        val lessons = day.officialDay?.lessons.orEmpty()
        ScheduleDayUi(
            date = day.date,
            title = ScheduleDayTitle(day.date.dayOfWeek, day.date),
            summary = when {
                lessons.isNotEmpty() -> ScheduleDaySummary.Lessons(lessons.size)
                day.pendingSport.isNotEmpty() -> ScheduleDaySummary.AutoSignOnly
                else -> ScheduleDaySummary.NoLessons
            },
            isToday = day.date == now.date,
            isPast = day.date < now.date,
            rows = scheduleRows(day, states[index], timeZone)
        )
    }
}

/**
 * States follow the original day/official-lesson indices, not lesson IDs: an
 * upstream duplicate must not create multiple NEXT markers. Pending bookings
 * are intentionally outside the official timeline's current/next selection.
 * Current intervals are half-open: a lesson is completed exactly at its end.
 */
fun resolveScheduleTimeline(
    days: List<ScheduleDisplayDay>,
    now: LocalDateTime
): List<List<ScheduleLessonState>> {
    var nextDayIndex = -1
    var nextLessonIndex = -1
    var nextStart: LocalDateTime? = null
    days.forEachIndexed { dayIndex, day ->
        day.officialDay?.lessons.orEmpty().forEachIndexed { lessonIndex, lesson ->
            val start = LocalDateTime(day.date, lesson.start)
            val previousNextStart = nextStart
            if (start > now && (previousNextStart == null || start < previousNextStart)) {
                nextDayIndex = dayIndex
                nextLessonIndex = lessonIndex
                nextStart = start
            }
        }
    }

    return days.mapIndexed { dayIndex, day ->
        day.officialDay?.lessons.orEmpty().mapIndexed { lessonIndex, lesson ->
            when {
                now >= LocalDateTime(day.date, lesson.end) -> ScheduleLessonState.COMPLETED
                now >= LocalDateTime(day.date, lesson.start) -> ScheduleLessonState.CURRENT
                dayIndex == nextDayIndex && lessonIndex == nextLessonIndex -> ScheduleLessonState.NEXT
                else -> ScheduleLessonState.UPCOMING
            }
        }
    }
}

/**
 * Official lessons by start (stable for equal starts), a break after a lesson when the gap to the next one is over
 * an hour and no pending row overlaps it, then pending rows merged in by start; only the last row is flagged last.
 */
private fun scheduleRows(
    day: ScheduleDisplayDay,
    states: List<ScheduleLessonState>,
    timeZone: TimeZone
): List<ScheduleRowUi> {
    val rows = mutableListOf<ScheduleRowUi>()
    val sortedLessons = day.officialDay?.lessons.orEmpty().withIndex().sortedBy { it.value.start }
    val pendingRows = day.pendingSport.map { booking -> booking.toRow(timeZone) }

    sortedLessons.forEachIndexed { index, indexedLesson ->
        val lesson = indexedLesson.value
        rows += ScheduleRowUi.LessonRow(
            lesson = lesson,
            state = states[indexedLesson.index],
            isLast = false,
            changed = lesson.pairId in day.changedPairIds
        )
        val nextLesson = sortedLessons.getOrNull(index + 1)?.value ?: return@forEachIndexed
        val breakDuration = (nextLesson.start.toSecondOfDay() - lesson.end.toSecondOfDay()).seconds
        val overlapsPending = pendingRows.any { it.start < nextLesson.start && it.end > lesson.end }
        if (breakDuration > BIG_BREAK_THRESHOLD && !overlapsPending) {
            rows += ScheduleRowUi.BreakRow(lesson.end, nextLesson.start)
        }
    }

    rows += pendingRows
    if (rows.isEmpty()) return listOf(ScheduleRowUi.NoLessons)

    val lastIndex = rows.lastIndex
    return rows.sortedBy { it.start() }.mapIndexed { index, row ->
        when (row) {
            is ScheduleRowUi.LessonRow -> row.copy(isLast = index == lastIndex)
            is ScheduleRowUi.PendingSportRow -> row.copy(isLast = index == lastIndex)
            else -> row
        }
    }
}

private fun PendingSportBooking.toRow(timeZone: TimeZone) = ScheduleRowUi.PendingSportRow(
    booking = this,
    start = start.toLocalDateTime(timeZone).time,
    end = end.toLocalDateTime(timeZone).time,
    status = if (isPrediction) PendingSportStatus.PREDICTION else PendingSportStatus.WAITING,
    isLast = false
)

private fun ScheduleRowUi.start(): LocalTime = when (this) {
    is ScheduleRowUi.LessonRow -> lesson.start
    is ScheduleRowUi.PendingSportRow -> start
    is ScheduleRowUi.BreakRow -> from
    ScheduleRowUi.NoLessons -> LocalTime(0, 0)
}
