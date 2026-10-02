package dev.alllexey.itmowidgets.feature.schedule.data.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.schedule.WritableCalendar
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.CalendarEvent
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.PhoneCalendars
import javax.inject.Inject

/**
 * The phone's calendars through `CalendarContract`. The app's own calendar is a local one (`ACCOUNT_TYPE_LOCAL`),
 * created and deleted through the sync-adapter URI, which a local account allows any app to use; events in any
 * calendar are ordinary app events. Only ids the app stored itself are ever updated or deleted.
 */
class AndroidPhoneCalendars @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val time: AcademicTimeProvider
) : PhoneCalendars {

    private val resolver get() = context.contentResolver

    override fun hasAccess(): Boolean = PERMISSIONS.all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    override fun writable(): List<WritableCalendar> = query(
        "${Calendars.CALENDAR_ACCESS_LEVEL} >= ? AND NOT (${Calendars.ACCOUNT_TYPE} = ? AND ${Calendars.ACCOUNT_NAME} = ?)",
        arrayOf(Calendars.CAL_ACCESS_CONTRIBUTOR.toString(), ACCOUNT_TYPE, ACCOUNT_NAME)
    )

    override fun find(id: Long): WritableCalendar? = query(
        "${Calendars._ID} = ? AND ${Calendars.CALENDAR_ACCESS_LEVEL} >= ?",
        arrayOf(id.toString(), Calendars.CAL_ACCESS_CONTRIBUTOR.toString())
    ).firstOrNull()

    override fun findOwn(): Long? = resolver.query(
        Calendars.CONTENT_URI,
        arrayOf(Calendars._ID),
        "${Calendars.ACCOUNT_TYPE} = ? AND ${Calendars.ACCOUNT_NAME} = ? AND ${Calendars.NAME} = ?",
        arrayOf(ACCOUNT_TYPE, ACCOUNT_NAME, OWN_CALENDAR_NAME),
        null
    )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }

    override fun createOwn(): Long {
        val values = ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            put(Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
            put(Calendars.NAME, OWN_CALENDAR_NAME)
            put(Calendars.CALENDAR_DISPLAY_NAME, context.getString(R.string.app_name))
            put(Calendars.CALENDAR_COLOR, ContextCompat.getColor(context, R.color.calendar_app))
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.OWNER_ACCOUNT, ACCOUNT_NAME)
            put(Calendars.CALENDAR_TIME_ZONE, time.zoneId.id)
            put(Calendars.VISIBLE, 1)
            put(Calendars.SYNC_EVENTS, 1)
        }
        val uri = checkNotNull(resolver.insert(syncAdapter(Calendars.CONTENT_URI), values)) { "The calendar was not created" }
        return ContentUris.parseId(uri)
    }

    override fun deleteOwn(id: Long) {
        resolver.delete(
            syncAdapter(ContentUris.withAppendedId(Calendars.CONTENT_URI, id)),
            "${Calendars.ACCOUNT_TYPE} = ? AND ${Calendars.ACCOUNT_NAME} = ?",
            arrayOf(ACCOUNT_TYPE, ACCOUNT_NAME)
        )
    }

    override fun insert(calendarId: Long, event: CalendarEvent): Long {
        val values = values(event).apply {
            put(Events.CALENDAR_ID, calendarId)
            put(Events.UID_2445, event.uid)
        }
        val uri = checkNotNull(resolver.insert(Events.CONTENT_URI, values)) { "The event was not inserted" }
        return ContentUris.parseId(uri)
    }

    override fun update(eventId: Long, event: CalendarEvent): Boolean =
        resolver.update(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), values(event), "${Events.DELETED} = 0", null) > 0

    override fun delete(eventId: Long) {
        resolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), null, null)
    }

    private fun values(event: CalendarEvent) = ContentValues().apply {
        put(Events.TITLE, event.title)
        put(Events.DTSTART, event.start.toEpochMilli())
        put(Events.DTEND, event.end.toEpochMilli())
        put(Events.EVENT_TIMEZONE, time.zoneId.id)
        put(Events.EVENT_LOCATION, event.location.orEmpty())
        put(Events.DESCRIPTION, event.description.orEmpty())
        put(Events.AVAILABILITY, Events.AVAILABILITY_BUSY)
        put(Events.HAS_ALARM, 0)
    }

    private fun query(selection: String, arguments: Array<String>): List<WritableCalendar> = resolver.query(
        Calendars.CONTENT_URI,
        arrayOf(Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME),
        selection,
        arguments,
        "${Calendars.ACCOUNT_NAME}, ${Calendars.CALENDAR_DISPLAY_NAME}"
    )?.use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(WritableCalendar(cursor.getLong(0), cursor.getString(1).orEmpty(), cursor.getString(2).orEmpty()))
            }
        }
    }.orEmpty()

    private fun syncAdapter(uri: Uri): Uri = uri.buildUpon()
        .appendQueryParameter(android.provider.CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
        .appendQueryParameter(Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
        .build()

    companion object {
        val PERMISSIONS = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        const val ACCOUNT_NAME = "ITMO.Widgets"
        const val ACCOUNT_TYPE = android.provider.CalendarContract.ACCOUNT_TYPE_LOCAL
        const val OWN_CALENDAR_NAME = "itmo_widgets_schedule"
    }
}
