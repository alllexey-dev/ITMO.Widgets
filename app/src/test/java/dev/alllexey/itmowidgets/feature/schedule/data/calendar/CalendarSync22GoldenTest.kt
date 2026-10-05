package dev.alllexey.itmowidgets.feature.schedule.data.calendar

import dev.alllexey.itmowidgets.core.schedule.CalendarSyncProblem
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncState
import dev.alllexey.itmowidgets.feature.schedule.data.assertSameJson
import dev.alllexey.itmowidgets.feature.schedule.data.copyStored22
import dev.alllexey.itmowidgets.feature.schedule.data.stored22
import java.io.File
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The `calendar_sync/state.json` files 2.2 and earlier builds wrote read into the same state and are rewritten unchanged. */
class CalendarSync22GoldenTest {
    @get:Rule val temporary = TemporaryFolder()

    private val directory get() = File(temporary.root, "calendar_sync")
    private val store get() = CalendarSyncFileStore(directory.toOkioPath())

    @Test
    fun `the 2_2 state reads with its event and sweep and is rewritten unchanged`() {
        val file = copyStored22("calendar_sync/state.json", directory)

        val state = checkNotNull(store.read())

        assertEquals(
            StoredCalendarSync(
                enabled = true,
                target = TARGET_APP,
                calendarId = 7,
                problem = "NO_PERMISSION",
                events = listOf(
                    StoredEvent(
                        key = "lesson-5001-2026-10-05",
                        eventId = 77,
                        calendarId = 7,
                        start = 1_791_183_600_000,
                        end = 1_791_189_000_000,
                        title = "Тестовая дисциплина",
                        location = "Тестовый корпус, 101",
                        description = "Тестовый преподаватель"
                    )
                ),
                cleanups = listOf(StoredCleanup(calendarId = 8, until = 1_791_709_200_000))
            ),
            state
        )
        assertEquals(CalendarSyncState(enabled = true, problem = CalendarSyncProblem.NO_PERMISSION), state.toModel())
        store.write(state)
        assertSameJson(stored22("calendar_sync/state.json").decodeToString(), file.readText())
        assertEquals(state, store.read())
    }

    @Test
    fun `a file without cleanups reads them as null and is rewritten without them`() {
        val file = copyStored22("calendar_sync/state-without-cleanups.json", directory, "state.json")

        val state = checkNotNull(store.read())

        assertNull(state.cleanups)
        assertEquals(TARGET_PHONE, state.target)
        assertEquals("Учёба", state.calendarName)
        assertEquals(StoredEvent("lesson-1", 501, null, 1_791_180_000_000, 1_791_185_400_000, "Тестовый предмет", "1506", null), state.events.single())
        assertEquals(12L, state.calendarOf(state.events.single()))
        assertEquals(CalendarSyncState(), state.toModel())
        store.write(state)
        assertSameJson(stored22("calendar_sync/state-without-cleanups.json").decodeToString(), file.readText())
        assertEquals(state, store.read())
    }
}
