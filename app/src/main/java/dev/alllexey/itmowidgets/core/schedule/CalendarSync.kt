package dev.alllexey.itmowidgets.core.schedule

import kotlinx.coroutines.flow.Flow

/** Where the own schedule is written in the phone's calendar. */
sealed interface CalendarTarget {
    /**
     * The local calendar «ITMO.Widgets» the app creates and deletes itself. Calendar apps that read the phone's
     * calendars (Xiaomi, Yandex) show it; Google Calendar shows only Google-account calendars and does not.
     */
    data object AppCalendar : CalendarTarget

    /** A calendar of the phone the user picked, usually one of a Google account. */
    data class PhoneCalendar(val id: Long) : CalendarTarget
}

/** A Google-account calendar of the phone the app may add events to (access level contributor or higher). */
data class WritableCalendar(val id: Long, val name: String, val account: String)

/** Why synchronization turned itself off. */
enum class CalendarSyncProblem { NO_PERMISSION, CALENDAR_MISSING }

/** How turning synchronization on or moving it to another calendar ended. */
enum class CalendarSyncResult { DONE, NO_PERMISSION, CALENDAR_MISSING, FAILED }

data class CalendarSyncState(
    val enabled: Boolean = false,
    /** The calendar in use, or the last one after synchronization turned itself off. */
    val target: CalendarTarget? = null,
    /** Display name of the picked calendar; null for the app's own calendar. */
    val calendarName: String? = null,
    /** The account of the picked calendar; null for the app's own calendar. */
    val calendarAccount: String? = null,
    /** Why synchronization turned itself off; cleared when the user turns it on or off. */
    val problem: CalendarSyncProblem? = null,
    /** The calendar in use holds events of the app, which turning off or picking another calendar deletes. */
    val hasEvents: Boolean = false
)

/** Synchronization of the own schedule with the phone's calendar; implemented by the schedule feature. */
interface CalendarSync {
    fun observeState(): Flow<CalendarSyncState>

    /** Turns synchronization on into [target], or moves the app's events there, and syncs soon. */
    suspend fun enable(target: CalendarTarget): CalendarSyncResult

    /** Deletes the app's events (and its own calendar) and stops the work. */
    suspend fun disable()

    /** Google-account calendars the user may pick, without the app's own; null without the calendar permission. */
    suspend fun writableCalendars(): List<WritableCalendar>?

    /** Makes the periodic work match the switch and the session; safe to repeat. */
    suspend fun syncWork()

    fun stopWork()

    /** One sync as soon as the network allows when synchronization is on, as after a manual refresh. */
    suspend fun requestSync()
}
