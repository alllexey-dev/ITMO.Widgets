package dev.alllexey.itmowidgets.feature.schedule.data

import java.io.File
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The 2.2 `teacher_lessons/weeks.json` reads into the same weeks and is written back with every value unchanged. */
class TeacherWeeks22GoldenTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `the 2_2 file reads into the same weeks and is rewritten unchanged`() {
        val original = copyStored22(FIXTURE, File(temporary.root, "files/teacher_lessons"))
        val store = TeacherWeeksFileStore(directoriesAt(temporary.root))

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
        assertSameJson(stored22(FIXTURE).decodeToString(), original.readText())
        assertEquals(weeks, store.read())
    }

    private companion object {
        const val FIXTURE = "teacher_lessons/weeks.json"
    }
}
