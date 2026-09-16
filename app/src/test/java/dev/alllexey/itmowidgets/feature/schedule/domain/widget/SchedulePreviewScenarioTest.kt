package dev.alllexey.itmowidgets.feature.schedule.domain.widget

import dev.alllexey.itmowidgets.core.settings.CompactScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.FullScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulePreviewScenarioTest {
    private val scenario = SchedulePreviewScenario(ScheduleWidgetSelector())
    private val labels = SchedulePreviewLabels("History", "Math", "Programming", "Physics", "Teacher")

    @Test
    fun `early selection uses the real fifteen minute boundary`() {
        assertEquals("Programming", snapshot().singleLesson.lesson?.subject)
        assertEquals("Math", snapshot(ScheduleWidgetSettings(compact = CompactScheduleWidgetSettings(showNextLessonEarly = false))).singleLesson.lesson?.subject)
    }

    @Test
    fun `hiding teacher affects both widget types`() {
        val shown = snapshot()
        val hidden = snapshot(ScheduleWidgetSettings(compact = CompactScheduleWidgetSettings(hideTeacher = true), full = FullScheduleWidgetSettings(hideTeacher = true)))
        assertEquals("Teacher", shown.singleLesson.lesson?.teacher)
        assertEquals(null, hidden.singleLesson.lesson?.teacher)
        assertTrue(hidden.lessonList.mapNotNull { it.lesson }.all { it.teacher == null })
    }

    @Test
    fun `past lessons disappear using the real selector`() {
        val shown = snapshot().lessonList.mapNotNull { it.lesson?.subject }
        val hidden = snapshot(ScheduleWidgetSettings(full = FullScheduleWidgetSettings(hidePastLessons = true))).lessonList.mapNotNull { it.lesson?.subject }
        assertEquals(listOf("History", "Math", "Programming"), shown)
        assertEquals(listOf("Math", "Programming"), hidden)
    }

    @Test
    fun `evening example exposes tomorrow only in day widget when enabled`() {
        val today = snapshot(evening = true)
        val tomorrow = snapshot(ScheduleWidgetSettings(full = FullScheduleWidgetSettings(showTomorrowWhenTodayIsOver = true)), evening = true)
        assertEquals(SingleLessonWidgetKind.NO_MORE_TODAY, today.singleLesson.kind)
        assertEquals(today.singleLesson, tomorrow.singleLesson)
        assertFalse(today.lessonList.any { it.tomorrow })
        assertEquals(listOf("Physics", "Programming"), tomorrow.lessonList.mapNotNull { it.lesson?.subject })
        val header = tomorrow.lessonList.first { it.kind == ScheduleListWidgetItemKind.HEADER }
        assertTrue(header.tomorrow)
        assertEquals("2026-09-08", header.dateIso)
    }

    @Test
    fun `same sample inputs produce identical output`() {
        assertEquals(snapshot(), snapshot())
    }

    @Test
    fun `compact early selection never hides the ongoing lesson in the full widget`() {
        val full = FullScheduleWidgetSettings(hidePastLessons = true)
        val early = snapshot(ScheduleWidgetSettings(full = full))
        val regular = snapshot(ScheduleWidgetSettings(CompactScheduleWidgetSettings(showNextLessonEarly = false), full))
        assertEquals("Programming", early.singleLesson.lesson?.subject)
        assertEquals("Math", regular.singleLesson.lesson?.subject)
        assertEquals(regular.lessonList, early.lessonList)
        assertEquals(listOf("Math", "Programming"), early.lessonList.mapNotNull { it.lesson?.subject })
    }

    @Test
    fun `each format hides its own teachers without altering the other content`() {
        val original = snapshot()
        val compact = snapshot(ScheduleWidgetSettings(compact = CompactScheduleWidgetSettings(hideTeacher = true)))
        assertEquals(null, compact.singleLesson.lesson?.teacher)
        assertEquals(original.lessonList, compact.lessonList)
        val full = snapshot(ScheduleWidgetSettings(full = FullScheduleWidgetSettings(hideTeacher = true)))
        assertEquals(original.singleLesson, full.singleLesson)
        assertTrue(full.lessonList.mapNotNull { it.lesson }.all { it.teacher == null })
    }

    private fun snapshot(settings: ScheduleWidgetSettings = ScheduleWidgetSettings(), evening: Boolean = false) =
        scenario.snapshot(settings, evening, labels)
}
