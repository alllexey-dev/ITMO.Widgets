package dev.alllexey.itmowidgets.feature.schedule.widget

import dev.alllexey.itmowidgets.core.platform.BundleIdentifiers
import dev.alllexey.itmowidgets.core.settings.AccentColor
import dev.alllexey.itmowidgets.core.settings.LessonStyle
import dev.alllexey.itmowidgets.core.settings.ThemeSpec
import dev.alllexey.itmowidgets.core.settings.WidgetPalette
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.storage.AppGroupDirectory
import dev.alllexey.itmowidgets.core.storage.AppGroupSnapshotWriter
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.testing.RecordingAppLog
import dev.alllexey.itmowidgets.feature.schedule.data.widget.ScheduleWidgetTimelineLoadResult
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetLesson
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetLessonState
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshot
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetTimeline
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetTimelineEntry
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetTimelineJson
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SingleLessonWidgetContent
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SingleLessonWidgetKind
import dev.alllexey.itmowidgets.designsystem.theme.widgetPalette
import dev.alllexey.itmowidgets.testkit.FakeClock
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSProcessInfo

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleTimelineWriterTest {

    private val root: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "itmo-schedule-test-${Random.nextLong().toULong()}"
    private val log = RecordingAppLog()
    private val directory = appGroup()
    private val reloads = mutableListOf<String>()
    private val clock = FakeClock(Instant.parse("2026-10-07T09:00:00Z"))
    private val source = FakeSource()
    private var palette: WidgetPalette? = null
    private val writer = ScheduleTimelineWriter(
        source = source,
        writer = AppGroupSnapshotWriter(directory, reloader = { kind -> reloads += kind }, json = ScheduleTimelineWriter.JSON),
        // 12:00 in Moscow on 2026-10-07.
        timeProvider = FixedAcademicTime(),
        clock = clock,
        log = log,
        palette = { palette },
    )

    @AfterTest
    fun deleteDevice() = FileSystem.SYSTEM.deleteRecursively(root)

    /**
     * LS-3's reference fixture through the writer is the fixture in the App Group envelope: the Swift reader's tests
     * (`ScheduleTimelineTests`) wrap the same file the same way.
     */
    @Test
    fun theReferenceFixtureIsWrittenInTheEnvelopeUnchanged() = runTest(UnconfinedTestDispatcher()) {
        val fixture = FileSystem.SYSTEM.read(repositoryRoot() / FIXTURE) { readUtf8() }
        source.result = ScheduleWidgetTimelineLoadResult.Available(ScheduleWidgetTimelineJson.decode(fixture))

        writer.publish()

        val written = Json.parseToJsonElement(FileSystem.SYSTEM.read(directory.file(FILE_NAME)) { readUtf8() })
        val expected = JsonObject(mapOf("version" to JsonPrimitive(1), "value" to Json.parseToJsonElement(fixture)))
        assertEquals(expected, written)
        assertEquals(listOf(SINGLE_LESSON_KIND, DAY_SCHEDULE_KIND), reloads)
    }

    @Test
    fun eachWriteCarriesTheWidgetThemeOfItsMoment() = runTest(UnconfinedTestDispatcher()) {
        writer.publish()
        assertNull(read().palette)

        palette = ThemeSpec(accent = AccentColor.TEAL).widgetPalette()
        writer.publish()

        assertEquals(ThemeSpec(accent = AccentColor.TEAL).widgetPalette(), read().palette)
    }

    @Test
    fun theTimelineEndsAtTheEndOfTomorrowInTheAcademicZone() = runTest(UnconfinedTestDispatcher()) {
        writer.publish()

        assertEquals(listOf(Instant.parse("2026-10-08T21:00:00Z")), source.untils)
    }

    @Test
    fun roomsAndBuildingsAreTheShortTitlesTheAndroidWidgetShows() = runTest(UnconfinedTestDispatcher()) {
        source.result = available(lesson(room = "ауд. 1506/1 (лекционная)", building = "Корпус на длинной улице, д. 9"))

        writer.publish()

        val lesson = assertNotNull(read().entries.single().snapshot.singleLesson.lesson)
        assertEquals("1506/1", lesson.room)
        assertEquals("Корпус на длин", lesson.building)
    }

    @Test
    fun anUnavailableScheduleKeepsThePreviousTimeline() = runTest(UnconfinedTestDispatcher()) {
        writer.publish()
        val previous = read()
        reloads.clear()

        source.result = ScheduleWidgetTimelineLoadResult.Unavailable(LessonStyle.DOT, LessonStyle.DOT)
        writer.publish()
        source.failure = IllegalStateException("No definition for ScheduleWidgetDataProvider")
        writer.publish()

        assertEquals(previous, read())
        assertEquals(emptyList(), reloads)
        assertEquals(
            listOf(
                "WARN:ScheduleTimeline:No schedule for the widgets; the previous timeline stays",
                "WARN:ScheduleTimeline:Could not load the schedule widget timeline",
            ),
            log.lines
        )
    }

    @Test
    fun theSessionTheOptionsTheForegroundAndSportRequestsEachWrite() = runTest(UnconfinedTestDispatcher()) {
        val triggers = Triggers()
        writer.launchIn(backgroundScope, triggers.asTriggers())
        assertEquals(1, source.untils.size, "the settled session writes once")

        triggers.session.value = "signed-out"
        triggers.settings.value = "large text"
        triggers.foregrounds.emit(Unit)
        writer.refreshScheduleWidgets()

        assertEquals(5, source.untils.size)
        assertEquals(5 * 2, reloads.size)
    }

    @Test
    fun aScheduleChangeWritesUnlessItIsAWritesOwnRefresh() = runTest(UnconfinedTestDispatcher()) {
        val triggers = Triggers()
        writer.launchIn(backgroundScope, triggers.asTriggers())

        triggers.schedule.value = "refreshed by the write"
        assertEquals(1, source.untils.size, "within a minute of the write: its own refresh")

        clock.advanceBy(ScheduleTimelineWriter.OWN_REFRESH_WINDOW + 1.seconds)
        triggers.schedule.value = "refreshed by the schedule screen"
        assertEquals(2, source.untils.size)
    }

    @Test
    fun aStoppedTriggerLeavesTheOthersWorking() = runTest(UnconfinedTestDispatcher()) {
        val triggers = Triggers()
        writer.launchIn(
            backgroundScope,
            triggers.asTriggers().let {
                ScheduleTimelineTriggers(
                    it.session, it.widgetSettings, it.foregrounds,
                    kotlinx.coroutines.flow.flow { throw IllegalStateException("No ScheduleRepository") }
                )
            }
        )

        triggers.foregrounds.emit(Unit)

        assertEquals(2, source.untils.size)
        assertEquals(listOf("WARN:ScheduleTimeline:A schedule widget trigger stopped"), log.lines)
    }

    private fun read(): ScheduleWidgetTimeline =
        assertNotNull(AppGroupSnapshotWriter(directory, reloader = {}).read(ScheduleTimelineWriter.FILE))

    private fun appGroup(): AppGroupDirectory {
        FileSystem.SYSTEM.createDirectories(root)
        return AppGroupDirectory.resolve(
            identifiers = BundleIdentifiers(appGroupId = "group.test.itmo", keychainGroup = null),
            appDirectories = object : AppDirectories {
                override val files = root / "files"
                override val cache = root / "caches"
                override val noBackup = root / "no-backup"
            },
            log = log,
            containerOf = { root / "group" },
        )
    }

    private class Triggers {
        val session = MutableStateFlow<Any>("signed-in")
        val settings = MutableStateFlow<Any>("normal text")
        val foregrounds = MutableSharedFlow<Unit>()
        val schedule = MutableStateFlow<Any>("cached")

        fun asTriggers() = ScheduleTimelineTriggers(session, settings, foregrounds, schedule)
    }

    private class FakeSource : ScheduleTimelineSource {
        val untils = mutableListOf<Instant>()
        var result: ScheduleWidgetTimelineLoadResult = available(lesson(room = "1404", building = null))
        var failure: Exception? = null

        override suspend fun loadTimeline(until: Instant): ScheduleWidgetTimelineLoadResult {
            untils += until
            failure?.let { throw it }
            return result
        }
    }

    private companion object {
        const val FILE_NAME = "schedule-timeline-v1.json"
        const val FIXTURE = "shared/feature-schedule/fixtures/schedule-widget-timeline-v1.json"
        const val SINGLE_LESSON_KIND = "dev.alllexey.itmowidgets.widget.single-lesson"
        const val DAY_SCHEDULE_KIND = "dev.alllexey.itmowidgets.widget.day-schedule"

        val NOW: Instant = Instant.parse("2026-10-07T09:00:00Z")

        fun lesson(room: String?, building: String?) = ScheduleWidgetLesson(
            subject = "Математика",
            start = "12:00",
            end = "13:30",
            typeId = 1,
            room = room,
            building = building,
            state = ScheduleWidgetLessonState.CURRENT,
        )

        fun available(lesson: ScheduleWidgetLesson) = ScheduleWidgetTimelineLoadResult.Available(
            ScheduleWidgetTimeline(
                generatedAt = NOW,
                validUntil = NOW + 36.hours,
                entries = listOf(
                    ScheduleWidgetTimelineEntry(
                        NOW,
                        ScheduleWidgetSnapshot(
                            singleLesson = SingleLessonWidgetContent(SingleLessonWidgetKind.LESSON, lesson),
                            lessonList = emptyList(),
                            singleLessonStyle = LessonStyle.DOT,
                            lessonListStyle = LessonStyle.DOT,
                        )
                    )
                )
            )
        )

        /** The checkout the simulator runs this binary from: the nearest parent with `iosApp/project.yml`. */
        fun repositoryRoot(): Path {
            var directory: Path? = NSProcessInfo.processInfo.arguments.first().toString().toPath().parent
            while (directory != null) {
                if (FileSystem.SYSTEM.exists(directory / "iosApp" / "project.yml")) return directory
                directory = directory.parent
            }
            error("No repository above the test binary ${NSProcessInfo.processInfo.arguments.first()}")
        }
    }
}
