package dev.alllexey.itmowidgets.feature.schedule.domain.calendar

import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncResult
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncState
import dev.alllexey.itmowidgets.core.schedule.CalendarTarget
import dev.alllexey.itmowidgets.core.schedule.WritableCalendar
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * The phone's calendars as the app sees them. Calls throw `SecurityException` without the calendar permission;
 * the caller owns IO dispatch.
 */
interface PhoneCalendars {
    fun hasAccess(): Boolean

    /** Google-account calendars with access level contributor or higher; the picker offers only these. */
    fun writable(): List<WritableCalendar>

    /** The writable calendar [id], the app's own included; null when it is gone or read-only. */
    fun find(id: Long): WritableCalendar?

    /** The app's own local calendar, if it exists. */
    fun findOwn(): Long?

    fun createOwn(): Long

    /** Deletes the app's own calendar [id] with every event in it; any other calendar is left alone. */
    fun deleteOwn(id: Long)

    /** Inserts [event] with the app's marker: its package and the occurrence key, and the ICS UID. */
    fun insert(calendarId: Long, event: CalendarEvent): Long

    /** False when the event no longer exists. */
    fun update(eventId: Long, event: CalendarEvent): Boolean

    fun delete(eventId: Long)

    /**
     * Live events of [calendarId] starting in [from]..[to] that carry the app's marker, whether or not their ids are
     * stored: the sweep that finds events whose id was lost.
     */
    fun marked(calendarId: Long, from: Instant, to: Instant): List<MarkedEvent>
}

/** An event of the phone's calendar with the app's marker. */
data class MarkedEvent(val eventId: Long, val key: String?, val end: Instant)

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

    suspend fun enable(target: CalendarTarget): CalendarSyncResult

    suspend fun disable()

    suspend fun writableCalendars(): List<WritableCalendar>?

    /**
     * Sweeps calendars the app left, then brings the window today..today+28 in line with My ITMO. Success also when
     * synchronization is off or turned itself off because the permission or the calendar is gone; a failure only when
     * My ITMO or the provider failed.
     */
    suspend fun sync(): AppResult<Unit>
}
