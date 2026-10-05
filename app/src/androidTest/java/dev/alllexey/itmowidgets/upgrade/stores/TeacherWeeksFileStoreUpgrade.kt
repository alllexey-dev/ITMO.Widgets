package dev.alllexey.itmowidgets.upgrade.stores

import dev.alllexey.itmowidgets.core.storage.AndroidAppDirectories
import dev.alllexey.itmowidgets.feature.schedule.data.TeacherWeeksFileStore
import dev.alllexey.itmowidgets.feature.schedule.data.WeekLesson
import dev.alllexey.itmowidgets.upgrade.Captured22.SUBJECT
import dev.alllexey.itmowidgets.upgrade.Upgrade22Fixture
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * `files/teacher_lessons/weeks.json` (format 1): one finished week, one lesson without a subject name. Gson wrote it;
 * kotlinx reads it and writes format 1 back.
 */
object TeacherWeeksFileStoreUpgrade {

    fun check(fixture: Upgrade22Fixture) {
        val expected = mapOf(
            LocalDate.parse("2026-09-28") to listOf(WeekLesson(100101, 3001, SUBJECT), WeekLesson(100102, 3002, ""))
        )
        val store = TeacherWeeksFileStore(AndroidAppDirectories(fixture.context))

        assertEquals(expected, store.read())
        store.write(expected)
        val written = File(fixture.filesDir, "teacher_lessons/weeks.json").readText()
        assertTrue(written, written.startsWith("{\"format\":1,"))
        assertEquals(expected, TeacherWeeksFileStore(AndroidAppDirectories(fixture.context)).read())
    }
}
