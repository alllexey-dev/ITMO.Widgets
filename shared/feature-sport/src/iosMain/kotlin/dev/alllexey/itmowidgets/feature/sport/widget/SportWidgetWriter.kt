package dev.alllexey.itmowidgets.feature.sport.widget

import dev.alllexey.itmowidgets.core.diagnostics.AppLog
import dev.alllexey.itmowidgets.core.settings.WidgetPalette
import dev.alllexey.itmowidgets.core.storage.AppGroupSnapshotWriter
import dev.alllexey.itmowidgets.core.storage.SnapshotFile
import dev.alllexey.itmowidgets.core.text.buildingShortTitle
import dev.alllexey.itmowidgets.core.text.resolve
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.sport.domain.widget.SportWidgetKind
import dev.alllexey.itmowidgets.feature.sport.domain.widget.SportWidgetTimeline
import dev.alllexey.itmowidgets.feature.sport.domain.widget.SportWidgetTimelineJson
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus

/** The sport widget's timeline up to an instant, `SportWidgetDataProvider.loadTimeline` in the app graph. */
fun interface SportTimelineSource {
    suspend fun loadTimeline(until: Instant): SportWidgetTimeline
}

/**
 * What makes the sport file stale besides time: the session and the widget theme. Each flow may start with its current
 * value; only changes after it count, except for [session], whose first settled value is the writer's first write.
 * Launches, wakes and returns to the foreground write through the background runner's step.
 */
class SportWidgetTriggers(
    val session: Flow<Any>,
    val widgetTheme: Flow<Any?>,
)

/**
 * Keeps [FILE] in the App Group container on the sport widget's timeline (`SportWidgetTimeline`, from now to
 * [DAYS] days ahead: the bookings' days change at midnights) and reloads [KIND] after each write; the widget extension
 * only picks the entry of the moment (docs/ios.md, Data sharing). Buildings are the short titles (`Ломо`), the
 * [palette] is the app's colours of that moment while the widgets follow the theme.
 *
 * A failed load, and a timeline that only says the score failed, keep the previous file: the last score stays on the
 * home screen as the schedule's last timeline does. Sign-out removes the file with the App Group's other snapshots and
 * the next write says signed out.
 */
class SportWidgetWriter(
    private val source: SportTimelineSource,
    private val writer: AppGroupSnapshotWriter,
    private val timeProvider: AcademicTimeProvider,
    private val log: AppLog,
    private val palette: suspend () -> WidgetPalette? = { null },
) {

    private val mutex = Mutex()
    private val requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Asks for a write after a change only the caller knows of (a sport booking); never waits for it. */
    fun refresh() {
        requests.tryEmit(Unit)
    }

    /** Writes the timeline and reloads the widget; one write at a time. Failures are logged and keep the old file. */
    suspend fun publish() = mutex.withLock {
        val timeline = try {
            source.loadTimeline(until())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            log.warn(TAG, "Could not load the sport widget timeline", error)
            return@withLock
        }
        if (timeline.entries.all { it.snapshot.kind == SportWidgetKind.ERROR } && writer.read(FILE) != null) {
            log.warn(TAG, "No sport score for the widget; the previous timeline stays")
            return@withLock
        }
        try {
            writer.write(FILE, timeline.withShortBuildings().copy(palette = palette()), listOf(KIND))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            log.warn(TAG, "Could not write the sport widget timeline", error)
        }
    }

    /** Writes on every trigger in [scope] until the scope ends; overlapping triggers collapse into one write. */
    fun launchIn(scope: CoroutineScope, triggers: SportWidgetTriggers): Job = scope.launch {
        merge(
            requests,
            triggers.session.distinctUntilChanged().map { },
            triggers.widgetTheme.distinctUntilChanged().drop(1).map { }
                .catch { error -> log.warn(TAG, "A sport widget trigger stopped", error) },
        ).conflate().collect { publish() }
    }

    private fun until(): Instant =
        timeProvider.localNow().date.plus(DAYS, DateTimeUnit.DAY).atStartOfDayIn(timeProvider.timeZone)

    companion object {
        /** `sport-widget-v1.json`: the envelope's version is the timeline's own (`SportWidgetTimelineJson`). */
        val FILE = SnapshotFile(
            name = "sport-widget",
            version = SportWidgetTimelineJson.VERSION,
            serializer = SportWidgetTimeline.serializer()
        )

        /** The WidgetKit kind of the sport widget, a stable identifier (`StableIdentifiersTests`). */
        const val KIND = "dev.alllexey.itmowidgets.widget.sport"

        /** A week: the widget names the days of the bookings, which MyITMO publishes about two weeks ahead. */
        const val DAYS = 7

        private const val TAG = "SportWidget"
    }
}

/** The timeline with MyITMO's addresses as the short building titles the widget shows (`Ломо`). */
suspend fun SportWidgetTimeline.withShortBuildings(): SportWidgetTimeline = copy(
    entries = entries.map { entry ->
        entry.copy(
            snapshot = entry.snapshot.copy(
                items = entry.snapshot.items?.map { item ->
                    item.copy(building = item.building?.let { buildingShortTitle(it, BUILDING_MAX_LENGTH).resolve() })
                }
            )
        )
    }
)

/** As the schedule widgets' buildings (`ScheduleTimelineWriter`). */
private const val BUILDING_MAX_LENGTH = 14
