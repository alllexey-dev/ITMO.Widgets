package dev.alllexey.itmowidgets.core.debug

import dev.alllexey.itmowidgets.core.schedule.CalendarSync
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncResult
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncState
import kotlinx.coroutines.flow.MutableStateFlow

/** Calendar synchronization in memory for debug hosts: the switch flips, no calendar or work is touched. */
class MemoryCalendarSync(state: CalendarSyncState = CalendarSyncState()) : CalendarSync {
    val state = MutableStateFlow(state)

    override fun observeState() = state

    override suspend fun enable(): CalendarSyncResult {
        state.value = CalendarSyncState(enabled = true)
        return CalendarSyncResult.DONE
    }

    override suspend fun disable() {
        state.value = CalendarSyncState()
    }

    override suspend fun syncWork() = Unit

    override fun stopWork() = Unit

    override suspend fun requestSync() = Unit
}
