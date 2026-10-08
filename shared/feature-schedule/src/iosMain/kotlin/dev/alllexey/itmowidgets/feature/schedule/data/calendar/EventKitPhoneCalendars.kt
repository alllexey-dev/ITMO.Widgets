package dev.alllexey.itmowidgets.feature.schedule.data.calendar

import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.CalendarEvent
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.MarkedEvent
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.PhoneCalendars
import kotlin.time.Instant

/**
 * The phone's calendars on iOS (IO-15b): EventKit with full access through [store], the identifiers as numbers
 * through [ids]. The app's own calendar is the one titled [OWN_CALENDAR_TITLE] that it created, in the iCloud source,
 * else the local one; after a reinstall a calendar of that title in those sources counts as the app's, as Android
 * finds its local calendar by its account. The app's events carry the tag line in their notes
 * ([CalendarEvent.taggedDescription]), which the sweeps read; EventKit has no other marker that every source keeps.
 *
 * EventKit may give an event a new identifier (a sync with iCloud, a calendar change). [marked] then finds a tagged
 * event it has no number for while the number of the same occurrence names a gone event, and moves that number to
 * it, so the sync neither deletes the lesson as an orphan nor inserts it twice.
 */
class EventKitPhoneCalendars(private val store: EventStore, private val ids: EventKitIdStore) : PhoneCalendars {

    override fun hasAccess(): Boolean = store.hasFullAccess()

    override fun exists(id: Long): Boolean = ids.identifierOf(id)?.let(store::calendarExists) ?: false

    override fun findOwn(): Long? = saving { ownIdentifier()?.let { ids.idOf(it) } }

    override fun createOwn(): Long = saving {
        val identifier = store.createCalendar(OWN_CALENDAR_TITLE)
        ids.own = identifier
        ids.idOf(identifier)
    }

    override fun deleteOwn(id: Long) = saving {
        val identifier = ids.identifierOf(id) ?: return@saving
        if (identifier != ids.own && identifier !in store.calendarsTitled(OWN_CALENDAR_TITLE)) return@saving
        if (store.calendarExists(identifier)) store.removeCalendar(identifier)
        ids.forgetCalendar(identifier)
    }

    override fun insert(calendarId: Long, event: CalendarEvent): Long = saving {
        val calendar = checkNotNull(ids.identifierOf(calendarId)) { "No calendar $calendarId" }
        ids.idOf(store.saveEvent(calendar, event), calendar, event.key)
    }

    override fun update(eventId: Long, event: CalendarEvent): Boolean =
        ids.identifierOf(eventId)?.let { store.updateEvent(it, event) } ?: false

    override fun delete(eventId: Long) = saving {
        ids.identifierOf(eventId)?.let(store::removeEvent)
        ids.forget(eventId)
    }

    override fun marked(calendarId: Long, from: Instant, to: Instant): List<MarkedEvent> = saving {
        val calendar = ids.identifierOf(calendarId) ?: return@saving emptyList()
        store.events(calendar, from, to).mapNotNull { item ->
            val key = CalendarEvent.keyOfDescription(item.notes) ?: return@mapNotNull null
            val id = ids.knownIdOf(item.identifier)
                ?: ids.adopt(item.identifier, calendar, key) { !store.eventExists(it) }
                ?: ids.idOf(item.identifier, calendar, key)
            MarkedEvent(id, key, item.end)
        }
    }

    /** The calendar the app created, or one of its title from an earlier install. */
    private fun ownIdentifier(): String? =
        ids.own?.takeIf(store::calendarExists)
            ?: store.calendarsTitled(OWN_CALENDAR_TITLE).firstOrNull()?.also { ids.own = it }

    /** [block], then the numbers it handed out or dropped are written, also when it failed halfway. */
    private fun <T> saving(block: () -> T): T = try {
        block()
    } finally {
        ids.save()
    }

    companion object {
        /** The app's name, as Android's `CALENDAR_DISPLAY_NAME`; calendars have no other name on iOS. */
        const val OWN_CALENDAR_TITLE = "ITMO.Widgets"
    }
}
