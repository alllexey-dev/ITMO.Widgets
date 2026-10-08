@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package dev.alllexey.itmowidgets.feature.schedule.data.calendar

import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.CalendarEvent
import kotlin.time.Instant
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.EventKit.EKAuthorizationStatusFullAccess
import platform.EventKit.EKCalendar
import platform.EventKit.EKEntityType
import platform.EventKit.EKEvent
import platform.EventKit.EKEventAvailabilityBusy
import platform.EventKit.EKEventStore
import platform.EventKit.EKSource
import platform.EventKit.EKSourceType
import platform.EventKit.EKSpan
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.timeZoneWithName
import platform.UIKit.UIColor

/**
 * [EventStore] over EventKit's `EKEventStore` (`platform.EventKit`, an Objective-C API that Kotlin/Native calls
 * directly). The store is created on the first call with full access, so a graph built before the user answers the
 * system prompt still writes after it. The access itself is asked by the app's Swift side
 * (`requestFullAccessToEvents`), which shows the system prompt with `NSCalendarsFullAccessUsageDescription`.
 *
 * Events are busy, have no alarms and carry [zoneId] (`AcademicTimeProvider`'s zone) as their time zone; the app's
 * calendar has Android's `calendar_app` colour.
 */
class SystemEventStore(private val zoneId: String) : EventStore {

    private var created: EKEventStore? = null

    private val store: EKEventStore
        get() {
            check(hasFullAccess()) { "No full access to the calendar" }
            return created ?: EKEventStore().also { created = it }
        }

    override fun hasFullAccess(): Boolean =
        EKEventStore.authorizationStatusForEntityType(EKEntityType.EKEntityTypeEvent) == EKAuthorizationStatusFullAccess

    override fun calendarExists(identifier: String): Boolean = store.calendarWithIdentifier(identifier) != null

    override fun calendarsTitled(title: String): List<String> {
        val sources = writableSources().map { it.sourceIdentifier }.toSet()
        return store.calendarsForEntityType(EKEntityType.EKEntityTypeEvent)
            .filterIsInstance<EKCalendar>()
            .filter { it.title == title && it.allowsContentModifications && it.source?.sourceIdentifier in sources }
            .map { it.calendarIdentifier }
    }

    override fun createCalendar(title: String): String {
        val source = checkNotNull(writableSources().firstOrNull()) { "Neither an iCloud nor a local calendar source" }
        val calendar = EKCalendar.calendarForEntityType(EKEntityType.EKEntityTypeEvent, eventStore = store)
        calendar.title = title
        calendar.source = source
        calendar.CGColor = APP_COLOR.CGColor
        attempt("Saving the calendar") { error -> store.saveCalendar(calendar, commit = true, error = error) }
        return calendar.calendarIdentifier
    }

    override fun removeCalendar(identifier: String) {
        val calendar = store.calendarWithIdentifier(identifier) ?: return
        attempt("Removing the calendar") { error -> store.removeCalendar(calendar, commit = true, error = error) }
    }

    override fun saveEvent(calendarIdentifier: String, event: CalendarEvent): String {
        val calendar = checkNotNull(store.calendarWithIdentifier(calendarIdentifier)) { "No calendar" }
        val item = EKEvent.eventWithEventStore(store)
        item.calendar = calendar
        item.fill(event)
        save(item)
        return checkNotNull(item.eventIdentifier) { "The saved event has no identifier" }
    }

    override fun eventExists(identifier: String): Boolean = store.eventWithIdentifier(identifier) != null

    override fun updateEvent(identifier: String, event: CalendarEvent): Boolean {
        val item = store.eventWithIdentifier(identifier) ?: return false
        item.fill(event)
        save(item)
        return true
    }

    override fun removeEvent(identifier: String) {
        val item = store.eventWithIdentifier(identifier) ?: return
        attempt("Removing an event") { error ->
            store.removeEvent(item, span = EKSpan.EKSpanThisEvent, commit = true, error = error)
        }
    }

    override fun events(calendarIdentifier: String, from: Instant, to: Instant): List<StoredCalendarItem> {
        val calendar = store.calendarWithIdentifier(calendarIdentifier) ?: return emptyList()
        val predicate = store.predicateForEventsWithStartDate(from.toNSDate(), endDate = to.toNSDate(), calendars = listOf(calendar))
        // The predicate also matches events that only overlap the range; the sweep counts by start, as on Android.
        return store.eventsMatchingPredicate(predicate)
            .filterIsInstance<EKEvent>()
            .mapNotNull { item ->
                val start = item.startDate?.toInstant() ?: return@mapNotNull null
                val end = item.endDate?.toInstant() ?: start
                val identifier = item.eventIdentifier ?: return@mapNotNull null
                StoredCalendarItem(identifier, item.notes, end).takeIf { start >= from && start < to }
            }
    }

    /** The iCloud source, else the local one; never an Exchange, Google or subscribed source. */
    private fun writableSources(): List<EKSource> {
        val sources = store.sources.filterIsInstance<EKSource>()
        val iCloud = sources.filter { it.sourceType == EKSourceType.EKSourceTypeCalDAV && it.title == ICLOUD_SOURCE }
        val local = sources.filter { it.sourceType == EKSourceType.EKSourceTypeLocal }
        return iCloud + local
    }

    private fun EKEvent.fill(event: CalendarEvent) {
        title = event.title
        startDate = event.start.toNSDate()
        endDate = event.end.toNSDate()
        timeZone = NSTimeZone.timeZoneWithName(zoneId)
        location = event.location
        notes = event.taggedDescription
        availability = EKEventAvailabilityBusy
        alarms = null
    }

    private fun save(item: EKEvent) = attempt("Saving an event") { error ->
        store.saveEvent(item, span = EKSpan.EKSpanThisEvent, commit = true, error = error)
    }

    private inline fun attempt(what: String, call: (CPointer<ObjCObjectVar<NSError?>>) -> Boolean) = memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        check(call(error.ptr)) { "$what failed: ${error.value?.localizedDescription}" }
    }

    private companion object {
        const val ICLOUD_SOURCE = "iCloud"

        /** Android's `calendar_app`, #4984E2. */
        val APP_COLOR: UIColor = UIColor.colorWithRed(0x49 / 255.0, green = 0x84 / 255.0, blue = 0xE2 / 255.0, alpha = 1.0)

        fun Instant.toNSDate(): NSDate = NSDate.dateWithTimeIntervalSince1970(toEpochMilliseconds() / 1000.0)

        fun NSDate.toInstant(): Instant = Instant.fromEpochMilliseconds((timeIntervalSince1970 * 1000).toLong())
    }
}
