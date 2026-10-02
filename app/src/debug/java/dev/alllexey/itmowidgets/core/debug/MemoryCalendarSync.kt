package dev.alllexey.itmowidgets.core.debug

import dev.alllexey.itmowidgets.core.schedule.CalendarSync
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncResult
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncState
import dev.alllexey.itmowidgets.core.schedule.CalendarTarget
import dev.alllexey.itmowidgets.core.schedule.WritableCalendar
import kotlinx.coroutines.flow.MutableStateFlow

/** Calendar synchronization in memory for debug hosts: the switch flips, no calendar or work is touched. */
class MemoryCalendarSync(state: CalendarSyncState = CalendarSyncState()) : CalendarSync {
    val state = MutableStateFlow(state)
    @Volatile var calendars: List<WritableCalendar> = emptyList()

    override fun observeState() = state

    override suspend fun enable(target: CalendarTarget): CalendarSyncResult {
        val name = (target as? CalendarTarget.PhoneCalendar)?.let { picked -> calendars.firstOrNull { it.id == picked.id }?.name }
        state.value = CalendarSyncState(enabled = true, target = target, calendarName = name)
        return CalendarSyncResult.DONE
    }

    override suspend fun disable() {
        state.value = CalendarSyncState()
    }

    override suspend fun writableCalendars(): List<WritableCalendar> = calendars

    override suspend fun syncWork() = Unit

    override fun stopWork() = Unit

    override suspend fun requestSync() = Unit
}
