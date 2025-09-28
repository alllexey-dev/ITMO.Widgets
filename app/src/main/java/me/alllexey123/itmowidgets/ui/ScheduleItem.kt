package me.alllexey123.itmowidgets.ui

import api.myitmo.model.Lesson
import java.time.LocalTime

sealed interface ScheduleItem {
    data class LessonItem(val lesson: Lesson) : ScheduleItem

    data class BreakItem(val from: LocalTime, val to: LocalTime) : ScheduleItem
}