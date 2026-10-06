package dev.alllexey.itmowidgets.feature.schedule.domain.calendar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class CalendarSyncPlannerTest {

    @Test
    fun newLessonsAreInserted() {
        val plan = CalendarSyncPlanner.plan(listOf(event("a"), event("b", day = 1)), emptyList(), WINDOW, NOW)

        assertEquals(listOf("a", "b"), plan.inserts.map { it.key })
        assertTrue(plan.updates.isEmpty() && plan.deletes.isEmpty() && plan.kept.isEmpty())
    }

    @Test
    fun theSameScheduleAgainChangesNothing() {
        val events = listOf(event("a"), event("b", day = 1))
        val synced = events.mapIndexed { index, event -> SyncedEvent(index + 1L, event) }

        val plan = CalendarSyncPlanner.plan(events, synced, WINDOW, NOW)

        assertTrue(plan.inserts.isEmpty() && plan.updates.isEmpty() && plan.deletes.isEmpty())
        assertEquals(synced, plan.kept)
    }

    @Test
    fun aKeyRepeatedInTheScheduleIsInsertedOnce() {
        val plan = CalendarSyncPlanner.plan(listOf(event("a"), event("a", day = 2)), emptyList(), WINDOW, NOW)

        assertEquals(listOf(event("a")), plan.inserts)
    }

    @Test
    fun aChangedTitleTimePlaceOrDescriptionUpdatesTheSameEvent() {
        val synced = listOf(
            SyncedEvent(1, event("a")),
            SyncedEvent(2, event("b")),
            SyncedEvent(3, event("c")),
            SyncedEvent(4, event("d"))
        )
        val desired = listOf(
            event("a").copy(title = "Химия"),
            event("b", day = 2),
            event("c").copy(location = "2304"),
            event("d").copy(description = "Практика")
        )

        val plan = CalendarSyncPlanner.plan(desired, synced, WINDOW, NOW)

        assertEquals(listOf(1L, 2L, 3L, 4L), plan.updates.map { it.eventId })
        assertEquals(desired, plan.updates.map { it.event })
        assertTrue(plan.inserts.isEmpty() && plan.deletes.isEmpty())
    }

    @Test
    fun aVanishedOrCancelledFutureLessonInTheWindowIsDeleted() {
        val synced = listOf(SyncedEvent(1, event("a")), SyncedEvent(2, event("b", day = 3)))

        val plan = CalendarSyncPlanner.plan(listOf(event("a")), synced, WINDOW, NOW)

        assertEquals(listOf(SyncedEvent(2, event("b", day = 3))), plan.deletes)
        assertEquals(listOf(SyncedEvent(1, event("a"))), plan.kept)
    }

    @Test
    fun pastEventsStayAsTheyWere() {
        val past = event("a", day = -1)
        val endedToday = event("b", hour = 6)
        val synced = listOf(SyncedEvent(1, past), SyncedEvent(2, endedToday))

        val plan = CalendarSyncPlanner.plan(listOf(endedToday.copy(title = "Химия")), synced, WINDOW, NOW)

        assertTrue(plan.inserts.isEmpty() && plan.updates.isEmpty() && plan.deletes.isEmpty())
        assertEquals(synced, plan.kept)
    }

    @Test
    fun aLessonThatEndedBeforeTheFirstSyncIsNotInsertedARunningOneIs() {
        val plan = CalendarSyncPlanner.plan(listOf(event("ended", hour = 6), event("running", hour = 8)), emptyList(), WINDOW, NOW)

        assertEquals(listOf("running"), plan.inserts.map { it.key })
    }

    @Test
    fun eventsOutsideTheWindowAreNotDeleted() {
        val later = SyncedEvent(1, event("a", day = 30))

        val plan = CalendarSyncPlanner.plan(emptyList(), listOf(later), WINDOW, NOW)

        assertEquals(listOf(later), plan.kept)
        assertTrue(plan.deletes.isEmpty())
    }

    @Test
    fun eventsThatEndedOverHalfAYearAgoAreForgotten() {
        val old = SyncedEvent(1, event("old", day = -181))
        val recent = SyncedEvent(2, event("recent", day = -179))

        val plan = CalendarSyncPlanner.plan(emptyList(), listOf(old, recent), WINDOW, NOW)

        assertEquals(listOf(recent), plan.kept)
        assertTrue(plan.deletes.isEmpty())
    }

    private fun event(key: String, day: Long = 0, hour: Long = 10) = CalendarEvent(
        key = key,
        title = "Физика",
        start = TODAY.plus(day.days).plus(hour.hours),
        end = TODAY.plus(day.days).plus(hour.hours).plus(90.minutes),
        location = "1506",
        description = "Лекция"
    )

    private companion object {
        /** Midnight of today in Moscow. */
        val TODAY: Instant = Instant.parse("2026-09-06T21:00:00Z")
        /** 09:00 in Moscow. */
        val NOW: Instant = TODAY.plus(9.hours)
        val WINDOW = TODAY..<TODAY.plus(29.days)
    }
}
