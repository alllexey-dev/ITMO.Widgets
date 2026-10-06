package dev.alllexey.itmowidgets.feature.schedule.data.calendar

import dev.alllexey.itmowidgets.core.schedule.CalendarSyncProblem
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncState
import dev.alllexey.itmowidgets.testkit.fakeFileSystemOf
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

/** `files/calendar_sync/state.json` on okio's fake file system; the 2.2 golden is `CalendarSync22GoldenTest` in `:app`. */
class CalendarSyncFileStoreTest {

    private val directory = "/files/calendar_sync".toPath()
    private val file = directory / "state.json"
    private val fileSystem: FakeFileSystem = fakeFileSystemOf()
    private val store get() = CalendarSyncFileStore(directory, fileSystem)

    @AfterTest
    fun tearDown() {
        fileSystem.checkNoOpenFiles()
    }

    @Test
    fun theWrittenStateReadsBackAndNoTemporaryFileIsLeft() {
        val state = StoredCalendarSync(
            enabled = true,
            target = TARGET_APP,
            calendarId = 7,
            events = listOf(StoredEvent("lesson-1", 42, 7, 1_000, 2_000, "Физика", "1506", "Лекция")),
            cleanups = listOf(StoredCleanup(11, 5_000))
        )

        store.write(state)

        assertEquals(state, store.read())
        assertTrue(fileSystem.read(file) { readUtf8() }.startsWith("{\"format\":1,"))
        assertFalse(fileSystem.exists(directory / "state.json.new"))
        assertEquals(CalendarSyncState(enabled = true), state.toModel())
        assertEquals("lesson-1", state.syncedEvents.single().event.key)
        assertEquals(7L, state.calendarOf(state.events.single()))
        val withoutCalendar = state.events.single().copy(calendarId = null)
        assertEquals(7L, state.copy(events = listOf(withoutCalendar)).calendarOf(withoutCalendar))
        assertEquals(state.events, state.syncedEvents.map { it.toStored(7) })
    }

    @Test
    fun aProblemSurvivesTheWriteAndAGoogleCalendarOfAnEarlierBuildReadsAsOff() {
        val state = StoredCalendarSync(target = TARGET_APP, calendarId = 3, problem = "NO_PERMISSION")

        store.write(state)

        assertEquals(CalendarSyncState(problem = CalendarSyncProblem.NO_PERMISSION), store.read()!!.toModel())
        assertEquals(
            CalendarSyncState(),
            StoredCalendarSync(enabled = true, target = TARGET_PHONE, calendarId = 11, calendarName = "Учёба").toModel()
        )
    }

    @Test
    fun aMissingFileReadsAsNullAndClearRemovesTheDirectory() {
        assertNull(store.read())
        store.write(StoredCalendarSync())

        store.clear()

        assertFalse(fileSystem.exists(directory))
        assertNull(store.read())
    }

    @Test
    fun aCorruptFileOrAnotherFormatThrows() {
        fileSystem.createDirectories(directory)

        fileSystem.write(file) { writeUtf8("{not json") }
        assertFailsWith<Exception> { store.read() }
        fileSystem.write(file) { writeUtf8("""{"format":2}""") }
        assertFailsWith<IllegalStateException> { store.read() }
        fileSystem.write(file) { writeUtf8("""{"format":1,"target":"elsewhere"}""") }
        assertFailsWith<IllegalStateException> { store.read() }
    }
}
