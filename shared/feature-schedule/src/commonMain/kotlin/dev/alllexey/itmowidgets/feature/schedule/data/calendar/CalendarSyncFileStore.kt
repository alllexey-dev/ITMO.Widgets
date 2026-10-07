package dev.alllexey.itmowidgets.feature.schedule.data.calendar

import dev.alllexey.itmowidgets.core.schedule.CalendarSyncProblem
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncState
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.storage.AtomicTextFile
import dev.alllexey.itmowidgets.feature.schedule.data.local.ScheduleFileSystem
import dev.alllexey.itmowidgets.feature.schedule.data.local.ScheduleStoreJson
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.CalendarEvent
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.SyncedEvent
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import okio.FileSystem
import okio.Path

/** 1: the switch, the calendar in use and the app's events in it with the content they were given. */
private const val FORMAT = 1

const val TARGET_APP = "app"
const val TARGET_PHONE = "phone"

/** Everything calendar synchronization keeps; calendar and event ids belong to this device only. */
@Serializable
data class StoredCalendarSync(
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
@Serializable
data class StoredCleanup(val calendarId: Long, val until: Long)

@Serializable
data class StoredEvent(
    val key: String,
    val eventId: Long,
    /** The calendar the event is in; null in files written before it was stored: the state's calendar. */
    val calendarId: Long? = null,
    val start: Long,
    val end: Long,
    val title: String,
    val location: String? = null,
    val description: String? = null
)

/**
 * The synchronization state in `filesDir` ([AppDirectories.files]), kept out of backups. Caller owns IO dispatch and
 * serialization.
 */
class CalendarSyncFileStore internal constructor(private val directory: Path, private val fileSystem: FileSystem) {
    constructor(directories: AppDirectories) : this(directories.files / "calendar_sync", ScheduleFileSystem)

    private val file = AtomicTextFile(directory / "state.json", fileSystem)

    /** `null` without a file; throws on a corrupt file or one of another format. */
    fun read(): StoredCalendarSync? {
        val text = file.read() ?: return null
        val state = ScheduleStoreJson.decodeFromString<StoredCalendarSync>(text)
        check(state.format == FORMAT) { "Unknown calendar sync format ${state.format}" }
        state.toModel()
        state.events.forEach { it.toModel() }
        return state
    }

    fun write(state: StoredCalendarSync) = file.write(ScheduleStoreJson.encodeToString(state))

    fun clear() = fileSystem.deleteRecursively(directory)
}

/** A Google calendar picked by an earlier build reads as off: the app no longer writes there. */
fun StoredCalendarSync.toModel(): CalendarSyncState {
    check(target == null || target == TARGET_APP || target == TARGET_PHONE) { "Unknown calendar target $target" }
    return CalendarSyncState(
        enabled = enabled && target != TARGET_PHONE,
        problem = problem?.let(CalendarSyncProblem::valueOf)
    )
}

val StoredCalendarSync.syncedEvents: List<SyncedEvent> get() = events.map { it.toModel() }

/** The calendar [event] is in. */
fun StoredCalendarSync.calendarOf(event: StoredEvent): Long? = event.calendarId ?: calendarId

fun StoredEvent.toModel() = SyncedEvent(
    eventId = eventId,
    event = CalendarEvent(
        key = key,
        title = title,
        start = Instant.fromEpochMilliseconds(start),
        end = Instant.fromEpochMilliseconds(end),
        location = location,
        description = description
    )
)

fun SyncedEvent.toStored(calendarId: Long?) = StoredEvent(
    key = event.key,
    eventId = eventId,
    calendarId = calendarId,
    start = event.start.toEpochMilliseconds(),
    end = event.end.toEpochMilliseconds(),
    title = event.title,
    location = event.location,
    description = event.description
)
