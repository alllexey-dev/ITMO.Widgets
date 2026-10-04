package dev.alllexey.itmowidgets.feature.schedule.data.calendar

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.location.BuildingDirectory
import dev.alllexey.itmowidgets.core.network.toAppError
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncProblem
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncResult
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncState
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.CalendarEvent
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.CalendarEvents
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.CalendarSyncPlanner
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.CalendarSyncRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.OwnScheduleSource
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.PhoneCalendars
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.SyncedEvent
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The own schedule of today..today+28 in the app's own local calendar «ITMO.Widgets», which the app owns entirely.
 * Google-account calendars are never written: Google writes events back that the app deletes in bulk. Earlier builds
 * could write to one; such a state is turned off and that calendar is cleaned up by the tag sweep below. Outside its
 * own calendar the app touches only its own events: the ids in [CalendarSyncFileStore] and events whose description
 * ends with the app's tag ([CalendarEvent.taggedDescription]).
 *
 * Every operation runs behind one mutex. Calendar writes and the ids they produce are not cancellable: a cancelled
 * work or a quick switch-off never leaves an inserted event untracked. Every insert is written to the file at once;
 * a delete drops its id only when it went through.
 *
 * A delete in a Google calendar is final only once Google's sync adapter uploads it. Android's guard against too many
 * deletions can undo it, and the adapter then writes the server's copies back as new rows, with new ids and without
 * the provider's local `CUSTOM_APP_*` columns. A calendar the app leaves is therefore swept again by its tag on later
 * runs ([StoredCleanup]) until it stays clean for [CLEANUP_PERIOD].
 */
@Singleton
class CalendarSyncRepositoryImpl @Inject constructor(
    private val calendars: PhoneCalendars,
    private val schedule: OwnScheduleSource,
    private val store: CalendarSyncFileStore,
    private val time: AcademicTimeProvider,
    private val buildings: BuildingDirectory,
    private val dispatchers: AppDispatchers
) : CalendarSyncRepository, SessionDataCleaner {

    private val mutex = Mutex()
    private val generation = AtomicLong()
    private val state = MutableStateFlow<StoredCalendarSync?>(null)

    override fun observeState(): Flow<CalendarSyncState> = flow {
        if (state.value == null) mutex.withLock { loaded() }
        emitAll(state.filterNotNull().map { it.toModel() })
    }

    override suspend fun isEnabled(): Boolean = (state.value ?: mutex.withLock { loaded() }).toModel().enabled

    /** A Google calendar of an earlier build counts: the next run turns it off and cleans it up. */
    override suspend fun hasPendingCleanup(): Boolean = (state.value ?: mutex.withLock { loaded() })
        .let { it.cleanups.orEmpty().isNotEmpty() || it.target == TARGET_PHONE }

    override suspend fun enable(): CalendarSyncResult = mutex.withLock {
        writing {
            guarded {
                if (!calendars.hasAccess()) return@guarded CalendarSyncResult.NO_PERMISSION
                val stored = loaded()
                val calendarId = ownCalendar(stored)
                // Events elsewhere (a Google calendar of an earlier build) go; the sync that follows fills the window.
                val (remaining, left) = removeOurs(stored, keep = calendarId)
                persist(
                    StoredCalendarSync(
                        enabled = true,
                        target = TARGET_APP,
                        calendarId = calendarId,
                        events = remaining,
                        cleanups = withCleanups(stored, left).filter { it.calendarId != calendarId }
                    )
                )
                CalendarSyncResult.DONE
            } ?: CalendarSyncResult.FAILED
        }
    }

    override suspend fun disable() {
        mutex.withLock {
            writing {
                // The stop flag first: a sync queued behind this lock finds synchronization off and writes nothing.
                val stored = loaded().copy(enabled = false, problem = null).also { persist(it) }
                if (guarded { calendars.hasAccess() } != true) {
                    // Nothing could be removed: the ids stay for the next turn on or off.
                    return@writing
                }
                val (remaining, left) = removeOurs(stored, keep = null)
                // The app's calendar goes in one operation. Ids whose delete failed stay and are retried by the next
                // turn on or off; a Google calendar of an earlier build is swept again until it stays clean.
                persist(
                    StoredCalendarSync(
                        calendarId = stored.calendarId.takeIf { remaining.isNotEmpty() },
                        events = remaining,
                        cleanups = withCleanups(stored, left)
                    )
                )
            }
        }
    }

    override suspend fun sync(): AppResult<Unit> = mutex.withLock {
        try {
            syncOnce()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            AppResult.Failure(error.toAppError())
        }
    }

    override suspend fun clearSessionData() {
        generation.incrementAndGet()
        mutex.withLock {
            writing {
                val stored = loaded()
                guarded { if (calendars.hasAccess()) removeOurs(stored, keep = null) }
                withContext(dispatchers.io) { store.clear() }
                state.value = StoredCalendarSync()
            }
        }
    }

    /** Holds [mutex]. Only the request to My ITMO can be cancelled; what follows runs to its end. */
    private suspend fun syncOnce(): AppResult<Unit> {
        val started = generation.get()
        val swept = writing { sweepLeftCalendars() }
        writing { leaveGoogleCalendar() }
        val calendarId = writing { usableCalendar() }
            ?: return if (swept) AppResult.Success(Unit) else AppResult.Failure(AppError.Unknown())
        val today = time.today()
        val days = schedule.read(today, today.plusDays(WINDOW_DAYS))
        return writing {
            val stored = loaded()
            when {
                generation.get() != started -> AppResult.Failure(AppError.Unauthorized)
                !stored.enabled || stored.calendarId != calendarId -> AppResult.Success(Unit)
                else -> apply(stored, calendarId, days, today)
            }
        }
    }

    /**
     * A Google calendar picked by an earlier build: synchronization turns off, the app's events there are deleted and
     * the calendar is swept again later. Nothing is inserted there any more; turning on uses the app's own calendar.
     */
    private suspend fun leaveGoogleCalendar() {
        val stored = loaded()
        if (stored.target != TARGET_PHONE) return
        if (guarded { calendars.hasAccess() } != true) {
            persist(stored.copy(enabled = false, target = null))
            return
        }
        val (remaining, left) = removeOurs(stored, keep = null)
        persist(
            StoredCalendarSync(
                calendarId = stored.calendarId.takeIf { remaining.isNotEmpty() },
                events = remaining,
                cleanups = withCleanups(stored, left)
            )
        )
    }

    /** The calendar to sync into, or null when off; turns synchronization off when it cannot reach it. */
    private suspend fun usableCalendar(): Long? {
        val stored = loaded()
        if (!stored.enabled) return null
        val calendarId = checkNotNull(stored.calendarId)
        val problem = guarded {
            when {
                !calendars.hasAccess() -> CalendarSyncProblem.NO_PERMISSION
                !calendars.exists(calendarId) -> CalendarSyncProblem.CALENDAR_MISSING
                else -> null
            }
        } ?: return calendarId
        persist(
            when (problem) {
                // The events are still there; turning on again adopts the app's calendar with them.
                CalendarSyncProblem.NO_PERMISSION -> stored.copy(enabled = false, problem = problem.name)
                // Its events went with it; ids left in other calendars stay for the next clean-up.
                CalendarSyncProblem.CALENDAR_MISSING -> StoredCalendarSync(
                    problem = problem.name,
                    events = stored.events.filter { stored.calendarOf(it) != calendarId },
                    cleanups = stored.cleanups
                )
            }
        )
        return null
    }

    /**
     * Clears leftovers in other calendars and untracked marked events in the window, then applies the plan event by
     * event, writing the ids after every insert.
     */
    private suspend fun apply(
        start: StoredCalendarSync,
        calendarId: Long,
        days: List<DaySchedule>,
        today: LocalDate
    ): AppResult<Unit> {
        val zone = time.zoneId
        val now = time.now().toInstant()
        val (kept, left) = removeOurs(start, keep = calendarId)
        var stored = start.copy(events = kept, cleanups = withCleanups(start, left))
        persist(stored)
        val window = today.atStartOfDay(zone).toInstant()..<today.plusDays(WINDOW_DAYS + 1).atStartOfDay(zone).toInstant()
        val tracked = stored.events.mapTo(mutableSetOf()) { it.eventId }
        val orphansRemoved = guarded {
            calendars.marked(calendarId, window.start, window.endExclusive)
                .filter { it.eventId !in tracked && it.end > now }
                .forEach { calendars.delete(it.eventId) }
        }
        val desired = CalendarEvents.from(days, zone) { lesson ->
            buildings.find(lesson.buildingId, lesson.mainBuildingId, lesson.building?.raw)?.address
        }
        val synced = stored.events.filter { stored.calendarOf(it) == calendarId }.map { it.toModel() }
        val plan = CalendarSyncPlanner.plan(desired, synced, window, now)
        val retained = (plan.kept + plan.updates + plan.deletes).mapTo(mutableSetOf()) { it.event.key }
        val others = stored.events.filter { stored.calendarOf(it) != calendarId }
        // Starts from what the calendar has; every applied step replaces its entry.
        val events = synced.filter { it.event.key in retained }.associateByTo(LinkedHashMap()) { it.event.key }
        fun current() = stored.copy(events = others + events.values.map { it.toStored(calendarId) })
        val applied = guarded {
            plan.deletes.forEach { synced ->
                calendars.delete(synced.eventId)
                events.remove(synced.event.key)
            }
            plan.updates.forEach { synced ->
                val eventId = if (calendars.update(synced.eventId, synced.event)) synced.eventId
                else calendars.insert(calendarId, synced.event)
                events[synced.event.key] = SyncedEvent(eventId, synced.event)
            }
            plan.inserts.forEach { event ->
                events[event.key] = SyncedEvent(calendars.insert(calendarId, event), event)
                stored = current().also { persist(it) }
            }
        }
        persist(current())
        return if (applied != null && orphansRemoved != null) AppResult.Success(Unit) else AppResult.Failure(AppError.Unknown())
    }

    /**
     * Deletes the app's events outside [keep] (all with null): the stored ids, then the marked events of those
     * calendars that the ids missed. The app's own calendar goes as a whole. Returns the entries still there:
     * those in [keep] and those whose delete failed.
     */
    private fun removeOurs(stored: StoredCalendarSync, keep: Long?): Pair<List<StoredEvent>, List<Long>> {
        val leaving = stored.events.filter { stored.calendarOf(it) != keep }
        val calendarIds = (leaving.mapNotNull { stored.calendarOf(it) } + listOfNotNull(stored.calendarId))
            .filter { it != keep }
            .distinct()
        val own = guardedNow { calendars.findOwn() }
        val failed = mutableListOf<StoredEvent>()
        calendarIds.forEach { calendarId ->
            val inCalendar = leaving.filter { stored.calendarOf(it) == calendarId }
            if (calendarId == own) {
                if (guardedNow { calendars.deleteOwn(calendarId) } == null) failed += inCalendar
                return@forEach
            }
            inCalendar.forEach { event -> if (guardedNow { calendars.delete(event.eventId) } == null) failed += event }
            sweep(calendarId)
        }
        return stored.events.filter { stored.calendarOf(it) == keep } + failed to calendarIds.filter { it != own }
    }

    /** Deletes every live tagged event of [calendarId] in the sweep range; the number found, or null on failure. */
    private fun sweep(calendarId: Long): Int? = guardedNow {
        val from = time.now().toInstant().minus(SWEEP_BACK)
        calendars.marked(calendarId, from, from.plus(SWEEP_BACK).plus(SWEEP_AHEAD))
            .onEach { calendars.delete(it.eventId) }
            .size
    }

    /** [stored]'s pending sweeps plus [left], each due for [CLEANUP_PERIOD] from now. */
    private fun withCleanups(stored: StoredCalendarSync, left: List<Long>): List<StoredCleanup> {
        val until = time.now().toInstant().plus(CLEANUP_PERIOD).toEpochMilli()
        val pending = stored.cleanups.orEmpty().filter { it.calendarId !in left }
        return pending + left.map { StoredCleanup(it, until) }
    }

    /**
     * Sweeps the calendars the app left. One is done when it is gone, became the calendar in use again (the sync's own
     * orphan sweep covers it), or stayed clean past its period; one where events came back starts its period anew.
     * False when a sweep failed.
     */
    private suspend fun sweepLeftCalendars(): Boolean {
        val stored = loaded()
        val pending = stored.cleanups.orEmpty()
        if (pending.isEmpty() || guarded { calendars.hasAccess() } != true) return true
        val now = time.now().toInstant()
        var failed = false
        val next = pending.mapNotNull { cleanup ->
            val inUse = stored.enabled && stored.calendarId == cleanup.calendarId
            if (inUse || guardedNow { calendars.exists(cleanup.calendarId) } != true) return@mapNotNull null
            when (val found = sweep(cleanup.calendarId)) {
                null -> cleanup.also { failed = true }
                0 -> cleanup.takeIf { now.toEpochMilli() < it.until }
                else -> cleanup.copy(until = now.plus(CLEANUP_PERIOD).toEpochMilli())
            }
        }
        if (next != pending) persist(stored.copy(cleanups = next))
        return !failed
    }

    /** The app's own calendar; one the stored ids do not belong to is recreated, so no stray event stays in it. */
    private fun ownCalendar(stored: StoredCalendarSync): Long {
        val existing = calendars.findOwn()
        val ours = stored.events.any { stored.calendarOf(it) == existing } ||
            (stored.target == TARGET_APP && stored.calendarId == existing)
        if (existing != null && ours) return existing
        existing?.let(calendars::deleteOwn)
        return calendars.createOwn()
    }

    /** Calendar writes and the ids they produce must not stop halfway when the caller is cancelled. */
    private suspend fun <T> writing(block: suspend () -> T): T = withContext(NonCancellable + dispatchers.io) { block() }

    /** [block]'s value, or null when the provider failed or the permission was revoked meanwhile. */
    private suspend fun <T> guarded(block: suspend () -> T): T? = try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        null
    }

    private fun <T> guardedNow(block: () -> T): T? = try {
        block()
    } catch (_: Exception) {
        null
    }

    private suspend fun persist(next: StoredCalendarSync) {
        withContext(dispatchers.io) { store.write(next) }
        state.value = next
    }

    /** A corrupt file is removed and the state starts empty. Must hold [mutex]. */
    private suspend fun loaded(): StoredCalendarSync = state.value ?: withContext(dispatchers.io) {
        try {
            store.read() ?: StoredCalendarSync()
        } catch (_: Exception) {
            store.clear()
            StoredCalendarSync()
        }
    }.also { state.value = it }

    private companion object {
        /** The window is today and the next 28 days. */
        const val WINDOW_DAYS = 28L
        /** The sweep of a calendar the app leaves covers what a sync could have written and kept. */
        val SWEEP_BACK: Duration = CalendarSyncPlanner.RETENTION
        val SWEEP_AHEAD: Duration = Duration.ofDays(400)
        /** How long a left calendar must stay clean; Google's sync writes undone deletions back within it. */
        val CLEANUP_PERIOD: Duration = Duration.ofDays(3)
    }
}
