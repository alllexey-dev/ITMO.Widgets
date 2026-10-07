package dev.alllexey.itmowidgets.feature.schedule.widget

import dev.alllexey.itmowidgets.core.diagnostics.AppLog
import dev.alllexey.itmowidgets.core.schedule.ScheduleWidgetRefreshRequester
import dev.alllexey.itmowidgets.core.storage.AppGroupSnapshotWriter
import dev.alllexey.itmowidgets.core.storage.SnapshotFile
import dev.alllexey.itmowidgets.core.text.buildingShortTitle
import dev.alllexey.itmowidgets.core.text.resolve
import dev.alllexey.itmowidgets.core.text.roomShortTitle
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.schedule.data.widget.ScheduleWidgetTimelineLoadResult
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleListWidgetItem
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetLesson
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshot
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetTimeline
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetTimelineJson
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SingleLessonWidgetContent
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.serialization.json.Json

/** The schedule widgets' timeline up to an instant, `ScheduleWidgetDataProvider.loadTimeline` in the app graph. */
fun interface ScheduleTimelineSource {
    suspend fun loadTimeline(until: Instant): ScheduleWidgetTimelineLoadResult
}

/**
 * What makes the timeline stale: the session (sign-in, demo, sign-out), the widget options, the app coming to the
 * foreground and the cached schedule. Each flow may start with its current value; only changes after it count, except
 * for [session], whose first settled value is the writer's first write.
 */
class ScheduleTimelineTriggers(
    val session: Flow<Any>,
    val widgetSettings: Flow<Any>,
    val foregrounds: Flow<Unit>,
    val cachedSchedule: Flow<Any>,
)

/**
 * Keeps [FILE] in the App Group container on the schedule widgets' timeline (LS-3's `ScheduleWidgetTimeline`, from now
 * to the end of tomorrow) and reloads both schedule widget kinds after each write; the widget extension links no
 * Kotlin and only picks the entry of the moment (IO-10b, docs/ios.md, Data sharing).
 *
 * The rooms and buildings in the file are the short titles Android's widget shows (`roomShortTitle`,
 * `buildingShortTitle`): the reader would need the catalog's Russian building names to shorten them. Everything else is the provider's data and keys.
 * An unavailable schedule keeps the previous file, as Android keeps its last snapshot. Times come from the academic
 * clock (the timeline's end) and the wall [clock] (how recently a write started).
 */
@OptIn(ExperimentalAtomicApi::class)
class ScheduleTimelineWriter(
    private val source: ScheduleTimelineSource,
    private val writer: AppGroupSnapshotWriter,
    private val timeProvider: AcademicTimeProvider,
    private val clock: Clock,
    private val log: AppLog,
) : ScheduleWidgetRefreshRequester {

    private val mutex = Mutex()
    private val requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val lastStartMillis = AtomicLong(Long.MIN_VALUE)

    /** Asks for a write after a change only the caller knows of (a sport booking); never waits for it. */
    override fun refreshScheduleWidgets() {
        requests.tryEmit(Unit)
    }

    /**
     * Writes the timeline from now to the end of tomorrow and reloads both kinds; one write at a time. Failures are
     * logged and leave the previous file in place.
     */
    suspend fun publish() = mutex.withLock {
        lastStartMillis.store(clock.now().toEpochMilliseconds())
        val result = try {
            source.loadTimeline(endOfTomorrow())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            log.warn(TAG, "Could not load the schedule widget timeline", error)
            return@withLock
        }
        when (result) {
            is ScheduleWidgetTimelineLoadResult.Unavailable ->
                log.warn(TAG, "No schedule for the widgets; the previous timeline stays")
            is ScheduleWidgetTimelineLoadResult.Available -> write(result.timeline)
        }
    }

    /**
     * Writes on every trigger in [scope] until the scope ends. Overlapping triggers collapse into one write. A change
     * of the cached schedule within [OWN_REFRESH_WINDOW] of a write's start is that write's own refresh and is skipped,
     * so a write never causes another one.
     */
    fun launchIn(scope: CoroutineScope, triggers: ScheduleTimelineTriggers): Job = scope.launch {
        merge(
            requests,
            triggers.session.distinctUntilChanged().map { },
            triggers.widgetSettings.changes(),
            triggers.foregrounds,
            triggers.cachedSchedule.changes().filter { !writeStartedRecently() },
        ).conflate().collect { publish() }
    }

    private fun Flow<Any>.changes(): Flow<Unit> = distinctUntilChanged()
        .drop(1)
        .map { }
        .catch { error -> log.warn(TAG, "A schedule widget trigger stopped", error) }

    private fun writeStartedRecently(): Boolean {
        val started = lastStartMillis.load()
        return started != Long.MIN_VALUE &&
            clock.now() - Instant.fromEpochMilliseconds(started) < OWN_REFRESH_WINDOW
    }

    private fun endOfTomorrow(): Instant {
        val zone = timeProvider.timeZone
        return timeProvider.localNow().date.plus(2, DateTimeUnit.DAY).atStartOfDayIn(zone)
    }

    private suspend fun write(timeline: ScheduleWidgetTimeline) {
        try {
            writer.write(FILE, timeline.withShortLocations(), KINDS)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            log.warn(TAG, "Could not write the schedule widget timeline", error)
        }
    }

    private suspend fun ScheduleWidgetTimeline.withShortLocations(): ScheduleWidgetTimeline =
        copy(entries = entries.map { entry -> entry.copy(snapshot = entry.snapshot.withShortLocations()) })

    private suspend fun ScheduleWidgetSnapshot.withShortLocations(): ScheduleWidgetSnapshot = copy(
        singleLesson = singleLesson.withShortLocations(),
        lessonList = lessonList.map { item -> item.withShortLocations() },
        officialFallback = officialFallback?.withShortLocations(),
    )

    private suspend fun SingleLessonWidgetContent.withShortLocations() = copy(lesson = lesson?.withShortLocations())

    private suspend fun ScheduleListWidgetItem.withShortLocations() = copy(lesson = lesson?.withShortLocations())

    private suspend fun ScheduleWidgetLesson.withShortLocations() = copy(
        room = room?.let { roomShortTitle(it).resolve() },
        building = building?.let { buildingShortTitle(it, BUILDING_MAX_LENGTH).resolve() },
    )

    companion object {
        /** `schedule-timeline-v1.json`: the envelope's version is the timeline's own (`ScheduleWidgetTimelineJson`). */
        val FILE = SnapshotFile(
            name = "schedule-timeline",
            version = ScheduleWidgetTimelineJson.VERSION,
            serializer = ScheduleWidgetTimeline.serializer()
        )

        /** The timeline's JSON rules (`ScheduleWidgetTimelineJson`): nulls omitted, defaults written. */
        val JSON = Json {
            explicitNulls = false
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        /** The WidgetKit kinds of the lesson and the day widget, stable identifiers (`StableIdentifiersTests`). */
        const val SINGLE_LESSON_KIND = "dev.alllexey.itmowidgets.widget.single-lesson"
        const val DAY_SCHEDULE_KIND = "dev.alllexey.itmowidgets.widget.day-schedule"
        val KINDS = listOf(SINGLE_LESSON_KIND, DAY_SCHEDULE_KIND)

        /** Longer than a schedule refresh takes, shorter than a user's next pull to refresh. */
        val OWN_REFRESH_WINDOW = 1.minutes

        /** As Android's `ScheduleWidgetRenderer`. */
        private const val BUILDING_MAX_LENGTH = 14
        private const val TAG = "ScheduleTimeline"
    }
}
