package dev.alllexey.itmowidgets.feature.schedule.domain.widget

import com.google.gson.Gson
import dev.alllexey.itmowidgets.core.settings.CompactScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.FullScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.LessonStyle
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.WidgetTextSize
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleWidgetSnapshotTest {

    @Test
    fun `loading and error snapshots cannot replace refresh failure`() {
        assertFalse(ScheduleWidgetSnapshot.loading().canBeShownWhenRefreshFails())
        assertFalse(ScheduleWidgetSnapshot.error().canBeShownWhenRefreshFails())
    }

    @Test
    fun `snapshots written before the text size choice read as normal and the choice reaches the fallback`() {
        val legacyJson = Gson().toJson(ScheduleWidgetSnapshot.loading())
            .replace(",\"compactTextSize\":\"NORMAL\"", "")
        assertFalse(legacyJson.contains("TextSize"))
        val legacy = Gson().fromJson(legacyJson, ScheduleWidgetSnapshot::class.java)
        assertNull(legacy.compactTextSize)
        assertEquals(WidgetTextSize.NORMAL, legacy.resolvedCompactTextSize)
        assertEquals(WidgetTextSize.NORMAL, legacy.resolvedFullTextSize)

        val sized = ScheduleWidgetSnapshot.error().copy(officialFallback = ScheduleWidgetSnapshot.error()).withTextSizes(
            ScheduleWidgetSettings(
                compact = CompactScheduleWidgetSettings(textSize = WidgetTextSize.LARGE),
                full = FullScheduleWidgetSettings(textSize = WidgetTextSize.EXTRA_LARGE)
            )
        )
        assertEquals(WidgetTextSize.LARGE, sized.resolvedCompactTextSize)
        assertEquals(WidgetTextSize.EXTRA_LARGE, sized.withoutPendingSport().resolvedFullTextSize)
        val roundTrip = Gson().fromJson(Gson().toJson(sized), ScheduleWidgetSnapshot::class.java)
        assertEquals(sized, roundTrip)
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

    @Test
    fun `pending snapshot stays visible only while enabled and strictly before validity deadline`() {
        val snapshot = pendingSnapshot()

        assertSame(snapshot, snapshot.forPendingAvailability(enabled = true, now = NOW))
        assertSame(snapshot.officialFallback, snapshot.forPendingAvailability(enabled = false, now = NOW))
        assertSame(
            snapshot.officialFallback,
            snapshot.forPendingAvailability(enabled = true, now = NOW.plusSeconds(300))
        )
        assertSame(
            snapshot.officialFallback,
            snapshot.forPendingAvailability(enabled = true, now = NOW.plusSeconds(301))
        )
    }

    @Test
    fun `missing or malformed pending deadline fails closed to official fallback`() {
        val snapshot = pendingSnapshot()

        assertSame(
            snapshot.officialFallback,
            snapshot.copy(pendingValidUntil = null).forPendingAvailability(enabled = true, now = NOW)
        )
        assertSame(
            snapshot.officialFallback,
            snapshot.copy(pendingValidUntil = "invalid").forPendingAvailability(enabled = true, now = NOW)
        )
    }

    @Test
    fun `refresh failure fallback restores official next and count without pending markers`() {
        val snapshot = pendingSnapshot()
        val fallback = snapshot.withoutPendingSport()

        assertSame(snapshot.officialFallback, fallback)
        assertEquals("Математика", fallback.singleLesson.lesson?.subject)
        assertEquals(1, fallback.singleLesson.remainingLessons)
        assertEquals(listOf("Математика", "Физика"), fallback.lessonList.mapNotNull { it.lesson?.subject })
        assertNull(fallback.singleLesson.lesson?.pendingStatus)
        assertTrue(fallback.lessonList.mapNotNull { it.lesson }.all { it.pendingStatus == null })
        assertNull(fallback.officialFallback)
        assertNull(fallback.pendingValidUntil)
        assertTrue(fallback.canBeShownWhenRefreshFails())
        assertSame(fallback, fallback.withoutPendingSport())
        assertSame(fallback, fallback.forPendingAvailability(enabled = false, now = NOW))
    }

    @Test
    fun `Gson preserves waiting prediction validity and official fallback across snapshot persistence`() {
        val gson = Gson()
        val snapshot = pendingSnapshot()

        val json = gson.toJson(snapshot)
        val restored = gson.fromJson(json, ScheduleWidgetSnapshot::class.java)

        assertEquals(snapshot, restored)
        assertEquals(ScheduleWidgetPendingStatus.WAITING, restored.singleLesson.lesson?.pendingStatus)
        assertEquals(
            listOf(ScheduleWidgetPendingStatus.WAITING, null, ScheduleWidgetPendingStatus.PREDICTED, null),
            restored.lessonList.mapNotNull { it.lesson }.map { it.pendingStatus }
        )
        assertEquals(snapshot.pendingValidUntil, restored.pendingValidUntil)
        assertEquals(snapshot.officialFallback, restored.withoutPendingSport())
        assertEquals(
            snapshot.officialFallback,
            restored.forPendingAvailability(enabled = false, now = NOW)
        )
        val encodedLesson = gson.toJsonTree(snapshot.singleLesson.lesson).asJsonObject
        assertEquals("WAITING", encodedLesson.get("pendingStatus").asString)
        assertFalse(gson.toJsonTree(snapshot.officialFallback!!.singleLesson.lesson).asJsonObject.has("pendingStatus"))
    }

    @Test
    fun `legacy Gson snapshot without pending fields remains official and usable`() {
        val json = """
            {
              "singleLesson": {
                "kind": "LESSON",
                "lesson": {
                  "subject": "Математика",
                  "start": "12:00",
                  "end": "13:30",
                  "typeId": 1,
                  "state": "UPCOMING"
                },
                "remainingLessons": 1
              },
              "lessonList": [{"kind": "END"}],
              "singleLessonStyle": "DOT",
              "lessonListStyle": "DOT"
            }
        """.trimIndent()

        val snapshot = Gson().fromJson(json, ScheduleWidgetSnapshot::class.java)

        assertEquals("Математика", snapshot.singleLesson.lesson?.subject)
        assertEquals(1, snapshot.singleLesson.remainingLessons)
        assertNull(snapshot.singleLesson.lesson?.pendingStatus)
        assertNull(snapshot.officialFallback)
        assertNull(snapshot.pendingValidUntil)
        assertSame(snapshot, snapshot.withoutPendingSport())
        assertSame(snapshot, snapshot.forPendingAvailability(enabled = false, now = NOW))
        assertTrue(snapshot.canBeShownWhenRefreshFails())
    }

    private fun pendingSnapshot(): ScheduleWidgetSnapshot {
        val firstOfficial = widgetLesson("Математика", "12:00")
        val secondOfficial = widgetLesson("Физика", "14:00")
        val official = ScheduleWidgetSnapshot(
            singleLesson = SingleLessonWidgetContent(SingleLessonWidgetKind.LESSON, firstOfficial, remainingLessons = 1),
            lessonList = listOf(
                ScheduleListWidgetItem(ScheduleListWidgetItemKind.LESSON, firstOfficial),
                ScheduleListWidgetItem(ScheduleListWidgetItemKind.LESSON, secondOfficial),
                ScheduleListWidgetItem(ScheduleListWidgetItemKind.END)
            ),
            singleLessonStyle = LessonStyle.DOT,
            lessonListStyle = LessonStyle.DOT
        )
        val waiting = widgetLesson("Плавание", "11:00", ScheduleWidgetPendingStatus.WAITING)
        val predicted = widgetLesson("Волейбол", "13:45", ScheduleWidgetPendingStatus.PREDICTED)
        return ScheduleWidgetSnapshot(
            singleLesson = SingleLessonWidgetContent(SingleLessonWidgetKind.LESSON, waiting, remainingLessons = 3),
            lessonList = listOf(
                ScheduleListWidgetItem(ScheduleListWidgetItemKind.LESSON, waiting),
                official.lessonList[0],
                ScheduleListWidgetItem(ScheduleListWidgetItemKind.LESSON, predicted),
                official.lessonList[1],
                ScheduleListWidgetItem(ScheduleListWidgetItemKind.END)
            ),
            singleLessonStyle = LessonStyle.DOT,
            lessonListStyle = LessonStyle.DOT,
            officialFallback = official,
            pendingValidUntil = NOW.plusSeconds(300).toString()
        )
    }

    private fun widgetLesson(
        subject: String,
        start: String,
        pendingStatus: ScheduleWidgetPendingStatus? = null,
    ) = ScheduleWidgetLesson(
        subject = subject,
        start = start,
        end = java.time.LocalTime.parse(start).plusMinutes(90).toString(),
        typeId = if (pendingStatus == null) 1 else 11,
        teacher = null,
        room = null,
        building = null,
        state = ScheduleWidgetLessonState.UPCOMING,
        pendingStatus = pendingStatus
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-10T07:00:00Z")
    }
}
