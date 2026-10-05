package dev.alllexey.itmowidgets.upgrade.stores

import dev.alllexey.itmowidgets.core.storage.AndroidAppDirectories
import dev.alllexey.itmowidgets.feature.reviews.data.StoredLevel
import dev.alllexey.itmowidgets.feature.reviews.data.TeacherLevelsFileStore
import dev.alllexey.itmowidgets.upgrade.Captured22.AT_MS
import dev.alllexey.itmowidgets.upgrade.Upgrade22Fixture
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * `files/teacher_levels/levels.json` (format 1): a known level and a remembered "no level". Gson wrote it; kotlinx
 * reads it and writes format 1 back.
 */
object TeacherLevelsFileStoreUpgrade {

    fun check(fixture: Upgrade22Fixture) {
        val expected = mapOf(100101 to StoredLevel("POSITIVE", AT_MS), 100102 to StoredLevel(null, AT_MS))
        val store = TeacherLevelsFileStore(AndroidAppDirectories(fixture.context))

        assertEquals(expected, store.read())
        store.write(expected)
        val written = File(fixture.filesDir, "teacher_levels/levels.json").readText()
        assertTrue(written, written.startsWith("{\"format\":1,"))
        assertEquals(expected, TeacherLevelsFileStore(AndroidAppDirectories(fixture.context)).read())
    }
}
