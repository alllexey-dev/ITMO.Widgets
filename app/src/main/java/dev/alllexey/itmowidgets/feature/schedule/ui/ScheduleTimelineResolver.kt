package dev.alllexey.itmowidgets.feature.schedule.ui

import dev.alllexey.itmowidgets.feature.schedule.presentation.ScheduleDisplayDay
import java.time.LocalDateTime

/**
 * States follow the original day/official-lesson indices, not lesson IDs: an
 * upstream duplicate must not create multiple NEXT markers. Pending bookings
 * are intentionally outside the official timeline's current/next selection.
 * Current intervals are half-open: a lesson is completed exactly at its end.
 */
fun resolveScheduleTimeline(
    days: List<ScheduleDisplayDay>,
    now: LocalDateTime
): List<List<ScheduleItem.LessonState>> {
    var nextDayIndex = -1
    var nextLessonIndex = -1
    var nextStart: LocalDateTime? = null
    days.forEachIndexed { dayIndex, day ->
        day.officialDay?.lessons.orEmpty().forEachIndexed { lessonIndex, lesson ->
            val start = lesson.start.atDate(day.date)
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
                now >= lesson.end.atDate(day.date) -> ScheduleItem.LessonState.COMPLETED
                now >= lesson.start.atDate(day.date) -> ScheduleItem.LessonState.CURRENT
                dayIndex == nextDayIndex && lessonIndex == nextLessonIndex -> ScheduleItem.LessonState.NEXT
                else -> ScheduleItem.LessonState.UPCOMING
            }
        }
    }
}
