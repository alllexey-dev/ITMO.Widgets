package dev.alllexey.itmowidgets.upgrade.stores

import dev.alllexey.itmowidgets.core.schedule.CalendarSyncProblem
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncState
import dev.alllexey.itmowidgets.feature.schedule.data.calendar.CalendarSyncFileStore
import dev.alllexey.itmowidgets.feature.schedule.data.calendar.StoredCalendarSync
import dev.alllexey.itmowidgets.feature.schedule.data.calendar.StoredCleanup
import dev.alllexey.itmowidgets.feature.schedule.data.calendar.StoredEvent
import dev.alllexey.itmowidgets.feature.schedule.data.calendar.TARGET_APP
import dev.alllexey.itmowidgets.feature.schedule.data.calendar.toModel
import dev.alllexey.itmowidgets.upgrade.Captured22.AT_MS
import dev.alllexey.itmowidgets.upgrade.Captured22.DAY_MS
import dev.alllexey.itmowidgets.upgrade.Captured22.SUBJECT
import dev.alllexey.itmowidgets.upgrade.Upgrade22Fixture
import java.io.File
import java.time.Instant
import org.junit.Assert.assertEquals

/** `files/calendar_sync/state.json` (format 1): the switch, the app's calendar, its event and a pending sweep. */
object CalendarSyncFileStoreUpgrade {

    fun check(fixture: Upgrade22Fixture) {
        val expected = StoredCalendarSync(
            enabled = true,
            target = TARGET_APP,
            calendarId = 7,
            problem = "NO_PERMISSION",
            events = listOf(
                StoredEvent(
                    key = "lesson-5001-2026-10-05",
                    eventId = 77,
                    calendarId = 7,
                    start = Instant.parse("2026-10-05T07:00:00Z").toEpochMilli(),
                    end = Instant.parse("2026-10-05T08:30:00Z").toEpochMilli(),
                    title = SUBJECT,
                    location = "Тестовый корпус, 101",
                    description = "Тестовый преподаватель"
                )
            ),
            cleanups = listOf(StoredCleanup(calendarId = 8, until = AT_MS + 7 * DAY_MS))
        )

        val stored = CalendarSyncFileStore(File(fixture.filesDir, "calendar_sync"), fixture.gson).read()
        assertEquals(expected, stored)
        assertEquals(CalendarSyncState(enabled = true, problem = CalendarSyncProblem.NO_PERMISSION), stored?.toModel())
    }
}
