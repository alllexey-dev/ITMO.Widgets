package dev.alllexey.itmowidgets.feature.schedule.data

import dev.alllexey.itmowidgets.testkit.fakeFileSystemOf
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

/** `files/teacher_lessons/weeks.json` on okio's fake file system; the 2.2 golden is `TeacherWeeks22GoldenTest` in `:app`. */
class TeacherWeeksFileStoreTest {

    private val directory = "/files/teacher_lessons".toPath()
    private val file = directory / "weeks.json"
    private val fileSystem: FakeFileSystem = fakeFileSystemOf()
    private val store get() = TeacherWeeksFileStore(directory, fileSystem)

    @AfterTest
    fun tearDown() {
        fileSystem.checkNoOpenFiles()
    }

    @Test
    fun theWrittenWeeksReadBackInTheSameShape() {
        val weeks = mapOf(
            LocalDate(2026, 9, 28) to listOf(WeekLesson(100101, 3001, "Тестовая дисциплина"), WeekLesson(100102, 3002, ""))
        )

        store.write(weeks)

        assertEquals(
            """{"format":1,"weeks":{"2026-09-28":[{"teacherIsu":100101,"flowId":3001,"subject":"Тестовая дисциплина"},""" +
                """{"teacherIsu":100102,"flowId":3002,"subject":""}]}}""",
            fileSystem.read(file) { readUtf8() }
        )
        assertEquals(weeks, store.read())
    }

    @Test
    fun aMissingFileReadsAsEmptyTheFormatIsWrittenAndClearRemovesTheDirectory() {
        assertEquals(emptyMap(), store.read())
        store.write(emptyMap())

        assertTrue(fileSystem.read(file) { readUtf8() }.contains("\"format\":1"))
        assertFalse(fileSystem.exists(directory / "weeks.json.new"))
        store.clear()

        assertFalse(fileSystem.exists(directory))
        assertEquals(emptyMap(), store.read())
    }

    @Test
    fun anotherFormatAWeekNotStartingOnMondayOrABrokenFileFailToRead() {
        fileSystem.createDirectories(directory)
        listOf(
            """{"format":2,"weeks":{}}""",
            """{"format":1,"weeks":{"2026-09-29":[]}}""",
            """{"format":1,"weeks":{"2026-09-28":[{"teacherIsu":0,"flowId":1,"subject":""}]}}""",
            """{"format":1,"weeks":{"2026-09-28":[{"teacherIsu":1,"flowId":1}]}}""",
            "{",
        ).forEach { content ->
            fileSystem.write(file) { writeUtf8(content) }
            assertFailsWith<Exception>(content) { store.read() }
        }
    }
}
