package dev.alllexey.itmowidgets.feature.schedule.data.calendar

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncProblem
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncState
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.CalendarEvent
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.SyncedEvent
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import javax.inject.Inject

/** 1: the switch, the calendar in use and the app's events in it with the content they were given. */
private const val FORMAT = 1

internal const val TARGET_APP = "app"
internal const val TARGET_PHONE = "phone"

/** Everything calendar synchronization keeps; calendar and event ids belong to this device only. */
internal data class StoredCalendarSync(
    val format: Int = FORMAT,
    val enabled: Boolean = false,
    /**
     * [TARGET_APP]; [TARGET_PHONE] only in files of earlier builds that wrote to a Google calendar, which the next
     * operation turns off and cleans up.
     */
    val target: String? = null,
    /** The calendar the [events] are in. */
    val calendarId: Long? = null,
    /** Written by earlier builds for a picked calendar; no longer used. */
    val calendarName: String? = null,
    val calendarAccount: String? = null,
    val problem: String? = null,
    val events: List<StoredEvent> = emptyList(),
    /** Calendars the app left, swept again for events Google's sync writes back; null in older files. */
    val cleanups: List<StoredCleanup>? = null
)

/** A calendar to sweep for the app's events until [until] (epoch millis) has passed with nothing found. */
internal data class StoredCleanup(val calendarId: Long, val until: Long)

internal data class StoredEvent(
    val key: String,
    val eventId: Long,
    /** The calendar the event is in; null in files written before it was stored: the state's calendar. */
    val calendarId: Long? = null,
    val start: Long,
    val end: Long,
    val title: String,
    val location: String?,
    val description: String?
)

/** The synchronization state in `filesDir`, kept out of backups. Caller owns IO dispatch and serialization. */
class CalendarSyncFileStore internal constructor(private val directory: File, private val gson: Gson) {
    @Inject constructor(@ApplicationContext context: Context, gson: Gson) : this(File(context.filesDir, "calendar_sync"), gson)

    private val file get() = File(directory, "state.json")

    /** `null` without a file; throws on a corrupt file or one of another format. */
    internal fun read(): StoredCalendarSync? {
        if (!file.exists()) return null
        val state = checkNotNull(gson.fromJson(file.readText(), StoredCalendarSync::class.java))
        check(state.format == FORMAT) { "Unknown calendar sync format ${state.format}" }
        state.toModel()
        checkNotNull(state.events).forEach { it.toModel() }
        return state
    }

    internal fun write(state: StoredCalendarSync) {
        check(directory.isDirectory || directory.mkdirs())
        val temporary = File(directory, "state.json.tmp")
        FileOutputStream(temporary).use { stream ->
            stream.write(gson.toJson(state).toByteArray(Charsets.UTF_8)); stream.fd.sync()
        }
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    internal fun clear() { check(!directory.exists() || directory.deleteRecursively()) }
}

/** A Google calendar picked by an earlier build reads as off: the app no longer writes there. */
internal fun StoredCalendarSync.toModel(): CalendarSyncState {
    check(target == null || target == TARGET_APP || target == TARGET_PHONE) { "Unknown calendar target $target" }
    return CalendarSyncState(
        enabled = enabled && target != TARGET_PHONE,
        problem = problem?.let(CalendarSyncProblem::valueOf)
    )
}

internal val StoredCalendarSync.syncedEvents: List<SyncedEvent> get() = events.map { it.toModel() }

/** The calendar [event] is in. */
internal fun StoredCalendarSync.calendarOf(event: StoredEvent): Long? = event.calendarId ?: calendarId

internal fun StoredEvent.toModel() = SyncedEvent(
    eventId = eventId,
    event = CalendarEvent(
        key = checkNotNull(key),
        title = checkNotNull(title),
        start = Instant.ofEpochMilli(start),
        end = Instant.ofEpochMilli(end),
        location = location,
        description = description
    )
)

internal fun SyncedEvent.toStored(calendarId: Long?) = StoredEvent(
    key = event.key,
    eventId = eventId,
    calendarId = calendarId,
    start = event.start.toEpochMilli(),
    end = event.end.toEpochMilli(),
    title = event.title,
    location = event.location,
    description = event.description
)
