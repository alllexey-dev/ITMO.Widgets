package me.alllexey123.itmowidgets.ui.widgets

import me.alllexey123.itmowidgets.R

object LessonRepository {
    @Volatile
    private var lessons: List<SingleLessonData> = emptyList()

    @Volatile
    var rowLayoutId: Int = R.layout.single_lesson_widget_dot

    @Volatile
    var bonusLayoutId: Int = R.layout.item_lesson_list_empty

    fun setLessons(newLessons: List<SingleLessonData>) {
        lessons = newLessons
    }

    fun getLessons(): List<SingleLessonData> = lessons
}