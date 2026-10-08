package dev.alllexey.itmowidgets.feature.schedule.data.calendar

import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.CalendarEvent
import kotlin.time.Instant

/**
 * EventKit's calendar database in memory, by string identifiers: [calendars] by title, [events] with their notes as
 * [SystemEventStore] writes them. Without [access] every call but [hasFullAccess] throws, as EventKit refuses it.
 */
class FakeEventStore : EventStore {
    var access = true
    val calendars = linkedMapOf<String, String>()
    val events = linkedMapOf<String, FakeItem>()
    var calendarsCreated = 0
    var saves = 0
    var updates = 0
    var removes = 0
    private var next = 1

    data class FakeItem(val calendar: String, val event: CalendarEvent, val notes: String?)

    fun eventsIn(calendar: String): List<CalendarEvent> =
        events.values.filter { it.calendar == calendar }.map { it.event }

    /** A calendar the user made in the Calendar app. */
    fun addCalendar(title: String): String = identifier("calendar").also { calendars[it] = title }

    /** An event with the app's tag that the id file does not know, as after a lost write. */
    fun addTagged(calendar: String, event: CalendarEvent): String =
        identifier("event").also { events[it] = FakeItem(calendar, event, event.taggedDescription) }

    /** EventKit gives event [identifier] a new identifier, as a sync with iCloud may. */
    fun renumber(identifier: String): String {
        val item = checkNotNull(events.remove(identifier))
        return identifier("event").also { events[it] = item }
    }

    override fun hasFullAccess() = access

    override fun calendarExists(identifier: String): Boolean = checked { identifier in calendars }

    override fun calendarsTitled(title: String): List<String> = checked {
        calendars.filterValues { it == title }.keys.toList()
    }

    override fun createCalendar(title: String): String = checked {
        calendarsCreated++
        addCalendar(title)
    }

    override fun removeCalendar(identifier: String) = checked {
        calendars.remove(identifier)
        events.entries.removeAll { it.value.calendar == identifier }
        Unit
    }

    override fun saveEvent(calendarIdentifier: String, event: CalendarEvent): String = checked {
        check(calendarIdentifier in calendars) { "No calendar $calendarIdentifier" }
        saves++
        identifier("event").also { events[it] = FakeItem(calendarIdentifier, event, event.taggedDescription) }
    }

    override fun eventExists(identifier: String): Boolean = checked { identifier in events }

    override fun updateEvent(identifier: String, event: CalendarEvent): Boolean = checked {
        val item = events[identifier] ?: return@checked false
        updates++
        events[identifier] = item.copy(event = event, notes = event.taggedDescription)
        true
    }

    override fun removeEvent(identifier: String) = checked {
        if (events.remove(identifier) != null) removes++
        Unit
    }

    override fun events(calendarIdentifier: String, from: Instant, to: Instant): List<StoredCalendarItem> = checked {
        events.filter { (_, item) ->
            item.calendar == calendarIdentifier && item.event.start >= from && item.event.start < to
        }.map { (identifier, item) -> StoredCalendarItem(identifier, item.notes, item.event.end) }
    }

    private fun identifier(kind: String) = "$kind-${next++}"

    private fun <T> checked(block: () -> T): T {
        check(access) { "No full access to the calendar" }
        return block()
    }
}
