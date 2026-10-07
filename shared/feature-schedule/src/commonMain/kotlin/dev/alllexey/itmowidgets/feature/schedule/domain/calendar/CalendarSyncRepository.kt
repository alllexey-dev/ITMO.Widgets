package dev.alllexey.itmowidgets.feature.schedule.domain.calendar

import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncResult
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncState
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/** The own personal schedule straight from My ITMO, without the schedule cache and without Backend. */
fun interface OwnScheduleSource {
    /** Days of [start]..[end]; throws when My ITMO does not answer with data. */
    suspend fun read(start: LocalDate, end: LocalDate): List<DaySchedule>
}

/** Synchronization state and the app's events in the phone's calendar. */
interface CalendarSyncRepository {
    fun observeState(): Flow<CalendarSyncState>

    suspend fun isEnabled(): Boolean

    /** A calendar the app left is still swept for events that came back; the work stays until it is clean. */
    suspend fun hasPendingCleanup(): Boolean

    suspend fun enable(): CalendarSyncResult

    suspend fun disable()

    /**
     * Sweeps calendars the app left, then brings the window today..today+28 in line with My ITMO. Success also when
     * synchronization is off or turned itself off because the permission or the calendar is gone; a failure only when
     * My ITMO or the provider failed.
     */
    suspend fun sync(): AppResult<Unit>
}
