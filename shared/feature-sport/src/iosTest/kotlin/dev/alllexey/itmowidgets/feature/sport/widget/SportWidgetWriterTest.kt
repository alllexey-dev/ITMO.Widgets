package dev.alllexey.itmowidgets.feature.sport.widget

import dev.alllexey.itmowidgets.core.platform.BundleIdentifiers
import dev.alllexey.itmowidgets.core.settings.AccentColor
import dev.alllexey.itmowidgets.core.settings.ThemeSpec
import dev.alllexey.itmowidgets.core.settings.WidgetPalette
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.storage.AppGroupDirectory
import dev.alllexey.itmowidgets.core.storage.AppGroupSnapshotWriter
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.testing.RecordingAppLog
import dev.alllexey.itmowidgets.designsystem.theme.widgetPalette
import dev.alllexey.itmowidgets.feature.sport.domain.widget.SportWidgetKind
import dev.alllexey.itmowidgets.feature.sport.domain.widget.SportWidgetSampleLabels
import dev.alllexey.itmowidgets.feature.sport.domain.widget.SportWidgetSampleState
import dev.alllexey.itmowidgets.feature.sport.domain.widget.SportWidgetSamples
import dev.alllexey.itmowidgets.feature.sport.domain.widget.SportWidgetSnapshot
import dev.alllexey.itmowidgets.feature.sport.domain.widget.SportWidgetTimeline
import dev.alllexey.itmowidgets.feature.sport.domain.widget.SportWidgetTimelineEntry
import dev.alllexey.itmowidgets.feature.sport.domain.widget.SportWidgetTimelineJson
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.FileSystem
import okio.Path

@OptIn(ExperimentalCoroutinesApi::class)
class SportWidgetWriterTest {

    private val root: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "itmo-sport-widget-test-${Random.nextLong().toULong()}"
    private val log = RecordingAppLog()
    private val directory = appGroup()
    private val reloads = mutableListOf<String>()
    private val source = FakeSource()
    private var palette: WidgetPalette? = null
    private val writer = SportWidgetWriter(
        source = source,
        writer = AppGroupSnapshotWriter(directory, reloader = { kind -> reloads += kind }, json = SportWidgetTimelineJson.json),
        // 12:00 in Moscow on 2026-10-07.
        timeProvider = FixedAcademicTime(),
        log = log,
        palette = { palette },
    )

    @AfterTest
    fun deleteDevice() = FileSystem.SYSTEM.deleteRecursively(root)

    @Test
    fun theTimelineIsWrittenWithShortBuildingsAndReloadsTheSportWidget() = runTest(UnconfinedTestDispatcher()) {
        writer.publish()

        val envelope = Json.parseToJsonElement(FileSystem.SYSTEM.read(directory.file("sport-widget-v1.json")) { readUtf8() })
        assertEquals(1, envelope.jsonObject.getValue("version").jsonPrimitive.int)
        val items = assertNotNull(read().entries.single().snapshot.items)
        assertEquals(listOf("Ломо", "Кронва"), items.map { it.building })
        assertEquals(listOf("dev.alllexey.itmowidgets.widget.sport"), reloads)
    }

    @Test
    fun theTimelineRunsAWeekFromTodayInTheAcademicZone() = runTest(UnconfinedTestDispatcher()) {
        writer.publish()

        assertEquals(listOf(Instant.parse("2026-10-13T21:00:00Z")), source.untils)
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
    fun aFailedScoreKeepsThePreviousTimelineButIsWrittenWhenThereIsNone() = runTest(UnconfinedTestDispatcher()) {
        source.result = timeline(SportWidgetSnapshot.of(SportWidgetKind.ERROR))
        writer.publish()
        assertEquals(SportWidgetKind.ERROR, read().entries.single().snapshot.kind)

        source.result = timeline(SAMPLE)
        writer.publish()
        source.result = timeline(SportWidgetSnapshot.of(SportWidgetKind.ERROR))
        writer.publish()
        source.failure = IllegalStateException("No definition for SportWidgetDataProvider")
        writer.publish()

        assertEquals(SportWidgetKind.CONTENT, read().entries.single().snapshot.kind)
        assertEquals(
            listOf(
                "WARN:SportWidget:No sport score for the widget; the previous timeline stays",
                "WARN:SportWidget:Could not load the sport widget timeline",
            ),
            log.lines
        )
    }

    @Test
    fun theSessionTheThemeAndRequestsEachWrite() = runTest(UnconfinedTestDispatcher()) {
        val session = MutableStateFlow<Any>("signed-in")
        val theme = MutableStateFlow<Any?>("brand")
        writer.launchIn(backgroundScope, SportWidgetTriggers(session, theme))
        assertEquals(1, source.untils.size, "the settled session writes once")

        session.value = "signed-out"
        theme.value = "teal"
        writer.refresh()

        assertEquals(4, source.untils.size)
    }

    private fun read(): SportWidgetTimeline =
        assertNotNull(AppGroupSnapshotWriter(directory, reloader = {}, json = SportWidgetTimelineJson.json).read(SportWidgetWriter.FILE))

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

    private class FakeSource : SportTimelineSource {
        val untils = mutableListOf<Instant>()
        var result: SportWidgetTimeline = timeline(SAMPLE)
        var failure: Exception? = null

        override suspend fun loadTimeline(until: Instant): SportWidgetTimeline {
            untils += until
            failure?.let { throw it }
            return result
        }
    }

    private companion object {
        val SAMPLE = SportWidgetSamples.snapshot(SportWidgetSampleState.QUEUE, SportWidgetSampleLabels("Плавание", "Волейбол"))

        fun timeline(snapshot: SportWidgetSnapshot) = SportWidgetTimeline(
            generatedAt = "2026-10-07T09:00:00Z",
            validUntil = "2026-10-13T21:00:00Z",
            entries = listOf(SportWidgetTimelineEntry("2026-10-07T09:00:00Z", snapshot)),
        )
    }
}
