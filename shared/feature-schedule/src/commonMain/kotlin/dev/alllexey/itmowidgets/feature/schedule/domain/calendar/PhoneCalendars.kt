package dev.alllexey.itmowidgets.feature.schedule.domain.calendar

import kotlin.time.Instant

/**
 * The phone's calendars as the app sees them: the permission, the app's own calendar and the app's events in any
 * calendar, by the ids the platform gave them. Android implements it over `CalendarContract`
 * (`AndroidPhoneCalendars`). Calls throw when the platform refuses them, for example without the calendar
 * permission; the caller owns IO dispatch.
 */
interface PhoneCalendars {
    fun hasAccess(): Boolean

    /** Whether calendar [id] still exists; the app's own one, or one a previous build wrote to. */
    fun exists(id: Long): Boolean

    /** The app's own local calendar, if it exists. */
    fun findOwn(): Long?

    fun createOwn(): Long

    /** Deletes the app's own calendar [id] with every event in it; any other calendar is left alone. */
    fun deleteOwn(id: Long)

    /** Inserts [event] with the app's tag in its description and the local markers. */
    fun insert(calendarId: Long, event: CalendarEvent): Long

    /** False when the event no longer exists. */
    fun update(eventId: Long, event: CalendarEvent): Boolean

    fun delete(eventId: Long)

    /**
     * Live events of [calendarId] starting in [from]..[to] that carry the app's tag or marker, whether or not their ids
     * are stored: the sweep that finds events whose id was lost or that Google's sync wrote back.
     */
    fun marked(calendarId: Long, from: Instant, to: Instant): List<MarkedEvent>
}

/** An event of the phone's calendar with the app's marker. */
data class MarkedEvent(val eventId: Long, val key: String?, val end: Instant)
