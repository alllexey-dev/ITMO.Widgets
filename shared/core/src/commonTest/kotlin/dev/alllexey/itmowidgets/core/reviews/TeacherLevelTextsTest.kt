package dev.alllexey.itmowidgets.core.reviews

import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import kotlin.test.Test
import kotlin.test.assertEquals

class TeacherLevelTextsTest {

    @Test
    fun everyLevelHasItsToneAndLabel() {
        val labels = mapOf(
            TeacherLevel.VERY_NEGATIVE to CoreStrings.teacher_level_very_negative,
            TeacherLevel.NEGATIVE to CoreStrings.teacher_level_negative,
            TeacherLevel.MIXED to CoreStrings.teacher_level_mixed,
            TeacherLevel.POSITIVE to CoreStrings.teacher_level_positive,
            TeacherLevel.VERY_POSITIVE to CoreStrings.teacher_level_very_positive,
        )

        assertEquals(TeacherLevel.entries.toSet(), labels.keys)
        labels.forEach { (level, resource) ->
            assertEquals(level.name, level.tone().name)
            assertEquals(UiText.Res(resource), level.tone().label, level.name)
        }
    }

    @Test
    fun theDescriptionTakesTheLabelLowerCased() {
        val description = TeacherLevel.POSITIVE.tone().description("Скорее положительные")

        assertEquals(UiText.Res(CoreStrings.teacher_level_description, listOf("скорее положительные")), description)
    }
}
