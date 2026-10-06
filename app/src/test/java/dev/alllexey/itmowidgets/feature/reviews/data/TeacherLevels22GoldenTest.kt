package dev.alllexey.itmowidgets.feature.reviews.data

import dev.alllexey.itmowidgets.core.storage.AppDirectories
import java.io.File
import kotlinx.serialization.json.Json
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The files 2.2 and the SP-08 builds wrote, from `src/test/resources/stores/`, read by the store that moved to
 * `:shared:feature-reviews`; the store's own rules are `TeacherLevelsFileStoreTest` there.
 */
class TeacherLevels22GoldenTest {
    @get:Rule val temporary = TemporaryFolder()

    private val directory get() = File(temporary.root, "files/teacher_levels")
    private val store get() = TeacherLevelsFileStore(object : AppDirectories {
        override val files = File(temporary.root, "files").toOkioPath()
        override val cache = File(temporary.root, "cache").toOkioPath()
        override val noBackup = File(temporary.root, "no_backup").toOkioPath()
    })
    private val file get() = File(directory, "levels.json")

    @Test
    fun `the 2_2 files read into the same levels and are rewritten unchanged`() {
        mapOf(
            "teacher_levels/levels.json" to mapOf(
                100101 to StoredLevel("POSITIVE", 1_791_104_400_000), 100102 to StoredLevel(null, 1_791_104_400_000)
            ),
            "teacher_levels/levels-sp08.json" to mapOf(
                300010 to StoredLevel("POSITIVE", 1_790_000_000_000), 300011 to StoredLevel(null, 1_790_000_100_000),
                300012 to StoredLevel("VERY_NEGATIVE", 1_790_000_200_000)
            ),
        ).forEach { (path, expected) ->
            val original = stored22(path)
            directory.mkdirs()
            file.writeBytes(original)

            val levels = store.read()

            assertEquals(path, expected, levels)
            store.write(levels)
            val written = file.readText()
            assertTrue(written, written.startsWith("{\"format\":1,"))
            assertEquals(path, Json.parseToJsonElement(original.decodeToString()), Json.parseToJsonElement(written))
            assertEquals(path, expected, store.read())
        }
    }

    /** A file 2.2 wrote, from `src/test/resources/stores/` (see its README). */
    private fun stored22(path: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/stores/$path")) { "No golden $path" }.use { it.readBytes() }
}
