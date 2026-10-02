package dev.alllexey.itmowidgets.feature.schedule

import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncResult
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncState
import dev.alllexey.itmowidgets.core.schedule.CalendarTarget
import dev.alllexey.itmowidgets.core.schedule.WritableCalendar
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.CalendarEvent
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.CalendarSyncRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.CalendarSyncScheduler
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.PhoneCalendars
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** The phone's calendars in memory: [events] per calendar id; [access] off makes every call throw. */
class FakePhoneCalendars : PhoneCalendars {
    var access = true
    /** Ids of calendars that exist and accept events; the own one is added by [createOwn]. */
    val calendars = linkedMapOf<Long, WritableCalendar>()
    var ownId: Long? = null
    val events = linkedMapOf<Long, Pair<Long, CalendarEvent>>()
    var inserts = 0
    var updates = 0
    var deletes = 0
    /** The insert call with this number (1-based, counting failed ones) throws, as a provider failing midway. */
    var failOnInsert: Int? = null
    private var insertCalls = 0
    private var nextId = 100L

    fun add(calendar: WritableCalendar) {
        calendars[calendar.id] = calendar
    }

    fun eventsIn(calendarId: Long): List<CalendarEvent> =
        events.values.filter { it.first == calendarId }.map { it.second }

    override fun hasAccess() = access

    override fun writable(): List<WritableCalendar> = checked { calendars.values.filter { it.id != ownId } }

    override fun find(id: Long): WritableCalendar? = checked { calendars[id] }

    override fun findOwn(): Long? = checked { ownId?.takeIf { it in calendars } }

    override fun createOwn(): Long = checked {
        val id = nextId++
        calendars[id] = WritableCalendar(id, "ITMO.Widgets", "ITMO.Widgets")
        ownId = id
        id
    }

    override fun deleteOwn(id: Long) = checked {
        if (id == ownId) {
            calendars.remove(id)
            events.entries.removeAll { it.value.first == id }
            ownId = null
        }
    }

    override fun insert(calendarId: Long, event: CalendarEvent): Long = checked {
        if (++insertCalls == failOnInsert) throw IllegalStateException("The provider failed")
        check(calendarId in calendars) { "No calendar $calendarId" }
        inserts++
        val id = nextId++
        events[id] = calendarId to event
        id
    }

    override fun update(eventId: Long, event: CalendarEvent): Boolean = checked {
        updates++
        val current = events[eventId] ?: return@checked false
        events[eventId] = current.first to event
        true
    }

    override fun delete(eventId: Long) = checked {
        deletes++
        events.remove(eventId)
        Unit
    }

    private fun <T> checked(block: () -> T): T {
        if (!access) throw SecurityException("No calendar permission")
        return block()
    }
}

class FakeCalendarSyncScheduler : CalendarSyncScheduler {
    var ensureCalls = 0
    var runOnceCalls = 0
    var cancelCalls = 0

    override fun ensurePeriodic() {
        ensureCalls++
    }

    override fun runOnce() {
        runOnceCalls++
    }

    override fun cancel() {
        cancelCalls++
    }
}

/** The state in [state]; [enableResult] answers every enable, [syncResult] every sync after [onSync]. */
class FakeCalendarSyncRepository(enabled: Boolean = false) : CalendarSyncRepository {
    val state = MutableStateFlow(CalendarSyncState(enabled = enabled))
    var enableResult = CalendarSyncResult.DONE
    var syncResult: AppResult<Unit> = AppResult.Success(Unit)
    var onSync: () -> Unit = {}
    var syncs = 0
    val enabled = mutableListOf<CalendarTarget>()
    var disables = 0

    override fun observeState(): Flow<CalendarSyncState> = state

    override suspend fun isEnabled() = state.value.enabled

    override suspend fun enable(target: CalendarTarget): CalendarSyncResult {
        enabled += target
        if (enableResult == CalendarSyncResult.DONE) state.value = CalendarSyncState(enabled = true, target = target)
        return enableResult
    }

    override suspend fun disable() {
        disables++
        state.value = CalendarSyncState()
    }

    override suspend fun writableCalendars(): List<WritableCalendar>? = emptyList()

    override suspend fun sync(): AppResult<Unit> {
        syncs++
        onSync()
        return syncResult
    }
}
