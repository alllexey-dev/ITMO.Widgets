package dev.alllexey.itmowidgets.feature.schedule.ui.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleWidgetTextTest {

    @Test
    fun `shortens teacher name to fit compact widget row`() {
        assertEquals(
            "Попов А. И.",
            compactTeacherName("  Попов   Антон Игоревич  ")
        )
    }

    @Test
    fun `puts actionable location before teacher`() {
        assertEquals(
            "1506 Кронва · Попов А. И.",
            compactLessonDetails("1506 Кронва", "Попов Антон Игоревич")
        )
    }

    @Test
    fun `does not add separators for missing details`() {
        assertEquals("Кронва", compactLessonDetails("Кронва", null))
        assertEquals("", compactLessonDetails("", ""))
    }
}
