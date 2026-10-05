package dev.alllexey.itmowidgets.core.schedule

import dev.alllexey.itmowidgets.core.work.BackgroundCheck
import kotlinx.coroutines.flow.Flow

/** Why synchronization turned itself off. */
enum class CalendarSyncProblem { NO_PERMISSION, CALENDAR_MISSING }

/** How turning synchronization on ended. */
enum class CalendarSyncResult { DONE, NO_PERMISSION, FAILED, DEMO_UNAVAILABLE }

data class CalendarSyncState(
    val enabled: Boolean = false,
    /** Why synchronization turned itself off; cleared when the user turns it on or off. */
    val problem: CalendarSyncProblem? = null
)

/**
 * Synchronization of the own schedule with the app's own local calendar «ITMO.Widgets» on the phone; implemented by
 * the schedule feature. Calendar apps that read the phone's calendars (Xiaomi, Samsung, Yandex) show it, Google
 * Calendar does not. Google-account calendars are never written: Google brings back events the app deletes in bulk.
 */
interface CalendarSync : BackgroundCheck {
    fun observeState(): Flow<CalendarSyncState>

    /** Turns synchronization on into the app's own calendar and syncs soon. */
    suspend fun enable(): CalendarSyncResult

    /** Deletes the app's calendar with its events and stops the work. */
    suspend fun disable()

    /** Makes the periodic work match the switch and the session; safe to repeat. */
    override suspend fun syncWork()

    override fun stopWork()

    /** One sync as soon as the network allows when synchronization is on, as after a manual refresh. */
    suspend fun requestSync()
}
