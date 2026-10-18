package dev.alllexey.itmowidgets.feature.schedule.data.widget

import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.storage.ScheduleCheckPreferences
import dev.alllexey.itmowidgets.core.testing.FakeSessionTokenStore
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.testing.InMemoryPreferencesDataStore
import dev.alllexey.itmowidgets.core.testing.testAppDispatchers
import dev.alllexey.itmowidgets.feature.schedule.data.copyStored22
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SchedulePreviewLabels
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SchedulePreviewMoment
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SchedulePreviewScenario
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSelector
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshot
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import okio.Path
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * `no_backup/widgets/schedule_snapshot.json`: writes carry `formatVersion` 3 first and read back equal; 2.2's Gson
 * snapshots and version 2 of the lesson-list widgets read as loading until the next update rewrites them.
 */
class ScheduleWidgetSnapshotStoreGoldenTest {
    @get:Rule val temporary = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private val preferences = ScheduleCheckPreferences(InMemoryPreferencesDataStore())
    private val directory get() = File(temporary.root, "widgets")
    private val file get() = File(directory, "schedule_snapshot.json")

    @Test
    fun `a snapshot round-trips with its version marker first`() = runTest(dispatcher) {
        store().write(sample)

        assertTrue(file.readText(), file.readText().startsWith(MARKER))
        // The queues are off: the official twin shows.
        assertEquals(sample.withoutPendingSport(), store().read())
    }

    @Test
    fun `a queue holds until its deadline and the official twin shows after it`() = runTest(dispatcher) {
        preferences.setScheduleSportAutoSignEnabled(true)
        store().write(sample)

        assertEquals(sample, store().read())
        assertEquals(sample.officialFallback, store(at = LocalDateTime(2026, 9, 7, 12, 30)).read())
    }

    @Test
    fun `older snapshots and corrupt files read as loading`() = runTest(dispatcher) {
        listOf(G04, SP08, V2).forEach { old ->
            copyStored22(old, directory, "schedule_snapshot.json")
            assertEquals(old, ScheduleWidgetSnapshot.loading(), store().read())
        }
        listOf("{", "[]", "{\"formatVersion\":3}", "{\"formatVersion\":4}").forEach { content ->
            file.writeText(content)
            assertEquals(content, ScheduleWidgetSnapshot.loading(), store().read())
        }
    }

    @Test
    fun `a signed-out session never reads the file`() = runTest(dispatcher) {
        store().write(sample)

        assertEquals(ScheduleWidgetSnapshot.signedOut(), store(tokens = FakeSessionTokenStore(signedIn = false)).read())
    }

    private fun store(
        at: LocalDateTime = LocalDateTime(2026, 9, 7, 12, 22),
        tokens: FakeSessionTokenStore = FakeSessionTokenStore(),
    ) = ScheduleWidgetSnapshotStoreImpl(
        directories = Directories(temporary.root.toOkioPath()),
        scheduleChecks = preferences,
        backend = OptedInBackend,
        timeProvider = FixedAcademicTime(at),
        tokens = tokens,
        dispatchers = testAppDispatchers(dispatcher)
    )

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
        const val MARKER = "{\"formatVersion\":3,"

        /** The sample Monday at 12:22, with tomorrow's sport queue: a snapshot with its official twin. */
        val sample: ScheduleWidgetSnapshot = SchedulePreviewScenario(ScheduleWidgetSelector()).snapshot(
            ScheduleWidgetSettings(),
            SchedulePreviewMoment.LESSON,
            SchedulePreviewLabels("Матанализ", "Физика", "Программирование", "Английский", "Плавание", "Алгоритмы", "История", "Иванов И. И."),
        )
    }
}
