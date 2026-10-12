package dev.alllexey.itmowidgets.core.schedule

import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.core.CoreStrings

/**
 * The name of a MyITMO lesson type id: 1 лекция, 2 лаб., 3 практика, 5 экзамен, 6 зачёт, 10 консультация, 11 спорт,
 * -1 a free day; any other id is a plain "Пара". The one common owner; `core/ui/LessonTypes.kt` keeps the same
 * mapping as Android ids for Views.
 */
fun lessonTypeName(typeId: Int): UiText = UiText.Res(
    when (typeId) {
        -1 -> CoreStrings.schedule_no_lessons
        1 -> CoreStrings.schedule_lesson_type_lecture
        2 -> CoreStrings.schedule_lesson_type_lab
        3 -> CoreStrings.schedule_lesson_type_practice
        5 -> CoreStrings.schedule_lesson_type_exam
        6 -> CoreStrings.schedule_lesson_type_credit
        10 -> CoreStrings.schedule_lesson_type_consultation
        11 -> CoreStrings.title_sport
        else -> CoreStrings.schedule_lesson_type_default
    }
)
