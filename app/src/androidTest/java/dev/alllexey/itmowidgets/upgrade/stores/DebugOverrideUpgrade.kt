package dev.alllexey.itmowidgets.upgrade.stores

import dev.alllexey.itmowidgets.core.debug.FileSportLessonTemplateStore
import dev.alllexey.itmowidgets.core.debug.FileSportScoreOverrideStore
import dev.alllexey.itmowidgets.core.debug.SportScoreOverride
import dev.alllexey.itmowidgets.core.storage.AndroidAppDirectories
import dev.alllexey.itmowidgets.core.storage.AtomicTextFile
import dev.alllexey.itmowidgets.core.time.FileAcademicTimeOverrideStore
import dev.alllexey.itmowidgets.upgrade.Upgrade22Fixture
import java.io.File
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/** The three debug overrides in `no_backup/debug/`; this suite runs on debug builds, where they are read. */
object DebugOverrideUpgrade {

    fun check(fixture: Upgrade22Fixture) {
        val debug = File(AndroidAppDirectories(fixture.context).noBackup.toFile(), "debug")
        assertTrue(FileSportLessonTemplateStore(File(debug, "sport_lesson_templates")).isEnabled())
        assertEquals(
            SportScoreOverride(attendances = 7, bonus = 3),
            FileSportScoreOverrideStore(File(debug, "sport_score_override")).getOverride()
        )
        assertEquals(
            LocalDate.parse("2026-10-12"),
            FileAcademicTimeOverrideStore(AtomicTextFile(File(debug, "academic_date_override"))).getOverrideDate()
        )
    }
}
