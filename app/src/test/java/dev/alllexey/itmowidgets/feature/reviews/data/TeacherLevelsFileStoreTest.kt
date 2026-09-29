package dev.alllexey.itmowidgets.feature.reviews.data

import com.google.gson.Gson
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TeacherLevelsFileStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    private val directory get() = File(temporary.root, "teacher_levels")
    private val store get() = TeacherLevelsFileStore(directory, Gson())

    @Test
    fun `written levels read back including remembered absences`() {
        val levels = mapOf(100001 to StoredLevel("POSITIVE", 1_000L), 100002 to StoredLevel(null, 2_000L))

        store.write(levels)

        assertEquals(levels, store.read())
        assertFalse(File(directory, "levels.json.tmp").exists())
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
        val file = File(directory, "levels.json")
        listOf(
            """{"format":2,"entries":{}}""",
            """{"format":1,"entries":{"100001":{"level":"GREAT","fetchedAt":1}}}""",
            """{"format":1,"entries":{"abc":{"level":null,"fetchedAt":1}}}""",
            """{"format":1,"entries":{"0":{"level":null,"fetchedAt":1}}}""",
            "{",
        ).forEach { content ->
            file.writeText(content)
            assertThrows(content, Exception::class.java) { store.read() }
        }
    }
}
