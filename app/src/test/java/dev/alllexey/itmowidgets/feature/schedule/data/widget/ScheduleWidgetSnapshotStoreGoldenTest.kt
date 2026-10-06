package dev.alllexey.itmowidgets.feature.schedule.data.widget

import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.settings.LessonStyle
import dev.alllexey.itmowidgets.core.settings.WidgetTextSize
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.storage.ScheduleCheckPreferences
import dev.alllexey.itmowidgets.core.testing.FakeSessionTokenStore
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.testing.InMemoryPreferencesDataStore
import dev.alllexey.itmowidgets.core.testing.testAppDispatchers
import dev.alllexey.itmowidgets.feature.schedule.data.assertSameJson
import dev.alllexey.itmowidgets.feature.schedule.data.copyStored22
import dev.alllexey.itmowidgets.feature.schedule.data.stored22
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleListWidgetItem
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleListWidgetItemKind
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetLesson
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetLessonState
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetPendingStatus
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshot
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SingleLessonWidgetContent
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SingleLessonWidgetKind
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okio.Path
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * `no_backup/widgets/schedule_snapshot.json`: 2.2's Gson snapshots read through the legacy path, new writes carry
 * `formatVersion` 2 first, and an unreadable file shows the loading state.
 */
class ScheduleWidgetSnapshotStoreGoldenTest {
    @get:Rule val temporary = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private val preferences = ScheduleCheckPreferences(InMemoryPreferencesDataStore())
    private val directory get() = File(temporary.root, "widgets")
    private val file get() = File(directory, "schedule_snapshot.json")

    @Test
    fun `the 2_2 snapshot reads into the captured values and is rewritten with the marker`() = runTest(dispatcher) {
        copyStored22(G04, directory, "schedule_snapshot.json")
        val store = store()

        val snapshot = store.read()

        assertEquals(captured, snapshot)
        store.write(snapshot)
        assertTrue(file.readText(), file.readText().startsWith(MARKER))
        assertSameJson(withMarker(stored22(G04).decodeToString()), file.readText())
        assertEquals(captured, store().read())
    }

    @Test
    fun `a 2_2 snapshot with an official fallback and pending rows reads equal`() = runTest(dispatcher) {
        preferences.setScheduleSportAutoSignEnabled(true)
        copyStored22(SP08, directory, "schedule_snapshot.json")
        val store = store()

        val snapshot = store.read()

        assertEquals(withPending, snapshot)
        store.write(snapshot)
        assertSameJson(withMarker(stored22(SP08).decodeToString()), file.readText())
        assertEquals(withPending, store().read())
        assertEquals(official, store(at = LocalDateTime(2026, 10, 5, 10, 30)).read())
    }

    @Test
    fun `version 2 round-trips byte for byte`() = runTest(dispatcher) {
        preferences.setScheduleSportAutoSignEnabled(true)

        store().write(withPending)

        assertEquals(stored22(V2).decodeToString(), file.readText())
        copyStored22(V2, directory, "schedule_snapshot.json")
        assertEquals(withPending, store().read())
    }

    @Test
    fun `a corrupt file or one of another version reads as loading`() = runTest(dispatcher) {
        directory.mkdirs()
        val v2 = stored22(V2).decodeToString()
        listOf(
            "{",
            "[]",
            "{\"formatVersion\":2}",
            "{\"formatVersion\":null," + v2.removePrefix(MARKER),
            "{\"formatVersion\":3," + v2.removePrefix(MARKER),
            v2.replace("\"LESSON\"", "\"LECTURE\""),
        ).forEach { content ->
            file.writeText(content)
            assertEquals(content, ScheduleWidgetSnapshot.loading(), store().read())
        }
    }

    @Test
    fun `a signed-out session never reads the file`() = runTest(dispatcher) {
        copyStored22(G04, directory, "schedule_snapshot.json")

        assertEquals(ScheduleWidgetSnapshot.signedOut(), store(tokens = FakeSessionTokenStore(signedIn = false)).read())
    }

    private fun store(
        at: LocalDateTime = LocalDateTime(2026, 10, 5, 9, 0),
        tokens: FakeSessionTokenStore = FakeSessionTokenStore(),
    ) = ScheduleWidgetSnapshotStoreImpl(
        directories = Directories(temporary.root.toOkioPath()),
        scheduleChecks = preferences,
        backend = OptedInBackend,
        timeProvider = FixedAcademicTime(at),
        tokens = tokens,
        dispatchers = testAppDispatchers(dispatcher)
    )

    private fun withMarker(json: String): String {
        val fields = Json.parseToJsonElement(json).jsonObject
        return JsonObject(mapOf("formatVersion" to JsonPrimitive(2)) + fields).toString()
    }

    private class Directories(override val noBackup: Path) : AppDirectories {
        override val files: Path get() = error("The snapshot lives in noBackup")
        override val cache: Path get() = error("The snapshot lives in noBackup")
    }

    private object OptedInBackend : BackendGate {
        override suspend fun isConnected() = true
        override fun observeConnected(): Flow<Boolean> = flowOf(true)
        override suspend fun mayCallBackend() = true
        override suspend fun isOptedIn() = true
    }

    private companion object {
        const val G04 = "schedule_snapshot/schedule_snapshot.json"
        const val SP08 = "schedule_snapshot/schedule_snapshot-sp08.json"
        const val V2 = "schedule_snapshot/schedule_snapshot-v2.json"
        const val MARKER = "{\"formatVersion\":2,"

        val capturedLesson = ScheduleWidgetLesson(
            "Тестовая дисциплина", "10:00", "11:30", 1, "Тестовый преподаватель", "101", "Тестовый корпус",
            ScheduleWidgetLessonState.UPCOMING
        )
        val captured = ScheduleWidgetSnapshot(
            singleLesson = SingleLessonWidgetContent(SingleLessonWidgetKind.LESSON, capturedLesson, 2),
            lessonList = listOf(
                ScheduleListWidgetItem(ScheduleListWidgetItemKind.HEADER, dateIso = "2026-10-05", tomorrow = true),
                ScheduleListWidgetItem(ScheduleListWidgetItemKind.LESSON, capturedLesson),
                ScheduleListWidgetItem(ScheduleListWidgetItemKind.END)
            ),
            singleLessonStyle = LessonStyle.DOT,
            lessonListStyle = LessonStyle.LINE,
            compactTextSize = WidgetTextSize.LARGE,
            fullTextSize = WidgetTextSize.EXTRA_LARGE
        )

        val currentLesson = ScheduleWidgetLesson(
            "Тестовый предмет", "10:00", "11:30", 1, "Тестовый преподаватель", "1506", "Тестовый корпус",
            ScheduleWidgetLessonState.CURRENT
        )
        val official = ScheduleWidgetSnapshot(
            singleLesson = SingleLessonWidgetContent(SingleLessonWidgetKind.LESSON, currentLesson, 2),
            lessonList = listOf(
                ScheduleListWidgetItem(ScheduleListWidgetItemKind.HEADER, dateIso = "2026-10-05"),
                ScheduleListWidgetItem(ScheduleListWidgetItemKind.LESSON, currentLesson),
                ScheduleListWidgetItem(ScheduleListWidgetItemKind.END, tomorrow = true)
            ),
            singleLessonStyle = LessonStyle.LINE,
            lessonListStyle = LessonStyle.DOT,
            compactTextSize = WidgetTextSize.LARGE
        )
        val pendingSport = ScheduleWidgetLesson(
            "Спорт", "10:00", "11:30", 11, null, null, null, ScheduleWidgetLessonState.UPCOMING,
            ScheduleWidgetPendingStatus.PREDICTED
        )
        val withPending = official.copy(
            lessonList = official.lessonList + ScheduleListWidgetItem(ScheduleListWidgetItemKind.LESSON, pendingSport),
            officialFallback = official,
            pendingValidUntil = "2026-10-05T07:00:00Z"
        )
    }
}
