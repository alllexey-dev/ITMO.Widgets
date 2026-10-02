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
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.MarkedEvent
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.PhoneCalendars
import java.time.Instant
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

    /** Only Google accounts: their calendars reach Google Calendar and every device of the account. */
    override fun writable(): List<WritableCalendar> = query(
        "${Calendars.CALENDAR_ACCESS_LEVEL} >= ? AND ${Calendars.ACCOUNT_TYPE} = ?",
        arrayOf(Calendars.CAL_ACCESS_CONTRIBUTOR.toString(), GOOGLE_ACCOUNT_TYPE)
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
        // The provider refuses a selection on an id URI, so the id goes into the selection with the account.
        resolver.delete(
            syncAdapter(Calendars.CONTENT_URI),
            "${Calendars._ID} = ? AND ${Calendars.ACCOUNT_TYPE} = ? AND ${Calendars.ACCOUNT_NAME} = ?",
            arrayOf(id.toString(), ACCOUNT_TYPE, ACCOUNT_NAME)
        )
    }

    override fun insert(calendarId: Long, event: CalendarEvent): Long {
        val values = values(event).apply { put(Events.CALENDAR_ID, calendarId) }
        val uri = checkNotNull(resolver.insert(Events.CONTENT_URI, values)) { "The event was not inserted" }
        return ContentUris.parseId(uri)
    }

    override fun update(eventId: Long, event: CalendarEvent): Boolean =
        resolver.update(
            Events.CONTENT_URI,
            values(event),
            "${Events._ID} = ? AND ${Events.DELETED} = 0",
            arrayOf(eventId.toString())
        ) > 0

    override fun delete(eventId: Long) {
        resolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), null, null)
    }

    private fun values(event: CalendarEvent) = ContentValues().apply {
        put(Events.TITLE, event.title)
        put(Events.DTSTART, event.start.toEpochMilli())
        put(Events.DTEND, event.end.toEpochMilli())
        put(Events.EVENT_TIMEZONE, time.zoneId.id)
        put(Events.EVENT_LOCATION, event.location.orEmpty())
        put(Events.DESCRIPTION, event.taggedDescription)
        put(Events.AVAILABILITY, Events.AVAILABILITY_BUSY)
        put(Events.HAS_ALARM, 0)
        // Local markers; the Google sync adapter drops them from events it writes back, the description line stays.
        put(Events.UID_2445, event.uid)
        put(Events.CUSTOM_APP_PACKAGE, context.packageName)
        put(Events.CUSTOM_APP_URI, event.key)
    }

    override fun marked(calendarId: Long, from: Instant, to: Instant): List<MarkedEvent> = resolver.query(
        Events.CONTENT_URI,
        arrayOf(Events._ID, Events.CUSTOM_APP_URI, Events.DTEND, Events.UID_2445, Events.DESCRIPTION),
        "${Events.CALENDAR_ID} = ? AND ${Events.DELETED} = 0 AND ${Events.DTSTART} >= ? AND ${Events.DTSTART} < ? AND " +
            "(${Events.DESCRIPTION} LIKE ? OR ${Events.CUSTOM_APP_PACKAGE} = ? OR ${Events.UID_2445} LIKE ?)",
        arrayOf(
            calendarId.toString(), from.toEpochMilli().toString(), to.toEpochMilli().toString(),
            "%${CalendarEvent.TAG_PREFIX}lesson-%", context.packageName, "%@${CalendarEvent.UID_DOMAIN}"
        ),
        null
    )?.use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                val key = CalendarEvent.keyOfDescription(cursor.getString(4))
                    ?: cursor.getString(1) ?: cursor.getString(3)?.substringBefore('@')
                add(MarkedEvent(cursor.getLong(0), key, Instant.ofEpochMilli(cursor.getLong(2))))
            }
        }
    }.orEmpty()

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
        const val GOOGLE_ACCOUNT_TYPE = "com.google"
    }
}
