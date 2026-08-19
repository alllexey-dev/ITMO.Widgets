package dev.alllexey.itmowidgets.feature.schedule.domain.widget

import dev.alllexey.itmowidgets.core.settings.LessonStyle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleWidgetSnapshotTest {

    @Test
    fun `loading and error snapshots cannot replace refresh failure`() {
        assertFalse(ScheduleWidgetSnapshot.loading().canBeShownWhenRefreshFails())
        assertFalse(ScheduleWidgetSnapshot.error().canBeShownWhenRefreshFails())
    }

    @Test
    fun `lesson snapshot can remain visible after refresh failure`() {
        val snapshot = ScheduleWidgetSnapshot(
            singleLesson = SingleLessonWidgetContent(
                kind = SingleLessonWidgetKind.LESSON,
                lesson = ScheduleWidgetLesson(
                    subject = "Математический анализ",
                    start = "11:40",
                    end = "13:10",
                    typeId = 1,
                    teacher = null,
                    room = null,
                    building = null,
                    state = ScheduleWidgetLessonState.CURRENT
                )
            ),
            lessonList = emptyList(),
            singleLessonStyle = LessonStyle.DOT,
            lessonListStyle = LessonStyle.DOT
        )

        assertTrue(snapshot.canBeShownWhenRefreshFails())
    }

    @Test
    fun `valid empty state can remain visible after refresh failure`() {
        val snapshot = ScheduleWidgetSnapshot(
            singleLesson = SingleLessonWidgetContent(SingleLessonWidgetKind.EMPTY_TODAY),
            lessonList = listOf(
                ScheduleListWidgetItem(ScheduleListWidgetItemKind.EMPTY_TODAY)
            ),
            singleLessonStyle = LessonStyle.LINE,
            lessonListStyle = LessonStyle.LINE
        )

        assertTrue(snapshot.canBeShownWhenRefreshFails())
    }
}
