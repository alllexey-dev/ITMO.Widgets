package me.alllexey123.itmowidgets.ui.schedule

import api.myitmo.model.Lesson
import java.time.LocalTime

sealed interface ScheduleItem {
    data class LessonItem(val lesson: Lesson, val lessonState: LessonState) : ScheduleItem

    data class BreakItem(val from: LocalTime, val to: LocalTime) : ScheduleItem

    data class NoLessonsItem(val lessonState: LessonState): ScheduleItem

    enum class LessonState {
        UPCOMING,
        CURRENT,
        COMPLETED
    }
}