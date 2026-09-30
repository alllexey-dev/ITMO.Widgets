package dev.alllexey.itmowidgets.feature.schedule.data.changes

import com.google.gson.Gson
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ScheduleChangesFileStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    private val directory get() = File(temporary.root, "schedule_changes")
    private val store get() = ScheduleChangesFileStore(directory, Gson())

    @Test
    fun `the written state reads back and no temporary file is left`() {
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
        assertFalse(File(directory, "state.json.tmp").exists())
    }

    @Test
    fun `a missing file reads as null and clear removes the directory`() {
        assertNull(store.read())
        store.write(StoredScheduleChanges())

        store.clear()

        assertFalse(directory.exists())
        assertNull(store.read())
    }

    @Test
    fun `another format an unknown kind or a broken file fail to read`() {
        directory.mkdirs()
        val file = File(directory, "state.json")
        listOf(
            """{"format":2,"emptyHeld":false,"changes":[]}""",
            """{"format":1,"emptyHeld":false,"changes":[{"id":"1","detectedAt":1,"kind":"MOVED","fields":[],"subjectName":"","typeId":1,"read":false,"notified":false,"after":{"pairId":1,"date":"2026-09-08","start":"10:00","end":"11:30","subjectId":1,"subjectName":"","typeId":1,"flowId":1,"formatId":1}}]}""",
            """{"format":1,"emptyHeld":false,"snapshot":{"start":"2026-09-07","end":"not a date","lessons":[]},"changes":[]}""",
            "{",
        ).forEach { content ->
            file.writeText(content)
            assertThrows(content, Exception::class.java) { store.read() }
        }
    }
}
