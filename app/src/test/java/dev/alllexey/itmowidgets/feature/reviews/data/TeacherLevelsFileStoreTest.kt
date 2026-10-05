package dev.alllexey.itmowidgets.feature.reviews.data

import java.io.File
import kotlinx.serialization.json.Json
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TeacherLevelsFileStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    private val directory get() = File(temporary.root, "teacher_levels")
    private val store get() = TeacherLevelsFileStore(directory.toOkioPath())
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

    @Test
    fun `written levels read back including remembered absences`() {
        val levels = mapOf(100001 to StoredLevel("POSITIVE", 1_000L), 100002 to StoredLevel(null, 2_000L))

        store.write(levels)

        assertEquals(levels, store.read())
        assertFalse(file.readText().contains("null"))
        assertFalse(File(directory, "levels.json.new").exists())
    }

    @Test
    fun `a missing file reads as empty and clear removes the directory`() {
        assertEquals(emptyMap<Int, StoredLevel>(), store.read())
        store.write(mapOf(100001 to StoredLevel("MIXED", 1L)))

        store.clear()

        assertFalse(directory.exists())
        assertEquals(emptyMap<Int, StoredLevel>(), store.read())
    }

    @Test
    fun `another format an unknown level or a broken file fail to read`() {
        directory.mkdirs()
        listOf(
            """{"format":2,"entries":{}}""",
            """{"format":1,"entries":{"100001":{"level":"GREAT","fetchedAt":1}}}""",
            """{"format":1,"entries":{"100001":{"level":"POSITIVE"}}}""",
            """{"format":1,"entries":{"abc":{"level":null,"fetchedAt":1}}}""",
            """{"format":1,"entries":{"0":{"level":null,"fetchedAt":1}}}""",
            "{",
        ).forEach { content ->
            file.writeText(content)
            assertThrows(content, Exception::class.java) { store.read() }
        }
    }

    /** A file 2.2 wrote, from `src/test/resources/stores/` (see its README). */
    private fun stored22(path: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/stores/$path")) { "No golden $path" }.use { it.readBytes() }
}
