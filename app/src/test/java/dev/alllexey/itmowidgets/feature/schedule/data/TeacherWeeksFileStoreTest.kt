package dev.alllexey.itmowidgets.feature.schedule.data

import java.io.File
import kotlinx.datetime.LocalDate
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TeacherWeeksFileStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    private val directory get() = File(temporary.root, "teacher_lessons")
    private val store get() = TeacherWeeksFileStore(directory.toOkioPath())

    @Test
    fun `the 2_2 file reads into the same weeks and is rewritten unchanged`() {
        val original = copyStored22("teacher_lessons/weeks.json", directory)

        val weeks = store.read()

        assertEquals(
            mapOf(
                LocalDate(2026, 9, 28) to listOf(
                    WeekLesson(100101, 3001, "Тестовая дисциплина"),
                    WeekLesson(100102, 3002, "")
                )
            ),
            weeks
        )
        store.write(weeks)
        assertSameJson(stored22("teacher_lessons/weeks.json").decodeToString(), original.readText())
        assertEquals(weeks, store.read())
    }

    @Test
    fun `a missing file reads as empty, the format is written and clear removes the directory`() {
        assertEquals(emptyMap<LocalDate, List<WeekLesson>>(), store.read())
        store.write(emptyMap())

        assertTrue(File(directory, "weeks.json").readText().contains("\"format\":1"))
        assertFalse(File(directory, "weeks.json.new").exists())
        store.clear()

        assertFalse(directory.exists())
        assertEquals(emptyMap<LocalDate, List<WeekLesson>>(), store.read())
    }

    @Test
    fun `another format, a week not starting on Monday or a broken file fail to read`() {
        directory.mkdirs()
        val file = File(directory, "weeks.json")
        listOf(
            """{"format":2,"weeks":{}}""",
            """{"format":1,"weeks":{"2026-09-29":[]}}""",
            """{"format":1,"weeks":{"2026-09-28":[{"teacherIsu":0,"flowId":1,"subject":""}]}}""",
            """{"format":1,"weeks":{"2026-09-28":[{"teacherIsu":1,"flowId":1}]}}""",
            "{",
        ).forEach { content ->
            file.writeText(content)
            assertThrows(content, Exception::class.java) { store.read() }
        }
    }
}
