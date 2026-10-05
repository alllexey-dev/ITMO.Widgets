package dev.alllexey.itmowidgets.core.work

import dev.alllexey.itmowidgets.feature.recordbook.work.MARKS_SPEC
import dev.alllexey.itmowidgets.feature.recordbook.work.MarksWorker
import dev.alllexey.itmowidgets.feature.schedule.work.CALENDAR_SYNC_SPEC
import dev.alllexey.itmowidgets.feature.schedule.work.CalendarSyncWorker
import dev.alllexey.itmowidgets.feature.schedule.work.SCHEDULE_CHANGES_SPEC
import dev.alllexey.itmowidgets.feature.schedule.work.ScheduleChangesWorker
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import org.junit.Assert.assertEquals
import org.junit.Test

/** The work names and tags are stable identifiers (`StableIdentifiersTest`); periods and backoff are behaviour. */
class PeriodicCheckSpecTest {

    @Test
    fun `the schedule change check runs every two hours`() {
        assertEquals(
            PeriodicCheckSpec(
                worker = ScheduleChangesWorker::class,
                periodicWork = "schedule-changes-check",
                oneOffWork = "schedule-changes-now",
                tag = "schedule-changes",
                period = 2.hours,
                backoff = 15.minutes
            ),
            SCHEDULE_CHANGES_SPEC
        )
    }

    @Test
    fun `the calendar sync runs every two hours`() {
        assertEquals(
            PeriodicCheckSpec(
                worker = CalendarSyncWorker::class,
                periodicWork = "calendar-sync",
                oneOffWork = "calendar-sync-now",
                tag = "calendar-sync",
                period = 2.hours,
                backoff = 15.minutes
            ),
            CALENDAR_SYNC_SPEC
        )
    }

    @Test
    fun `the mark check runs every three hours`() {
        assertEquals(
            PeriodicCheckSpec(
                worker = MarksWorker::class,
                periodicWork = "marks-check",
                oneOffWork = "marks-check-now",
                tag = "marks",
                period = 3.hours,
                backoff = 15.minutes
            ),
            MARKS_SPEC
        )
    }
}
