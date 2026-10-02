package dev.alllexey.itmowidgets.feature.schedule.domain.calendar

import java.time.Duration
import java.time.Instant

/** An event the app put into the phone's calendar, with the content it was given. */
data class SyncedEvent(val eventId: Long, val event: CalendarEvent)

/** What one sync does to the phone's calendar; [kept] events stay as they are. */
data class CalendarSyncPlan(
    val inserts: List<CalendarEvent>,
    val updates: List<SyncedEvent>,
    val deletes: List<SyncedEvent>,
    val kept: List<SyncedEvent>
)

/**
 * Compares the schedule of the sync window with the app's own events. Only occurrences that have not ended are
 * touched: a past event stays as it was, also when the schedule no longer has it. Nothing outside the window is
 * deleted, so a lesson moved beyond the window is updated by the next syncs instead of disappearing.
 */
object CalendarSyncPlanner {

    /** Events that ended longer ago are forgotten: they stay in the calendar but are no longer the app's. */
    val RETENTION: Duration = Duration.ofDays(180)

    /**
     * [desired] is the schedule of [window], [synced] the app's events from earlier syncs. A key repeated in [desired]
     * counts once.
     */
    fun plan(
        desired: List<CalendarEvent>,
        synced: List<SyncedEvent>,
        window: OpenEndRange<Instant>,
        now: Instant
    ): CalendarSyncPlan {
        val wanted = desired.distinctBy(CalendarEvent::key).associateBy(CalendarEvent::key)
        val existing = synced.associateBy { it.event.key }
        val inserts = wanted.values.filter { it.key !in existing && it.end > now }
        val updates = mutableListOf<SyncedEvent>()
        val deletes = mutableListOf<SyncedEvent>()
        val kept = mutableListOf<SyncedEvent>()
        val forgetBefore = now.minus(RETENTION)
        existing.values.forEach { current ->
            val next = wanted[current.event.key]
            when {
                next == null && current.event.start in window && current.event.end > now -> deletes += current
                next != null && next != current.event && next.end > now && current.event.end > now ->
                    updates += current.copy(event = next)
                current.event.end < forgetBefore -> Unit
                else -> kept += current
            }
        }
        return CalendarSyncPlan(inserts, updates, deletes, kept)
    }
}
