package dev.alllexey.itmowidgets.feature.resources.data

import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.LinkVisibility
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.resources.SubjectLinkStatus
import dev.alllexey.itmowidgets.testkit.fakeFileSystemOf
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

/** `files/subject_links/cache.json` on okio's fake file system; the 2.2 goldens are `SubjectLinks22GoldenTest` in `:app`. */
class SubjectLinksFileStoreTest {

    private val directory = "/files/subject_links".toPath()
    private val file = directory / "cache.json"
    private val fileSystem: FakeFileSystem = fakeFileSystemOf()
    private val store get() = SubjectLinksFileStore(directory, fileSystem)

    @AfterTest
    fun tearDown() {
        fileSystem.checkNoOpenFiles()
    }

    @Test
    fun aFormat2FileKeepsItsDeviceLinkWithTheGsonDateAndIsRewrittenAsFormat3() {
        val id = "0b6c1d5e-7a35-4f0e-9a8e-1f2d3c4b5a60"
        val request = """{"subjectId":42,"subjectName":"Предмет","periodKey":"2026-1","category":"TASKS",""" +
            """"url":"https://example.org/tasks","visibility":"PRIVATE"}"""
        fileSystem.createDirectories(directory)
        fileSystem.write(file) {
            writeUtf8(
                """{"format":2,"local":{"$id":{"id":"$id","request":$request,"updatedAt":"2026-09-22T12:00+03:00"}},""" +
                    """"localPins":{},"scopes":{"42-2026-1":{"stale":true}}}"""
            )
        }

        val state = store.read()
        store.write(state)

        assertEquals(Instant.parse("2026-09-22T09:00:00Z"), state.local.getValue(id).updatedAt)
        assertTrue(state.scopes.isEmpty())
        val written = fileSystem.read(file) { readUtf8() }
        assertTrue(written.startsWith("{\"format\":3,"), written)
        assertTrue(written.contains("\"updatedAt\":\"2026-09-22T09:00:00Z\""), written)
        assertFalse(fileSystem.exists(directory / "cache.json.new"))
        assertEquals(state, store.read())
    }

    @Test
    fun format3RoundTripsLocalLinksPinsAndCachedAnswers() {
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
        assertFalse(fileSystem.read(file) { readUtf8() }.contains("null"))
    }

    @Test
    fun aMissingFileReadsAsEmptyAndClearRemovesTheDirectory() {
        assertEquals(StoredLinks(), store.read())
        store.write(StoredLinks())

        store.clear()

        assertFalse(fileSystem.exists(directory))
        assertEquals(StoredLinks(), store.read())
    }

    @Test
    fun aCorruptFileAnUnknownFormatOrAWrongScopeKeyFailToRead() {
        val id = "0b6c1d5e-7a35-4f0e-9a8e-1f2d3c4b5a60"
        val scope = """{"subjectId":42,"subjectName":"Предмет","periodKey":"2026-1"}"""
        val request = """{"subjectId":42,"subjectName":"Предмет","periodKey":"2026-1","category":"TASKS","url":"https://example.org","visibility":"PRIVATE"}"""
        val local = """{"$id":{"id":"$id","request":$request,"updatedAt":"2026-09-22T09:00:00Z"}}"""
        val answer = """{"mine":[],"shared":[],"previous":[],"audiences":[],"premoderation":true}"""
        fileSystem.createDirectories(directory)
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
            fileSystem.write(file) { writeUtf8(content) }
            assertFailsWith<Exception>(content) { store.read() }
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
}
