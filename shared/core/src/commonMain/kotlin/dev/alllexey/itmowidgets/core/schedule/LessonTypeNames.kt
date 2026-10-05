package dev.alllexey.itmowidgets.core.schedule

import dev.alllexey.itmowidgets.shared.core.Res
import dev.alllexey.itmowidgets.shared.core.schedule_lesson_type_consultation
import dev.alllexey.itmowidgets.shared.core.schedule_lesson_type_credit
import dev.alllexey.itmowidgets.shared.core.schedule_lesson_type_default
import dev.alllexey.itmowidgets.shared.core.schedule_lesson_type_exam
import dev.alllexey.itmowidgets.shared.core.schedule_lesson_type_lab
import dev.alllexey.itmowidgets.shared.core.schedule_lesson_type_lecture
import dev.alllexey.itmowidgets.shared.core.schedule_lesson_type_practice
import dev.alllexey.itmowidgets.shared.core.schedule_no_lessons
import dev.alllexey.itmowidgets.shared.core.title_sport
import org.jetbrains.compose.resources.StringResource

/**
 * The name of a MyITMO lesson type id: 1 лекция, 2 лаб., 3 практика, 5 экзамен, 6 зачёт, 10 консультация, 11 спорт,
 * -1 a free day; any other id is a plain "Пара". The one common owner; `core/ui/LessonTypes.kt` keeps the same
 * mapping as Android ids for Views.
 */
fun lessonTypeName(typeId: Int): StringResource = when (typeId) {
    -1 -> Res.string.schedule_no_lessons
    1 -> Res.string.schedule_lesson_type_lecture
    2 -> Res.string.schedule_lesson_type_lab
    3 -> Res.string.schedule_lesson_type_practice
    5 -> Res.string.schedule_lesson_type_exam
    6 -> Res.string.schedule_lesson_type_credit
    10 -> Res.string.schedule_lesson_type_consultation
    11 -> Res.string.title_sport
    else -> Res.string.schedule_lesson_type_default
}
