package dev.alllexey.itmowidgets.feature.schedule.domain.widget

import dev.alllexey.itmowidgets.core.settings.CompactScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.FullScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.WidgetTextSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SchedulePreviewScenarioTest {
    private val scenario = SchedulePreviewScenario(ScheduleWidgetSelector())
    private val labels = SchedulePreviewLabels("History", "Math", "Programming", "Physics", "Teacher")

    @Test
    fun earlySelectionUsesTheRealFifteenMinuteBoundary() {
        assertEquals("Programming", snapshot().singleLesson.lesson?.subject)
        assertEquals("Math", snapshot(ScheduleWidgetSettings(compact = CompactScheduleWidgetSettings(showNextLessonEarly = false))).singleLesson.lesson?.subject)
    }

    @Test
    fun textSizesReachTheSnapshotPerFormatSoThePreviewsRenderThem() {
        val snapshot = snapshot(
            ScheduleWidgetSettings(
                compact = CompactScheduleWidgetSettings(textSize = WidgetTextSize.EXTRA_LARGE),
                full = FullScheduleWidgetSettings(textSize = WidgetTextSize.LARGE)
            )
        )
        assertEquals(WidgetTextSize.EXTRA_LARGE, snapshot.resolvedCompactTextSize)
        assertEquals(WidgetTextSize.LARGE, snapshot.resolvedFullTextSize)
        assertEquals(WidgetTextSize.NORMAL, snapshot().resolvedCompactTextSize)
    }

    @Test
    fun hidingTeacherAffectsBothWidgetTypes() {
        val shown = snapshot()
        val hidden = snapshot(ScheduleWidgetSettings(compact = CompactScheduleWidgetSettings(hideTeacher = true), full = FullScheduleWidgetSettings(hideTeacher = true)))
        assertEquals("Teacher", shown.singleLesson.lesson?.teacher)
        assertEquals(null, hidden.singleLesson.lesson?.teacher)
        assertTrue(hidden.lessonList.mapNotNull { it.lesson }.all { it.teacher == null })
    }

    @Test
    fun pastLessonsDisappearUsingTheRealSelector() {
        val shown = snapshot().lessonList.mapNotNull { it.lesson?.subject }
        val hidden = snapshot(ScheduleWidgetSettings(full = FullScheduleWidgetSettings(hidePastLessons = true))).lessonList.mapNotNull { it.lesson?.subject }
        assertEquals(listOf("History", "Math", "Programming"), shown)
        assertEquals(listOf("Math", "Programming"), hidden)
    }

    @Test
    fun eveningExampleExposesTomorrowOnlyInDayWidgetWhenEnabled() {
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
    fun sameSampleInputsProduceIdenticalOutput() {
        assertEquals(snapshot(), snapshot())
    }

    @Test
    fun compactEarlySelectionNeverHidesTheOngoingLessonInTheFullWidget() {
        val full = FullScheduleWidgetSettings(hidePastLessons = true)
        val early = snapshot(ScheduleWidgetSettings(full = full))
        val regular = snapshot(ScheduleWidgetSettings(CompactScheduleWidgetSettings(showNextLessonEarly = false), full))
        assertEquals("Programming", early.singleLesson.lesson?.subject)
        assertEquals("Math", regular.singleLesson.lesson?.subject)
        assertEquals(regular.lessonList, early.lessonList)
        assertEquals(listOf("Math", "Programming"), early.lessonList.mapNotNull { it.lesson?.subject })
    }

    @Test
    fun eachFormatHidesItsOwnTeachersWithoutAlteringTheOtherContent() {
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
