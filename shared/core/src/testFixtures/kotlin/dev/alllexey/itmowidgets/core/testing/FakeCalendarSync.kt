package dev.alllexey.itmowidgets.core.testing

import dev.alllexey.itmowidgets.core.schedule.CalendarSync
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncResult
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncState
import kotlinx.coroutines.flow.MutableStateFlow

/** The state in [state]; [enableResult] answers every enable. */
class FakeCalendarSync(state: CalendarSyncState = CalendarSyncState()) : CalendarSync {
    val state = MutableStateFlow(state)
    var enableResult = CalendarSyncResult.DONE
    var enables = 0
    var disables = 0
    var syncRequests = 0

    override fun observeState() = state

    override suspend fun enable(): CalendarSyncResult {
        enables++
        if (enableResult == CalendarSyncResult.DONE) state.value = CalendarSyncState(enabled = true)
        return enableResult
    }

    override suspend fun disable() {
        disables++
        state.value = CalendarSyncState()
    }

    override suspend fun syncWork() = Unit

    override fun stopWork() = Unit

    override suspend fun requestSync() {
        syncRequests++
    }
}
