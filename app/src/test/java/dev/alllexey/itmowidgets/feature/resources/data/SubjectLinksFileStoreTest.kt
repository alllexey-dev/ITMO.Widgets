package dev.alllexey.itmowidgets.feature.resources.data

import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.LinkVisibility
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.resources.SubjectLinkStatus
import java.io.File
import kotlin.time.Instant
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SubjectLinksFileStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    private val directory get() = File(temporary.root, "subject_links")
    private val store get() = SubjectLinksFileStore(directory.toOkioPath())
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

    @Test
    fun `format 3 round-trips local links, pins and cached answers`() {
        val scope = ResourceScope(42, "Предмет", "2026-1")
        val author = StoredAuthor(
            300002, "Тестовый Автор", null, listOf(StoredAuthorGroup("T0000", 3, "ФТ")),
            StoredAuthorSharing(sport = false, schedule = true, friends = true)
        )
        val shared = link("5f0e8a1c-2b3d-4c5e-8f90-a1b2c3d4e5f6", scope).copy(
            visibility = LinkVisibility.FLOW, flowId = 7001, audienceLabel = "ТЕСТ 3.2.1", score = 3, myVote = 1, author = author
        )
        val previous = link("6a7b8c9d-0e1f-4a2b-9c3d-4e5f6a7b8c9d", scope.copy(periodKey = "2025-1")).copy(
            category = LinkCategory.EXAM, title = null, reportedByMe = true, score = -1
        )
        val mine = link("7b8c9d0e-1f2a-4b3c-8d4e-5f6a7b8c9d0e", scope).copy(
            isMine = true, status = SubjectLinkStatus.REJECTED, reviewNote = "Не по теме"
        )
        val localId = "0b6c1d5e-7a35-4f0e-9a8e-1f2d3c4b5a60"
        val state = StoredLinks(
            local = mapOf(
                localId to LocalLink(
                    localId,
                    StoredLinkRequest(42, "Предмет", "2026-1", LinkCategory.TASKS, "https://example.org/tasks", null, LinkVisibility.PRIVATE),
                    Instant.parse("2026-09-22T09:00:00.5Z")
                )
            ),
            localPins = mapOf(scope.key to LocalPin(scope, localId)),
            scopes = mapOf(
                scope.key to CachedLinks(
                    scope,
                    StoredLinksAnswer(
                        listOf(mine), listOf(shared), listOf(previous), shared.id,
                        listOf(StoredAudience(7001, "ТЕСТ 3.2.1", 3, 3), StoredAudience(7000, "ТЕСТ 3.2", 1, 2)), premoderation = true
                    )
                )
            ),
        )

        store.write(state)

        assertEquals(state, store.read())
        assertFalse(file.readText().contains("null"))
    }

    @Test
    fun `a missing file reads as empty and clear removes the directory`() {
        assertEquals(StoredLinks(), store.read())
        store.write(StoredLinks())

        store.clear()

        assertFalse(directory.exists())
        assertEquals(StoredLinks(), store.read())
    }

    @Test
    fun `a corrupt file, an unknown format or a wrong scope key fail to read`() {
        val id = "0b6c1d5e-7a35-4f0e-9a8e-1f2d3c4b5a60"
        val scope = """{"subjectId":42,"subjectName":"Предмет","periodKey":"2026-1"}"""
        val request = """{"subjectId":42,"subjectName":"Предмет","periodKey":"2026-1","category":"TASKS","url":"https://example.org","visibility":"PRIVATE"}"""
        val local = """{"$id":{"id":"$id","request":$request,"updatedAt":"2026-09-22T09:00:00Z"}}"""
        val answer = """{"mine":[],"shared":[],"previous":[],"audiences":[],"premoderation":true}"""
        directory.mkdirs()
        listOf(
            "{",
            """{"local":{}}""",
            """{"format":4,"local":{}}""",
            """{"format":0,"local":{}}""",
            """{"format":3}""",
            """{"format":3,"local":{"$id":{"id":"other","request":$request,"updatedAt":"2026-09-22T09:00:00Z"}}}""",
            """{"format":3,"local":{"not-a-uuid":{"id":"not-a-uuid","request":$request,"updatedAt":"2026-09-22T09:00:00Z"}}}""",
            """{"format":3,"local":${local.replace("PRIVATE", "ALL")}}""",
            """{"format":3,"local":${local.replace("2026-1", "2026-3")}}""",
            """{"format":3,"local":${local.replace("2026-09-22T09:00:00Z", "yesterday")}}""",
            """{"format":3,"local":{},"localPins":{"42-2026-2":{"scope":$scope,"linkId":"$id"}}}""",
            """{"format":2,"local":{},"localPins":{"42-2026-2":{"scope":$scope,"linkId":"$id"}}}""",
            """{"format":3,"local":{},"scopes":{"42-2026-2":{"scope":$scope,"response":$answer}}}""",
            """{"format":3,"local":{},"scopes":{"42-2026-1":{"scope":$scope,"response":${answer.replace("\"premoderation\":true", "\"pinnedId\":\"x\",\"premoderation\":true")}}}}""",
        ).forEach { content ->
            file.writeText(content)
            assertThrows(content, Exception::class.java) { store.read() }
        }
    }

    private fun link(id: String, scope: ResourceScope) = StoredLink(
        id = id,
        subjectId = scope.subjectId,
        subjectName = scope.subjectName,
        periodKey = scope.periodKey,
        category = LinkCategory.MATERIALS,
        url = "https://example.org/$id",
        title = "Материалы",
        visibility = LinkVisibility.ALL,
        status = SubjectLinkStatus.PUBLISHED,
        score = 0,
        myVote = 0,
        isMine = false,
        reportedByMe = false,
        updatedAt = Instant.parse("2026-09-20T07:30:00Z"),
    )

    /** A file 2.2 wrote, from `src/test/resources/stores/` (see its README), as this store's `cache.json`. */
    private fun copyStored22(path: String) {
        val bytes = checkNotNull(javaClass.getResourceAsStream("/stores/$path")) { "No golden $path" }.use { it.readBytes() }
        directory.mkdirs()
        file.writeBytes(bytes)
    }
}
