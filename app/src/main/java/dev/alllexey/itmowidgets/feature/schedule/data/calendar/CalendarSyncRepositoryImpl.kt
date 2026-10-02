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
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
 * The own schedule of today..today+28 in the phone's calendar. The app touches only the events it inserted, known by
 * the ids in [CalendarSyncFileStore]; the calendar it created itself is its own entirely.
 *
 * Syncs run one at a time and ask My ITMO outside the state lock, so switching off never waits for the network; a
 * sync whose calendar or session changed meanwhile writes nothing.
 */
@Singleton
class CalendarSyncRepositoryImpl @Inject constructor(
    private val calendars: PhoneCalendars,
    private val schedule: OwnScheduleSource,
    private val store: CalendarSyncFileStore,
    private val time: AcademicTimeProvider,
    private val buildings: BuildingDirectory
) : CalendarSyncRepository, SessionDataCleaner {

    private val syncs = Mutex()
    private val lock = Mutex()
    private val generation = AtomicLong()
    private val state = MutableStateFlow<StoredCalendarSync?>(null)

    override fun observeState(): Flow<CalendarSyncState> = flow {
        lock.withLock { loaded() }
        emitAll(state.filterNotNull().map { it.toModel() })
    }

    override suspend fun isEnabled(): Boolean = lock.withLock { loaded().enabled }

    override suspend fun enable(target: CalendarTarget): CalendarSyncResult = lock.withLock {
        guarded {
            if (!calendars.hasAccess()) return@guarded CalendarSyncResult.NO_PERMISSION
            val stored = loaded()
            val (calendarId, name) = when (target) {
                CalendarTarget.AppCalendar -> ownCalendar(stored) to null
                is CalendarTarget.PhoneCalendar ->
                    (calendars.find(target.id) ?: return@guarded CalendarSyncResult.CALENDAR_MISSING).let { it.id to it.name }
            }
            val events = moved(stored, calendarId)
            persist(
                StoredCalendarSync(
                    enabled = true,
                    target = if (target is CalendarTarget.AppCalendar) TARGET_APP else TARGET_PHONE,
                    calendarId = calendarId,
                    calendarName = name,
                    events = events.map { it.toStored() }
                )
            )
            CalendarSyncResult.DONE
        } ?: CalendarSyncResult.FAILED
    }

    override suspend fun disable() {
        lock.withLock {
            val stored = loaded()
            val removed = guarded {
                if (calendars.hasAccess()) removeEvents(stored)
                calendars.hasAccess()
            }
            // Without the permission nothing could be removed: the ids stay, so turning on the same calendar later
            // adopts the old events instead of duplicating them.
            persist(if (removed == true) StoredCalendarSync() else stored.copy(enabled = false, problem = null))
        }
    }

    override suspend fun writableCalendars(): List<WritableCalendar>? = withContext(Dispatchers.IO) {
        try {
            if (calendars.hasAccess()) calendars.writable() else null
        } catch (_: SecurityException) {
            null
        }
    }

    override suspend fun sync(): AppResult<Unit> = syncs.withLock {
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
        lock.withLock {
            val stored = loaded()
            guarded { if (calendars.hasAccess()) removeEvents(stored) }
            withContext(Dispatchers.IO) { store.clear() }
            state.value = StoredCalendarSync()
        }
    }

    /** The calendar to sync into, or null when off; turns synchronization off when it cannot reach it. Holds [lock]. */
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
                CalendarSyncProblem.CALENDAR_MISSING -> StoredCalendarSync(problem = problem.name)
            }
        )
        return null
    }

    private suspend fun syncOnce(): AppResult<Unit> {
        val started = generation.get()
        val calendarId = lock.withLock { usableCalendar() } ?: return AppResult.Success(Unit)
        val today = time.today()
        val days = schedule.read(today, today.plusDays(WINDOW_DAYS))
        return lock.withLock {
            val stored = loaded()
            when {
                generation.get() != started -> AppResult.Failure(AppError.Unauthorized)
                !stored.enabled || stored.calendarId != calendarId -> AppResult.Success(Unit)
                else -> apply(stored, calendarId, days, today)
            }
        }
    }

    /** Applies the plan event by event; the ids reached so far are written even when the provider fails midway. */
    private suspend fun apply(
        stored: StoredCalendarSync,
        calendarId: Long,
        days: List<DaySchedule>,
        today: LocalDate
    ): AppResult<Unit> {
        val zone = time.zoneId
        val desired = CalendarEvents.from(days, zone) { lesson ->
            buildings.find(lesson.buildingId, lesson.mainBuildingId, lesson.building?.raw)?.address
        }
        val window = today.atStartOfDay(zone).toInstant()..<today.plusDays(WINDOW_DAYS + 1).atStartOfDay(zone).toInstant()
        val plan = CalendarSyncPlanner.plan(desired, stored.syncedEvents, window, time.now().toInstant())
        val retained = (plan.kept + plan.updates + plan.deletes).mapTo(mutableSetOf()) { it.event.key }
        // Starts from what the calendar has; every applied step replaces its entry.
        val events = stored.syncedEvents.filter { it.event.key in retained }.associateByTo(LinkedHashMap()) { it.event.key }
        val applied = guarded {
            withContext(Dispatchers.IO) {
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
                }
            }
        }
        persist(stored.copy(events = events.values.map { it.toStored() }))
        return if (applied != null) AppResult.Success(Unit) else AppResult.Failure(AppError.Unknown())
    }

    /** The app's own calendar; one the stored ids do not belong to is recreated, so no stray event stays in it. */
    private suspend fun ownCalendar(stored: StoredCalendarSync): Long = withContext(Dispatchers.IO) {
        val existing = calendars.findOwn()
        if (existing != null && stored.target == TARGET_APP && stored.calendarId == existing) return@withContext existing
        existing?.let(calendars::deleteOwn)
        calendars.createOwn()
    }

    /** The app's events, moved to [calendarId] when they are elsewhere; the old own calendar is deleted. */
    private suspend fun moved(stored: StoredCalendarSync, calendarId: Long): List<SyncedEvent> = withContext(Dispatchers.IO) {
        val from = stored.calendarId ?: return@withContext emptyList()
        if (from == calendarId) return@withContext stored.syncedEvents
        val oldReachable = calendars.find(from) != null
        val moved = if (oldReachable) {
            stored.syncedEvents.map { synced -> SyncedEvent(calendars.insert(calendarId, synced.event), synced.event) }
        } else {
            emptyList()
        }
        if (stored.target == TARGET_APP) calendars.deleteOwn(from)
        else if (oldReachable) stored.syncedEvents.forEach { calendars.delete(it.eventId) }
        moved
    }

    private suspend fun removeEvents(stored: StoredCalendarSync) = withContext(Dispatchers.IO) {
        val calendarId = stored.calendarId ?: return@withContext
        if (stored.target == TARGET_APP) calendars.deleteOwn(calendarId)
        else stored.syncedEvents.forEach { calendars.delete(it.eventId) }
    }

    /** [block]'s value, or null when the provider failed or the permission was revoked meanwhile. */
    private suspend fun <T> guarded(block: suspend () -> T): T? = try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        null
    }

    private suspend fun persist(next: StoredCalendarSync) {
        withContext(Dispatchers.IO) { store.write(next) }
        state.value = next
    }

    /** A corrupt file is removed and the state starts empty. Must hold [lock]. */
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
    }
}
