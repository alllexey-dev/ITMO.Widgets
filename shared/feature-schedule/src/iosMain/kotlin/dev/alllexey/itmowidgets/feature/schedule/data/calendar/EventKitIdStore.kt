package dev.alllexey.itmowidgets.feature.schedule.data.calendar

import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.storage.AtomicTextFile
import dev.alllexey.itmowidgets.feature.schedule.data.local.ScheduleFileSystem
import dev.alllexey.itmowidgets.feature.schedule.data.local.ScheduleStoreJson
import kotlinx.serialization.Serializable
import okio.Path

/** 1: the app's own calendar and every number handed out with its EventKit identifier. */
private const val VERSION = 1

@Serializable
internal data class StoredEventKitIds(
    val version: Int = VERSION,
    /** The next number to hand out; numbers are never reused, so a stale one never names another event. */
    val next: Long = 1,
    /** The identifier of the calendar the app created. */
    val own: String? = null,
    val entries: List<StoredEventKitId> = emptyList()
)

/** A calendar ([calendar] null) or an event of [calendar] with its occurrence [key], by its EventKit identifier. */
@Serializable
internal data class StoredEventKitId(
    val id: Long,
    val identifier: String,
    val calendar: String? = null,
    val key: String? = null
)

/**
 * The numbers `PhoneCalendars` and the sync file know calendars and events by, for EventKit's string identifiers, in
 * `no-backup/calendar_eventkit/ids.json`: identifiers belong to this device, so a restored device starts without
 * them and sees the sync's calendar as missing. A damaged file, or one of a later version, starts empty. Not
 * thread-safe: `CalendarSyncRepositoryImpl` makes every calendar call behind one mutex. Changes are kept in memory
 * until [save].
 */
class EventKitIdStore internal constructor(private val file: AtomicTextFile) {

    constructor(directories: AppDirectories) : this(fileIn(directories.noBackup))

    private var loaded: StoredEventKitIds? = null
    private var dirty = false

    private val state: StoredEventKitIds
        get() = loaded ?: read().also { loaded = it }

    var own: String?
        get() = state.own
        set(value) {
            if (value != state.own) change(state.copy(own = value))
        }

    /** The number of [identifier], handed out now when it has none; an event has its [calendar] and [key]. */
    fun idOf(identifier: String, calendar: String? = null, key: String? = null): Long {
        state.entries.firstOrNull { it.identifier == identifier }?.let { return it.id }
        val id = state.next
        change(state.copy(next = id + 1, entries = state.entries + StoredEventKitId(id, identifier, calendar, key)))
        return id
    }

    /**
     * Moves the number of the event of [calendar] with [key] whose identifier [isGone] to [identifier], the new
     * identifier EventKit gave that event; null when no such number exists.
     */
    fun adopt(identifier: String, calendar: String, key: String, isGone: (String) -> Boolean): Long? {
        val entry = state.entries.firstOrNull { it.calendar == calendar && it.key == key && isGone(it.identifier) }
            ?: return null
        change(state.copy(entries = state.entries.map { if (it == entry) it.copy(identifier = identifier) else it }))
        return entry.id
    }

    fun identifierOf(id: Long): String? = state.entries.firstOrNull { it.id == id }?.identifier

    /** The number [identifier] already has; null hands out none. */
    fun knownIdOf(identifier: String): Long? = state.entries.firstOrNull { it.identifier == identifier }?.id

    fun forget(id: Long) {
        if (state.entries.any { it.id == id }) change(state.copy(entries = state.entries.filterNot { it.id == id }))
    }

    /** Forgets calendar [identifier] and its events, as deleting a calendar deletes them. */
    fun forgetCalendar(identifier: String) {
        val entries = state.entries.filterNot { it.identifier == identifier || it.calendar == identifier }
        change(state.copy(own = state.own.takeIf { it != identifier }, entries = entries))
    }

    /** Writes the changes since the last save. */
    fun save() {
        if (!dirty) return
        file.write(ScheduleStoreJson.encodeToString(state))
        dirty = false
    }

    private fun change(next: StoredEventKitIds) {
        loaded = next
        dirty = true
    }

    private fun read(): StoredEventKitIds = try {
        file.read()?.let { ScheduleStoreJson.decodeFromString<StoredEventKitIds>(it) }
            ?.takeIf { it.version == VERSION } ?: StoredEventKitIds()
    } catch (_: Exception) {
        StoredEventKitIds()
    }

    internal companion object {
        fun fileIn(noBackup: Path) = AtomicTextFile(noBackup / "calendar_eventkit" / "ids.json", ScheduleFileSystem)
    }
}
