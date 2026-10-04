package dev.alllexey.itmowidgets.upgrade.stores

import dev.alllexey.itmowidgets.feature.reviews.data.StoredLevel
import dev.alllexey.itmowidgets.feature.reviews.data.TeacherLevelsFileStore
import dev.alllexey.itmowidgets.upgrade.Captured22.AT_MS
import dev.alllexey.itmowidgets.upgrade.Upgrade22Fixture
import java.io.File
import org.junit.Assert.assertEquals

/** `files/teacher_levels/levels.json` (format 1): a known level and a remembered "no level". */
object TeacherLevelsFileStoreUpgrade {

    fun check(fixture: Upgrade22Fixture) {
        assertEquals(
            mapOf(100101 to StoredLevel("POSITIVE", AT_MS), 100102 to StoredLevel(null, AT_MS)),
            TeacherLevelsFileStore(File(fixture.filesDir, "teacher_levels"), fixture.gson).read()
        )
    }
}
