package dev.alllexey.itmowidgets.feature.schedule.data.calendar

import dev.alllexey.itmowidgets.core.location.BuildingDirectory
import dev.alllexey.itmowidgets.core.network.toAppError
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncProblem
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncResult
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncState
import dev.alllexey.itmowidgets.core.schedule.CalendarTarget
import dev.alllexey.itmowidgets.core.schedule.WritableCalendar
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
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
import kotlinx.coroutines.Dispatchers
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
 * The own schedule of today..today+28 in the phone's calendar. The app touches only its own events: the ids in
 * [CalendarSyncFileStore] and, for ids that were lost, events carrying its marker (package and occurrence key); the
 * calendar it created itself is its own entirely.
 *
 * Every operation runs behind one mutex. Calendar writes and the ids they produce are not cancellable: a cancelled
 * work or a quick switch-off never leaves an inserted event untracked. Every insert is written to the file at once;
 * a delete drops its id only when it went through.
 */
@Singleton
class CalendarSyncRepositoryImpl @Inject constructor(
    private val calendars: PhoneCalendars,
    private val schedule: OwnScheduleSource,
    private val store: CalendarSyncFileStore,
    private val time: AcademicTimeProvider,
    private val buildings: BuildingDirectory
) : CalendarSyncRepository, SessionDataCleaner {

    private val mutex = Mutex()
    private val generation = AtomicLong()
    private val state = MutableStateFlow<StoredCalendarSync?>(null)

    override fun observeState(): Flow<CalendarSyncState> = flow {
        if (state.value == null) mutex.withLock { loaded() }
        emitAll(state.filterNotNull().map { it.toModel() })
    }

    override suspend fun isEnabled(): Boolean = (state.value ?: mutex.withLock { loaded() }).enabled

    override suspend fun enable(target: CalendarTarget): CalendarSyncResult = mutex.withLock {
        writing {
            guarded {
                if (!calendars.hasAccess()) return@guarded CalendarSyncResult.NO_PERMISSION
                val stored = loaded()
                val picked = when (target) {
                    CalendarTarget.AppCalendar -> null
                    is CalendarTarget.PhoneCalendar ->
                        calendars.find(target.id) ?: return@guarded CalendarSyncResult.CALENDAR_MISSING
                }
                val calendarId = picked?.id ?: ownCalendar(stored)
                // Events elsewhere go; the window is written into the new calendar by the sync that follows.
                val remaining = removeOurs(stored, keep = calendarId)
                persist(
                    StoredCalendarSync(
                        enabled = true,
                        target = if (target is CalendarTarget.AppCalendar) TARGET_APP else TARGET_PHONE,
                        calendarId = calendarId,
                        calendarName = picked?.name,
                        calendarAccount = picked?.account,
                        events = remaining
                    )
                )
                CalendarSyncResult.DONE
            } ?: CalendarSyncResult.FAILED
        }
    }

    override suspend fun disable() {
        mutex.withLock {
            writing {
                val stored = loaded()
                if (guarded { calendars.hasAccess() } != true) {
                    // Nothing could be removed: the ids stay, so the same calendar picked later adopts the events.
                    persist(stored.copy(enabled = false, problem = null))
                    return@writing
                }
                val remaining = removeOurs(stored, keep = null)
                // Ids whose delete failed stay and are retried by the next turn on or off.
                persist(StoredCalendarSync(calendarId = stored.calendarId.takeIf { remaining.isNotEmpty() }, events = remaining))
            }
        }
    }

    override suspend fun writableCalendars(): List<WritableCalendar>? = withContext(Dispatchers.IO) {
        try {
            if (calendars.hasAccess()) calendars.writable() else null
        } catch (_: SecurityException) {
            null
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
                withContext(Dispatchers.IO) { store.clear() }
                state.value = StoredCalendarSync()
            }
        }
    }

    /** Holds [mutex]. Only the request to My ITMO can be cancelled; what follows runs to its end. */
    private suspend fun syncOnce(): AppResult<Unit> {
        val started = generation.get()
        val calendarId = writing { usableCalendar() } ?: return AppResult.Success(Unit)
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

    /** The calendar to sync into, or null when off; turns synchronization off when it cannot reach it. */
    private suspend fun usableCalendar(): Long? {
        val stored = loaded()
        if (!stored.enabled) return null
        val calendarId = checkNotNull(stored.calendarId)
        val problem = guarded {
            when {
                !calendars.hasAccess() -> CalendarSyncProblem.NO_PERMISSION
                calendars.find(calendarId) == null -> CalendarSyncProblem.CALENDAR_MISSING
                else -> null
            }
        } ?: return calendarId
        persist(
            when (problem) {
                // The events are still there; the same calendar picked again adopts them.
                CalendarSyncProblem.NO_PERMISSION -> stored.copy(enabled = false, problem = problem.name)
                // Its events went with it; ids left in other calendars stay for the next clean-up.
                CalendarSyncProblem.CALENDAR_MISSING -> StoredCalendarSync(
                    problem = problem.name,
                    events = stored.events.filter { stored.calendarOf(it) != calendarId }
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
        var stored = start.copy(events = removeOurs(start, keep = calendarId))
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
    private fun removeOurs(stored: StoredCalendarSync, keep: Long?): List<StoredEvent> {
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
            val from = time.now().toInstant().minus(SWEEP_BACK)
            guardedNow {
                calendars.marked(calendarId, from, from.plus(SWEEP_BACK).plus(SWEEP_AHEAD))
                    .forEach { calendars.delete(it.eventId) }
            }
        }
        return stored.events.filter { stored.calendarOf(it) == keep } + failed
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
    private suspend fun <T> writing(block: suspend () -> T): T = withContext(NonCancellable + Dispatchers.IO) { block() }

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
        withContext(Dispatchers.IO) { store.write(next) }
        state.value = next
    }

    /** A corrupt file is removed and the state starts empty. Must hold [mutex]. */
    private suspend fun loaded(): StoredCalendarSync = state.value ?: withContext(Dispatchers.IO) {
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
    }
}
