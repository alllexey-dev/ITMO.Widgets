package dev.alllexey.itmowidgets.feature.schedule.data.calendar

import com.google.gson.Gson
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncProblem
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncState
import dev.alllexey.itmowidgets.core.schedule.CalendarTarget
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CalendarSyncFileStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    private val directory get() = File(temporary.root, "calendar_sync")
    private val store get() = CalendarSyncFileStore(directory, Gson())

    @Test
    fun `the written state reads back and no temporary file is left`() {
        val state = StoredCalendarSync(
            enabled = true,
            target = TARGET_PHONE,
            calendarId = 7,
            calendarName = "Учёба",
            calendarAccount = "student@gmail.com",
            events = listOf(StoredEvent("lesson-1", 42, 1_000, 2_000, "Физика", "1506", "Лекция"))
        )

        store.write(state)

        assertEquals(state, store.read())
        assertFalse(File(directory, "state.json.tmp").exists())
        assertEquals(
            CalendarSyncState(enabled = true, target = CalendarTarget.PhoneCalendar(7), calendarName = "Учёба", calendarAccount = "student@gmail.com"),
            state.toModel()
        )
        assertEquals("lesson-1", state.syncedEvents.single().event.key)
        assertEquals(state.events, state.syncedEvents.map { it.toStored() })
    }

    @Test
    fun `the app calendar has no name and a problem survives the write`() {
        val state = StoredCalendarSync(target = TARGET_APP, calendarId = 3, calendarName = "ITMO.Widgets", problem = "NO_PERMISSION")

        store.write(state)

        assertEquals(
            CalendarSyncState(target = CalendarTarget.AppCalendar, problem = CalendarSyncProblem.NO_PERMISSION),
            store.read()!!.toModel()
        )
    }

    @Test
    fun `a missing file reads as null and clear removes the directory`() {
        assertNull(store.read())
        store.write(StoredCalendarSync())

        store.clear()

        assertFalse(directory.exists())
        assertNull(store.read())
    }

    @Test
    fun `a corrupt file or another format throws`() {
        directory.mkdirs()
        val file = File(directory, "state.json")

        file.writeText("{not json")
        assertThrows(Exception::class.java) { store.read() }
        file.writeText("""{"format":2}""")
        assertThrows(IllegalStateException::class.java) { store.read() }
        file.writeText("""{"format":1,"target":"elsewhere"}""")
        assertThrows(IllegalStateException::class.java) { store.read() }
    }
}
