package dev.alllexey.itmowidgets.feature.resources.data

import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.LinkVisibility
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import java.io.File
import kotlin.time.Instant
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The files 2.2 and the SP-08 builds wrote, from `src/test/resources/stores/`, read by the store that moved to
 * `:shared:feature-resources`; the store's own rules are `SubjectLinksFileStoreTest` there.
 */
class SubjectLinks22GoldenTest {
    @get:Rule val temporary = TemporaryFolder()

    private val directory get() = File(temporary.root, "files/subject_links")
    private val store get() = SubjectLinksFileStore(object : AppDirectories {
        override val files = File(temporary.root, "files").toOkioPath()
        override val cache = File(temporary.root, "cache").toOkioPath()
        override val noBackup = File(temporary.root, "no_backup").toOkioPath()
    })
    private val file get() = File(directory, "cache.json")

    @Test
    fun `the 2_2 file keeps its device link and pin and drops the cached answer`() {
        copyStored22("subject_links/cache.json")
        val scope = ResourceScope(2001, "Тестовая дисциплина", "2026-1")
        val id = "00000000-0000-4000-8000-000000000022"

        assertEquals(
            StoredLinks(
                local = mapOf(
                    id to LocalLink(
                        id,
                        StoredLinkRequest(
                            2001, "Тестовая дисциплина", "2026-1", LinkCategory.MATERIALS,
                            "https://example.com/upgrade22/materials", "Тестовые материалы", LinkVisibility.PRIVATE
                        ),
                        Instant.parse("2026-10-04T09:00:00Z")
                    )
                ),
                localPins = mapOf(scope.key to LocalPin(scope, id)),
            ),
            store.read()
        )
    }

    @Test
    fun `formats 1 and 2 read into the same device links and pins with no cached answers`() {
        copyStored22("subject_links/cache-sp08.json")
        val second = store.read()
        copyStored22("subject_links/cache-format1.json")
        val first = store.read()

        val scope = ResourceScope(42, "Тестовый предмет", "2026-1")
        val pinned = "0b6c1d5e-7a35-4f0e-9a8e-1f2d3c4b5a60"
        val other = "1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f"
        val expected = StoredLinks(
            local = mapOf(
                pinned to LocalLink(
                    pinned,
                    StoredLinkRequest(42, "Тестовый предмет", "2026-1", LinkCategory.TASKS, "https://example.org/tasks", "Лабы", LinkVisibility.PRIVATE),
                    Instant.parse("2026-09-22T09:00:00Z")
                ),
                other to LocalLink(
                    other,
                    StoredLinkRequest(43, "Second subject «кавычки»", "2025-2", LinkCategory.OTHER, "https://example.org/x?a=1&b=2", null, LinkVisibility.PRIVATE),
                    Instant.parse("2026-09-23T08:15:30.123456789Z")
                ),
            ),
            localPins = mapOf(scope.key to LocalPin(scope, pinned)),
        )
        assertEquals(expected, second)
        assertEquals(expected, first)
    }

    @Test
    fun `the first write after a format 2 read emits format 3 with ISO instants`() {
        copyStored22("subject_links/cache.json")
        val state = store.read()

        store.write(state)

        val written = file.readText()
        assertTrue(written, written.startsWith("{\"format\":3,"))
        assertTrue(written, written.contains("\"updatedAt\":\"2026-10-04T09:00:00Z\""))
        assertFalse(written, written.contains("\"scopes\":{\""))
        assertFalse(File(directory, "cache.json.new").exists())
        assertEquals(state, store.read())
    }

    /** A file 2.2 wrote, from `src/test/resources/stores/` (see its README), as this store's `cache.json`. */
    private fun copyStored22(path: String) {
        val bytes = checkNotNull(javaClass.getResourceAsStream("/stores/$path")) { "No golden $path" }.use { it.readBytes() }
        directory.mkdirs()
        file.writeBytes(bytes)
    }
}
