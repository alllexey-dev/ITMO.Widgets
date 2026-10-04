package dev.alllexey.itmowidgets.upgrade.stores

import dev.alllexey.itmowidgets.feature.schedule.data.TeacherWeeksFileStore
import dev.alllexey.itmowidgets.feature.schedule.data.WeekLesson
import dev.alllexey.itmowidgets.upgrade.Captured22.SUBJECT
import dev.alllexey.itmowidgets.upgrade.Upgrade22Fixture
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals

/** `files/teacher_lessons/weeks.json` (format 1): one finished week, one lesson without a subject name. */
object TeacherWeeksFileStoreUpgrade {

    fun check(fixture: Upgrade22Fixture) {
        assertEquals(
            mapOf(
                LocalDate.parse("2026-09-28") to listOf(WeekLesson(100101, 3001, SUBJECT), WeekLesson(100102, 3002, ""))
            ),
            TeacherWeeksFileStore(File(fixture.filesDir, "teacher_lessons"), fixture.gson).read()
        )
    }
}
