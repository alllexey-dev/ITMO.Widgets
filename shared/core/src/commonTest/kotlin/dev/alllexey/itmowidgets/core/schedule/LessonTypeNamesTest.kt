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
import kotlin.test.Test
import kotlin.test.assertEquals

class LessonTypeNamesTest {

    @Test
    fun everyMyItmoTypeIdHasItsName() {
        mapOf(
            -1 to Res.string.schedule_no_lessons,
            1 to Res.string.schedule_lesson_type_lecture,
            2 to Res.string.schedule_lesson_type_lab,
            3 to Res.string.schedule_lesson_type_practice,
            4 to Res.string.schedule_lesson_type_default,
            5 to Res.string.schedule_lesson_type_exam,
            6 to Res.string.schedule_lesson_type_credit,
            9 to Res.string.schedule_lesson_type_default,
            10 to Res.string.schedule_lesson_type_consultation,
            11 to Res.string.title_sport,
            0 to Res.string.schedule_lesson_type_default
        ).forEach { (typeId, name) -> assertEquals(name, lessonTypeName(typeId), "type $typeId") }
    }
}
