package dev.alllexey.itmowidgets.feature.schedule.ui

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson

@ColorRes
fun Lesson.TypeId.colorRes(): Int {
    return when (raw) {
        -1 -> R.color.free_color
        1 -> R.color.lecture_color
        2 -> R.color.lab_color
        3 -> R.color.practice_color
        4, 5, 6, 7, 8, 9 -> R.color.red_lesson_color
        10 -> R.color.consultation_color
        11 -> R.color.free_sport_color
        else -> R.color.subtext_color
    }
}

@StringRes
fun Lesson.TypeId.nameRes(): Int {
    return when (raw) {
        -1 -> R.string.schedule_no_lessons
        1 -> R.string.schedule_lesson_type_lecture
        2 -> R.string.schedule_lesson_type_lab
        3 -> R.string.schedule_lesson_type_practice
        5 -> R.string.schedule_lesson_type_exam
        6 -> R.string.schedule_lesson_type_credit
        10 -> R.string.schedule_lesson_type_consultation
        11 -> R.string.title_sport
        else -> R.string.schedule_lesson_type_default
    }
}
