package dev.alllexey.itmowidgets.feature.schedule.ui

import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import java.time.LocalTime

sealed interface ScheduleItem {
    /** [changed]: a schedule change of the last 30 days touches this lesson. */
    data class LessonItem(
        val lesson: Lesson,
        val lessonState: LessonState,
        val isLastLesson: Boolean,
        val changed: Boolean = false
    ) : ScheduleItem

    /** [start] and [end] are the booking's academic wall-clock times. */
    data class PendingSportItem(
        val booking: PendingSportBooking,
        val start: LocalTime,
        val end: LocalTime,
        val isLast: Boolean
    ) : ScheduleItem

    data class BreakItem(val from: LocalTime, val to: LocalTime) : ScheduleItem

    data class NoLessonsItem(val lessonState: LessonState): ScheduleItem

    enum class LessonState {
        UPCOMING,
        NEXT,
        CURRENT,
        COMPLETED
    }
}
