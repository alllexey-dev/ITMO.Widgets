package dev.alllexey.itmowidgets.feature.schedule.data.changes

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

/** `files/schedule_changes/state.json` on okio's fake file system; the 2.2 golden is `ScheduleChanges22GoldenTest` in `:app`. */
class ScheduleChangesFileStoreTest {

    private val directory = "/files/schedule_changes".toPath()
    private val file = directory / "state.json"
    private val fileSystem: FakeFileSystem = fakeFileSystemOf()
    private val store get() = ScheduleChangesFileStore(directory, fileSystem)

    @AfterTest
    fun tearDown() {
        fileSystem.checkNoOpenFiles()
    }

    @Test
    fun theWrittenStateReadsBackAndNoTemporaryFileIsLeft() {
        val lesson = StoredLesson(
            pairId = 1, date = "2026-09-08", start = "10:00", end = "11:30", subjectId = 10, subjectName = "Физика",
            typeId = 1, flowId = 100, flowName = "ФИЗ ПИИКТ 3.2", teacherIsu = 300001, teacherName = "Тестовый преподаватель",
            room = "1506", building = null, formatId = 1, format = "Очный"
        )
        val state = StoredScheduleChanges(
            snapshot = StoredSnapshot("2026-09-07", "2026-09-14", listOf(lesson)),
            emptyHeld = true,
            changes = listOf(StoredChange(
                id = "1-0", detectedAt = 1L, kind = "UPDATED", fields = listOf("TIME", "PLACE"), subjectName = "Физика",
                typeId = 1, flowName = null, before = lesson, after = lesson.copy(date = "2026-09-09", room = null),
                read = false, notified = true
            ))
        )

        store.write(state)

        assertEquals(state, store.read())
        assertTrue(fileSystem.read(file) { readUtf8() }.startsWith("{\"format\":1,"))
        assertFalse(fileSystem.exists(directory / "state.json.new"))
    }

    @Test
    fun aMissingFileReadsAsNullAndClearRemovesTheDirectory() {
        assertNull(store.read())
        store.write(StoredScheduleChanges())

        store.clear()

        assertFalse(fileSystem.exists(directory))
        assertNull(store.read())
    }

    @Test
    fun anotherFormatAnUnknownKindOrABrokenFileFailToRead() {
        fileSystem.createDirectories(directory)
        listOf(
            """{"format":2,"emptyHeld":false,"changes":[]}""",
            """{"format":1,"emptyHeld":false,"changes":[{"id":"1","detectedAt":1,"kind":"MOVED","fields":[],"subjectName":"","typeId":1,"read":false,"notified":false,"after":{"pairId":1,"date":"2026-09-08","start":"10:00","end":"11:30","subjectId":1,"subjectName":"","typeId":1,"flowId":1,"formatId":1}}]}""",
            """{"format":1,"emptyHeld":false,"snapshot":{"start":"2026-09-07","end":"not a date","lessons":[]},"changes":[]}""",
            "{",
        ).forEach { content ->
            fileSystem.write(file) { writeUtf8(content) }
            assertFailsWith<Exception>(content) { store.read() }
        }
    }
}
