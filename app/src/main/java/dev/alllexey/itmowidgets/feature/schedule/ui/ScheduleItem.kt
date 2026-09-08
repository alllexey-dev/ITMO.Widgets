package dev.alllexey.itmowidgets.feature.schedule.ui

import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import java.time.LocalTime

sealed interface ScheduleItem {
    data class LessonItem(val lesson: Lesson, val lessonState: LessonState, val isLastLesson: Boolean) : ScheduleItem

    data class PendingSportItem(val booking: PendingSportBooking, val isLast: Boolean) : ScheduleItem

    data class BreakItem(val from: LocalTime, val to: LocalTime) : ScheduleItem

    data class NoLessonsItem(val lessonState: LessonState): ScheduleItem

    enum class LessonState {
        UPCOMING,
        CURRENT,
        COMPLETED
    }
}
