package dev.alllexey.itmowidgets.core.schedule

import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import kotlin.test.Test
import kotlin.test.assertEquals

class LessonTypeNamesTest {

    @Test
    fun everyMyItmoTypeIdHasItsName() {
        mapOf(
            -1 to CoreStrings.schedule_no_lessons,
            1 to CoreStrings.schedule_lesson_type_lecture,
            2 to CoreStrings.schedule_lesson_type_lab,
            3 to CoreStrings.schedule_lesson_type_practice,
            4 to CoreStrings.schedule_lesson_type_default,
            5 to CoreStrings.schedule_lesson_type_exam,
            6 to CoreStrings.schedule_lesson_type_credit,
            9 to CoreStrings.schedule_lesson_type_default,
            10 to CoreStrings.schedule_lesson_type_consultation,
            11 to CoreStrings.title_sport,
            0 to CoreStrings.schedule_lesson_type_default
        ).forEach { (typeId, name) -> assertEquals(UiText.Res(name), lessonTypeName(typeId), "type $typeId") }
    }
}
