package dev.alllexey.itmowidgets.feature.schedule.data.calendar

import dev.alllexey.itmowidgets.core.schedule.CalendarSyncProblem
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncState
import java.io.File
import okio.Path.Companion.toOkioPath
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
    private val store get() = CalendarSyncFileStore(directory.toOkioPath())

    @Test
    fun `the written state reads back and no temporary file is left`() {
        val state = StoredCalendarSync(
            enabled = true,
            target = TARGET_APP,
            calendarId = 7,
            events = listOf(StoredEvent("lesson-1", 42, 7, 1_000, 2_000, "Физика", "1506", "Лекция")),
            cleanups = listOf(StoredCleanup(11, 5_000))
        )

        store.write(state)

        assertEquals(state, store.read())
        assertFalse(File(directory, "state.json.new").exists())
        assertEquals(
            CalendarSyncState(enabled = true),
            state.toModel()
        )
        assertEquals("lesson-1", state.syncedEvents.single().event.key)
        assertEquals(7L, state.calendarOf(state.events.single()))
        assertEquals(7L, state.copy(events = listOf(state.events.single().copy(calendarId = null))).calendarOf(state.events.single().copy(calendarId = null)))
        assertEquals(state.events, state.syncedEvents.map { it.toStored(7) })
    }

    @Test
    fun `a problem survives the write and a Google calendar of an earlier build reads as off`() {
        val state = StoredCalendarSync(target = TARGET_APP, calendarId = 3, problem = "NO_PERMISSION")

        store.write(state)

        assertEquals(CalendarSyncState(problem = CalendarSyncProblem.NO_PERMISSION), store.read()!!.toModel())
        assertEquals(
            CalendarSyncState(),
            StoredCalendarSync(enabled = true, target = TARGET_PHONE, calendarId = 11, calendarName = "Учёба").toModel()
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
