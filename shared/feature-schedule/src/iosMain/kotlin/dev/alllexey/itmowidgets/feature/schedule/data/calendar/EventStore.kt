package dev.alllexey.itmowidgets.feature.schedule.data.calendar

import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.CalendarEvent
import kotlin.time.Instant

/**
 * The calendar database as [EventKitPhoneCalendars] uses it, by EventKit's own string identifiers: [SystemEventStore]
 * over `EKEventStore` in the app, a map in tests. Every call but [hasFullAccess] throws without full access or when
 * EventKit refuses the change; the caller owns IO dispatch.
 */
interface EventStore {
    /** Full access (`EKAuthorizationStatusFullAccess`): write-only access can neither create a calendar nor read. */
    fun hasFullAccess(): Boolean

    fun calendarExists(identifier: String): Boolean

    /** Event calendars titled [title] that the app may write to, in the sources it creates calendars in. */
    fun calendarsTitled(title: String): List<String>

    /** A new event calendar titled [title] in the iCloud source, else the local one. */
    fun createCalendar(title: String): String

    fun removeCalendar(identifier: String)

    /** Inserts [event] into calendar [calendarIdentifier]: busy, no alarms, the description with the app's tag. */
    fun saveEvent(calendarIdentifier: String, event: CalendarEvent): String

    fun eventExists(identifier: String): Boolean

    /** False when the event no longer exists. */
    fun updateEvent(identifier: String, event: CalendarEvent): Boolean

    /** Nothing happens when the event no longer exists. */
    fun removeEvent(identifier: String)

    /** Events of calendar [calendarIdentifier] starting in [from]..[to], with their notes as the tag sweep reads them. */
    fun events(calendarIdentifier: String, from: Instant, to: Instant): List<StoredCalendarItem>
}

/** An event of the calendar database: its identifier, its notes (the app's tag is their last line) and its end. */
data class StoredCalendarItem(val identifier: String, val notes: String?, val end: Instant)
