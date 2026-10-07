package dev.alllexey.itmowidgets.feature.reviews.data

import dev.alllexey.itmowidgets.testkit.fakeFileSystemOf
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

/** `files/teacher_levels/levels.json` on okio's fake file system; the 2.2 golden is `TeacherLevels22GoldenTest` in `:app`. */
class TeacherLevelsFileStoreTest {

    private val directory = "/files/teacher_levels".toPath()
    private val file = directory / "levels.json"
    private val fileSystem: FakeFileSystem = fakeFileSystemOf()
    private val store get() = TeacherLevelsFileStore(directory, fileSystem)

    @AfterTest
    fun tearDown() {
        fileSystem.checkNoOpenFiles()
    }

    @Test
    fun writtenLevelsReadBackIncludingRememberedAbsences() {
        val levels = mapOf(100001 to StoredLevel("POSITIVE", 1_000L), 100002 to StoredLevel(null, 2_000L))

        store.write(levels)

        assertEquals(levels, store.read())
        assertFalse(fileSystem.read(file) { readUtf8() }.contains("null"))
        assertFalse(fileSystem.exists(directory / "levels.json.new"))
    }

    @Test
    fun aMissingFileReadsAsEmptyAndClearRemovesTheDirectory() {
        assertEquals(emptyMap<Int, StoredLevel>(), store.read())
        store.write(mapOf(100001 to StoredLevel("MIXED", 1L)))

        store.clear()

        assertFalse(fileSystem.exists(directory))
        assertEquals(emptyMap<Int, StoredLevel>(), store.read())
    }

    @Test
    fun anotherFormatAnUnknownLevelOrABrokenFileFailToRead() {
        fileSystem.createDirectories(directory)
        listOf(
            """{"format":2,"entries":{}}""",
            """{"format":1,"entries":{"100001":{"level":"GREAT","fetchedAt":1}}}""",
            """{"format":1,"entries":{"100001":{"level":"POSITIVE"}}}""",
            """{"format":1,"entries":{"abc":{"level":null,"fetchedAt":1}}}""",
            """{"format":1,"entries":{"0":{"level":null,"fetchedAt":1}}}""",
            "{",
        ).forEach { content ->
            fileSystem.write(file) { writeUtf8(content) }
            assertFailsWith<Exception>(content) { store.read() }
        }
    }
}
