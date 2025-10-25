package dev.alllexey.itmowidgets.ui.widgets.data

import dev.alllexey.itmowidgets.R

sealed class LessonListWidgetEntry {
    abstract val layoutId: Int

    object Error : LessonListWidgetEntry() {
        override val layoutId: Int = R.layout.item_lesson_list_error
    }
    data class DayTitle(val title: String) : LessonListWidgetEntry() {
        override val layoutId: Int = R.layout.item_lesson_list_day_title
    }
    data class FullDayEmpty(val isTomorrow: Boolean) : LessonListWidgetEntry() {
        override val layoutId: Int = R.layout.item_lesson_list_empty
    }
    object NoMoreLessons : LessonListWidgetEntry() {
        override val layoutId: Int = R.layout.item_lesson_list_no_more
    }
    data class LessonListEnd(val isTomorrow: Boolean) : LessonListWidgetEntry() {
        override val layoutId: Int = R.layout.item_lesson_list_end
    }
    object Updating : LessonListWidgetEntry() {
        override val layoutId: Int = R.layout.item_lesson_list_updating
    }

    // null to hide
    data class LessonData(
        val subject: String = "",
        val times: String? = null,
        val teacher: String? = null,
        val workTypeId: Int = 0,
        val room: String? = null,
        val building: String? = null,
        override val layoutId: Int,
    ) : LessonListWidgetEntry()
}