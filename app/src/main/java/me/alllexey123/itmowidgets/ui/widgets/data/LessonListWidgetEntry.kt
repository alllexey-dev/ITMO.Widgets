package me.alllexey123.itmowidgets.ui.widgets.data

import me.alllexey123.itmowidgets.R

sealed class LessonListWidgetEntry {
    abstract val layoutId: Int

    object Error : LessonListWidgetEntry() {
        override val layoutId: Int = R.layout.item_lesson_list_error
    }
    object FullDayEmpty : LessonListWidgetEntry() {
        override val layoutId: Int = R.layout.item_lesson_list_empty
    }
    object NoMoreLessons : LessonListWidgetEntry() {
        override val layoutId: Int = R.layout.item_lesson_list_no_more
    }
    object LessonListEnd : LessonListWidgetEntry() {
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