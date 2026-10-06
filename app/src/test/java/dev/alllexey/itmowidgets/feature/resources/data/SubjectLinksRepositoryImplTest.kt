package dev.alllexey.itmowidgets.feature.resources.data

import dev.alllexey.itmowidgets.client.common.ModerationReportRequest
import dev.alllexey.itmowidgets.client.common.ResourceVoteRequest
import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.client.links.PinSubjectLinkRequest
import dev.alllexey.itmowidgets.client.links.SaveSubjectLinkRequest
import dev.alllexey.itmowidgets.client.links.SubjectLinksApi
import dev.alllexey.itmowidgets.client.links.SubjectLinksResponse
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.demo.DemoStudy
import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.network.Core2Harness
import dev.alllexey.itmowidgets.core.network.Core2Harness.Companion.contractFixture
import dev.alllexey.itmowidgets.core.network.Core2Harness.Companion.errorEnvelope
import dev.alllexey.itmowidgets.core.network.Core2Harness.Companion.session
import dev.alllexey.itmowidgets.core.resources.LinkAudience
import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.LinkVisibility
import dev.alllexey.itmowidgets.core.resources.ResourceReportReason
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.resources.RestrictionCapability
import dev.alllexey.itmowidgets.core.resources.SubjectLinkStatus
import dev.alllexey.itmowidgets.core.resources.SubjectLinksSnapshot
import dev.alllexey.itmowidgets.core.resources.SubjectLinksState
import dev.alllexey.itmowidgets.core.resources.blocks
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.FakeBackendGate
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.testing.blockingIoAppDispatchers
import dev.alllexey.itmowidgets.core.testing.noDemo
import dev.alllexey.itmowidgets.testkit.respondJson
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid
import dev.alllexey.itmowidgets.client.links.LinkAudience as WireAudience
import dev.alllexey.itmowidgets.client.links.LinkCategory as WireCategory
import dev.alllexey.itmowidgets.client.links.LinkVisibility as WireVisibility
import dev.alllexey.itmowidgets.client.links.SubjectLink as WireLink
import dev.alllexey.itmowidgets.client.links.SubjectLinkStatus as WireStatus
import dev.alllexey.itmowidgets.client.links.UserRestriction as WireRestriction

@OptIn(ExperimentalCoroutinesApi::class)
class SubjectLinksRepositoryImplTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** The fake network holds a request on a blocked thread; see [blockingIoAppDispatchers]. */
    private val dispatchers = blockingIoAppDispatchers(mainDispatcherRule.dispatcher)

    @get:Rule val temporary = TemporaryFolder()
    private val scope = ResourceScope(42, "Предмет", "2026-1")
    private val now = Instant.parse("2026-09-22T09:00:00Z")
    private val clock = object : Clock { override fun now() = this@SubjectLinksRepositoryImplTest.now }
    private val id = Uuid.random().toString()
    private val url = "https://github.com/example"

    @Test fun `without the opt-in links stay on the device and survive a restart`() = runTest {
        val api = FakeSubjectLinksApi(); val services = FakeBackendGate(false); val folder = temporary.newFolder()
        val repository = repo(folder, api, services)

        assertTrue(repository.save(scope, id, LinkCategory.TASKS, url, "Лабы", LinkVisibility.PRIVATE, null) is AppResult.Success)
        assertTrue(repository.refresh(scope) is AppResult.Success)

        assertTrue(api.calls.isEmpty())
        val restored = repo(folder, api, services).content(scope).mine.single()
        assertEquals(id, restored.id)
        assertEquals("Лабы", restored.title)
        assertTrue(restored.local)
        assertEquals(now, restored.updatedAt)
        assertTrue(File(folder, "cache.json").readText().contains("\"updatedAt\":\"2026-09-22T09:00:00Z\""))
    }

    @Test fun `without the opt-in shared actions and non-private visibility need the connection`() = runTest {
        val repository = repo(temporary.newFolder(), FakeSubjectLinksApi(), FakeBackendGate(false))
        val disabled = AppResult.Failure(AppError.CustomServicesDisabled)

        assertEquals(disabled, repository.save(scope, id, LinkCategory.TASKS, url, null, LinkVisibility.FLOW, 7103))
        assertEquals(disabled, repository.vote(scope, id, 1))
        assertEquals(disabled, repository.pin(scope, id))
    }

    @Test fun `the first refresh with the opt-in uploads local links as private and drops them locally`() = runTest {
        val api = FakeSubjectLinksApi(); val services = FakeBackendGate(false); val folder = temporary.newFolder()
        val repository = repo(folder, api, services)
        repository.save(scope, id, LinkCategory.TASKS, url, "Лабы", LinkVisibility.PRIVATE, null)
        repository.pin(scope, id)

        services.optedIn.value = true
        assertTrue(repository.refresh(scope) is AppResult.Success)

        assertEquals(listOf("saveSubjectLink", "pinSubjectLink", "subjectLinks"), api.calls)
        assertEquals(WireVisibility.PRIVATE, api.saved.single().visibility)
        val link = repository.content(scope).mine.single()
        assertEquals(id, link.id)
        assertFalse(link.local)
        assertEquals(id, repository.content(scope).pinnedId)
        services.optedIn.value = false
        assertTrue(repo(folder, api, services).content(scope).mine.isEmpty())
    }

    @Test fun `a flow link sends its flow and reads back the flow name and the audiences`() = runTest {
        val api = FakeSubjectLinksApi(); val repository = repo(temporary.newFolder(), api, FakeBackendGate(true))
        repository.refresh(scope)

        val saved = repository.save(scope, id, LinkCategory.TASKS, url, null, LinkVisibility.FLOW, 7103)

        val request = api.saved.single()
        assertEquals(WireVisibility.FLOW, request.visibility)
        assertEquals(7103L, request.flowId)
        val link = (saved as AppResult.Success).value
        assertEquals(LinkVisibility.FLOW, link.visibility)
        assertEquals(7103L, link.flowId)
        assertEquals("ФИЗ ПИИКТ 3.2.1", link.audienceLabel)
        assertEquals(listOf(LinkAudience(7101, "ФИЗ ПИИКТ 3", 1, 1), LinkAudience(7103, "ФИЗ ПИИКТ 3.2.1", 2, 3)),
            repository.content(scope).audiences)
        assertEquals(link, repository.content(scope).mine.single())
    }

    @Test fun `a flow is sent only with FLOW visibility`() = runTest {
        val api = FakeSubjectLinksApi(); val repository = repo(temporary.newFolder(), api, FakeBackendGate(true))
        repository.refresh(scope)

        assertTrue(repository.save(scope, id, LinkCategory.TASKS, url, null, LinkVisibility.FLOW, null) is AppResult.Failure)
        assertTrue(repository.save(scope, id, LinkCategory.TASKS, url, null, LinkVisibility.ALL, 7103) is AppResult.Failure)
        assertTrue(api.saved.isEmpty())
        assertTrue(repository.save(scope, id, LinkCategory.TASKS, url, null, LinkVisibility.ALL, null) is AppResult.Success)
        assertEquals(null, api.saved.single().flowId)
    }

    @Test fun `a network error keeps the cached snapshot`() = runTest {
        val api = FakeSubjectLinksApi(); val repository = repo(temporary.newFolder(), api, FakeBackendGate(true))
        api.shared += api.link(Uuid.random(), LinkCategory.SCORES, isMine = false)
        repository.refresh(scope)
        val before = repository.content(scope)

        api.failNext = true
        assertEquals(AppResult.Failure(AppError.Network), repository.refresh(scope))
        api.failNext = true
        assertEquals(AppResult.Failure(AppError.Network), repository.vote(scope, before.shared.single().id, 1))

        assertEquals(before, repository.content(scope))
    }

    @Test fun `an action answer updates the cached snapshot`() = runTest {
        val api = FakeSubjectLinksApi(); val repository = repo(temporary.newFolder(), api, FakeBackendGate(true))
        val shared = api.link(Uuid.random(), LinkCategory.SCORES, isMine = false)
        api.shared += shared
        repository.refresh(scope)

        assertTrue(repository.vote(scope, shared.id.toString(), 1) is AppResult.Success)

        val link = repository.content(scope).shared.single()
        assertEquals(1, link.myVote)
        assertEquals(1, link.score)
    }

    @Test fun `the first load of a scope that fails is an error`() = runTest {
        val api = FakeSubjectLinksApi().apply { failNext = true }
        val repository = repo(temporary.newFolder(), api, FakeBackendGate(true))

        repository.refresh(scope)

        assertEquals(SubjectLinksState.Error(AppError.Network), repository.observe(scope).first())
    }

    @Test fun `session cleanup deletes the file and ignores a late answer`() = runTest {
        val api = FakeSubjectLinksApi(); val services = FakeBackendGate(false); val folder = temporary.newFolder()
        val repository = repo(folder, api, services)
        repository.save(scope, id, LinkCategory.TASKS, url, null, LinkVisibility.PRIVATE, null)
        services.optedIn.value = true
        api.pauseFetch = true

        val pending = async { repository.refresh(scope) }
        api.entered.await()
        val cleaning = async { repository.clearSessionData() }
        runCurrent()
        api.release.countDown()
        cleaning.await()

        assertEquals(AppResult.Failure(AppError.Unauthorized), pending.await())
        assertFalse(File(folder, "cache.json").exists())
        services.optedIn.value = false
        assertTrue(repository.content(scope).mine.isEmpty())
        assertTrue(repo(folder, api, services).content(scope).mine.isEmpty())
    }

    @Test fun `a corrupted file is not replaced with an empty one`() = runTest {
        val folder = temporary.newFolder(); val original = "{unreadable"
        File(folder, "cache.json").writeText(original)
        val repository = repo(folder, FakeSubjectLinksApi(), FakeBackendGate(false))

        assertTrue(repository.observe(scope).first() is SubjectLinksState.Error)
        assertTrue(repository.save(scope, id, LinkCategory.TASKS, url, null, LinkVisibility.PRIVATE, null) is AppResult.Failure)
        assertEquals(original, File(folder, "cache.json").readText())
    }

    @Test fun `a store from the GROUP build keeps device links and drops the stale server cache`() = runTest {
        val folder = temporary.newFolder()
        File(folder, "cache.json").writeText("""
            {"format":1,
             "local":{"$id":{"id":"$id","updatedAt":"2026-09-22T09:00:00Z","request":{"subjectId":42,
               "subjectName":"Предмет","periodKey":"2026-1","category":"TASKS","url":"$url","title":"Лабы","visibility":"PRIVATE"}}},
             "localPins":{},
             "scopes":{"42-2026-1":{"scope":{"subjectId":42,"subjectName":"Предмет","periodKey":"2026-1"},
               "response":{"mine":[],"shared":[{"visibility":"GROUP"}],"previous":[],"audiences":[],"premoderation":true}}}}
        """.trimIndent())
        val repository = repo(folder, FakeSubjectLinksApi(), FakeBackendGate(false))

        val restored = repository.content(scope).mine.single()
        assertEquals(id, restored.id)
        assertTrue(restored.local)
        assertTrue(repository.content(scope).shared.isEmpty())
    }

    @Test fun `the demo shows its links and refuses every change without Backend`() = runTest {
        val api = FakeSubjectLinksApi()
        val repository = repo(temporary.newFolder(), api, FakeBackendGate(false), FakeDemoMode(active = true))
        val algorithms = ResourceScope(DemoStudy.ALGORITHMS.id, DemoStudy.ALGORITHMS.name, "2026-1")
        val refused = AppResult.Failure(AppError.DemoUnavailable)

        val snapshot = repository.content(algorithms)

        assertTrue(snapshot.shared.isNotEmpty())
        assertTrue(snapshot.mine.isNotEmpty())
        assertEquals(snapshot.shared.first { it.category == LinkCategory.SCORES }.id, snapshot.pinnedId)
        assertTrue(repository.refresh(algorithms) is AppResult.Success)
        assertEquals(refused, repository.save(algorithms, id, LinkCategory.TASKS, url, null, LinkVisibility.PRIVATE, null))
        assertEquals(refused, repository.vote(algorithms, snapshot.shared.first().id, 1))
        assertEquals(refused, repository.report(algorithms, snapshot.shared.first().id, ResourceReportReason.SPAM, null))
        assertEquals(refused, repository.pin(algorithms, null))
        assertEquals(refused, repository.delete(algorithms, snapshot.mine.first().id))
        assertTrue(api.calls.isEmpty())
    }

    // Goldens: the real Core 2.0 client over MockEngine with Backend's vendored contract fixtures.

    private val matan = ResourceScope(501, "Математический анализ", "2026-1")
    private val ownId = "00000000-0000-4000-8000-000000000101"
    private val sharedId = "00000000-0000-4000-8000-000000000100"

    @Test fun `a refresh reads every list of Backend's answer with the stored bearer`() = runTest {
        val harness = Core2Harness(session()) { respondJson(contractFixture("http/links/subjectLinks.json")) }
        val repository = repo(temporary.newFolder(), harness.client.links, FakeBackendGate(true))

        assertEquals(AppResult.Success(Unit), repository.refresh(matan))

        val request = harness.backendRequests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/api/subjects/501/links", request.url.encodedPath)
        assertEquals("2026-1", request.url.parameters["period"])
        assertEquals("Bearer stored-access", request.headers[HttpHeaders.Authorization])
        val snapshot = repository.content(matan)
        assertEquals(listOf(SubjectLinkStatus.PRIVATE, SubjectLinkStatus.PENDING, SubjectLinkStatus.REJECTED, SubjectLinkStatus.HIDDEN),
            snapshot.mine.map { it.status })
        val flow = snapshot.mine[1]
        assertEquals(LinkVisibility.FLOW, flow.visibility)
        assertEquals(7001L, flow.flowId)
        assertEquals("ЛЕК МАТАН 3.1", flow.audienceLabel)
        assertEquals("Ссылка ведёт не на тот предмет", snapshot.mine[2].reviewNote)
        assertNull(snapshot.mine[0].title)
        assertEquals(listOf(sharedId, "00000000-0000-4000-8000-000000000105", "00000000-0000-4000-8000-000000000106"),
            snapshot.shared.map { it.id })
        val shared = snapshot.shared.first()
        assertEquals(UserSummary(100002, "Друг Первый", "https://example.org/avatars/100002.jpg",
            listOf(UserGroup("К3240", 2, "ФИТИП"), UserGroup("К3240c", 2, "ФИТИП")), UserSharing(sport = true, schedule = true, friends = true)),
            shared.author)
        assertEquals(1, shared.myVote)
        assertTrue(shared.reportedByMe)
        assertEquals(Instant.parse("2026-10-01T10:00:00Z"), shared.updatedAt)
        assertEquals(listOf(LinkCategory.RECORDINGS, LinkCategory.EXAM), snapshot.previous.map { it.category })
        assertEquals(sharedId, snapshot.pinnedId)
        assertEquals(listOf(LinkAudience(7001, "ЛЕК МАТАН 3.1", 1, 1), LinkAudience(7003, "ПРАК МАТАН 3.1.2", 3, 2)), snapshot.audiences)
        assertTrue(snapshot.premoderation)
    }

    @Test fun `a link and a status from a newer Backend read as other and without a badge`() = runTest {
        val answer = contractFixture("http/links/subjectLinks.json")
            .replace("\"category\": \"CHAT\"", "\"category\": \"FUTURE_CATEGORY\"")
            .replace("\"status\": \"HIDDEN\"", "\"status\": \"FUTURE_STATUS\"")
        val harness = Core2Harness(session()) { respondJson(answer) }
        val repository = repo(temporary.newFolder(), harness.client.links, FakeBackendGate(true))

        repository.refresh(matan)

        val mine = repository.content(matan).mine
        assertEquals(LinkCategory.OTHER, mine[2].category)
        assertEquals(SubjectLinkStatus.PUBLISHED, mine[3].status)
    }

    @Test fun `a FLOW save goes under its client id and reads back the flow name`() = runTest {
        val harness = Core2Harness(session()) { request ->
            if (request.method == HttpMethod.Get) respondJson(contractFixture("http/links/subjectLinks.json"))
            else respondJson(contractFixture("http/links/saveSubjectLink.json"))
        }
        val repository = repo(temporary.newFolder(), harness.client.links, FakeBackendGate(true))
        repository.refresh(matan)

        val saved = repository.save(matan, ownId, LinkCategory.MATERIALS, " https://example.org/links/new ", " Конспекты лекций ",
            LinkVisibility.FLOW, 7001)

        val request = harness.backendRequests.last()
        assertEquals(HttpMethod.Put, request.method)
        assertEquals("/api/links/$ownId", request.url.encodedPath)
        assertEquals(json(contractFixture("requests/SaveSubjectLinkRequest.json")), request.bodyJson())
        val link = (saved as AppResult.Success).value
        assertEquals(LinkVisibility.FLOW, link.visibility)
        assertEquals("ЛЕК МАТАН 3.1", link.audienceLabel)
        assertEquals(SubjectLinkStatus.PENDING, link.status)
        assertEquals(link, repository.content(matan).mine.first { it.id == ownId })
    }

    @Test fun `delete, pin, vote and report send Backend's routes and bodies`() = runTest {
        val harness = Core2Harness(session()) { request ->
            val path = request.url.encodedPath
            respondJson(contractFixture(when {
                request.method == HttpMethod.Get -> "http/links/subjectLinks.json"
                request.method == HttpMethod.Delete -> "http/links/deleteSubjectLink.json"
                path.endsWith("/pin") -> "http/links/pinSubjectLink.json"
                path.endsWith("/vote") -> "http/links/voteSubjectLink.json"
                path.endsWith("/report") -> "http/links/reportSubjectLink.json"
                else -> error("Unexpected route $path")
            }))
        }
        val repository = repo(temporary.newFolder(), harness.client.links, FakeBackendGate(true))
        repository.refresh(matan)

        assertEquals(AppResult.Success(Unit), repository.vote(matan, sharedId, 1))
        assertEquals(AppResult.Success(Unit), repository.report(matan, sharedId, ResourceReportReason.OTHER, " Синтетическая жалоба "))
        assertEquals(AppResult.Success(Unit), repository.pin(matan, ownId))
        assertEquals(AppResult.Success(Unit), repository.delete(matan, "00000000-0000-4000-8000-000000000102"))

        val sent = harness.backendRequests.drop(1)
        assertEquals(listOf(HttpMethod.Put to "/api/links/$sharedId/vote", HttpMethod.Post to "/api/links/$sharedId/report",
            HttpMethod.Put to "/api/subjects/501/links/pin", HttpMethod.Delete to "/api/links/00000000-0000-4000-8000-000000000102"),
            sent.map { it.method to it.url.encodedPath })
        assertEquals(json(contractFixture("requests/ResourceVoteRequest.json")), sent[0].bodyJson())
        assertEquals(json(contractFixture("requests/ModerationReportRequest.json")), sent[1].bodyJson())
        assertEquals(json(contractFixture("requests/PinSubjectLinkRequest.json")), sent[2].bodyJson())
        // The pin answer lists only link 102, which the deletion then removes.
        val snapshot = repository.content(matan)
        assertTrue(snapshot.mine.isEmpty())
        assertNull(snapshot.pinnedId)
    }

    @Test fun `restrictions are read with an unknown capability blocking like ALL`() = runTest {
        val answer = contractFixture("http/users/myRestrictions.json").replace("\"WRITE_REVIEWS\"", "\"FUTURE_CAPABILITY\"")
        val harness = Core2Harness(session()) { respondJson(answer) }
        val repository = repo(temporary.newFolder(), harness.client.links, FakeBackendGate(true))

        assertEquals(AppResult.Success(Unit), repository.refreshRestrictions())

        assertEquals("/api/users/me/restrictions", harness.backendRequests.single().url.encodedPath)
        val rows = repository.observeRestrictions().first()
        assertEquals(listOf(RestrictionCapability.SUBMIT_RESOURCES, RestrictionCapability.VOTE, RestrictionCapability.REPORT,
            RestrictionCapability.ALL, RestrictionCapability.ALL), rows.map { it.capability })
        assertEquals("Оскорбления", rows[3].reason)
        assertNull(rows[3].expiresAt)
        assertEquals(Instant.parse("2026-10-08T09:00:00Z"), rows[0].expiresAt)
        assertEquals("00000000-0000-4000-8000-000000000404", rows[3].id)
        assertTrue(rows.blocks(RestrictionCapability.WRITE_REVIEWS) != null)
    }

    @Test fun `each Backend error reaches the caller with the released meaning`() = runTest {
        // A null status is a connection that fails before any answer.
        val cases = listOf<Triple<HttpStatusCode?, String, AppError>>(
            Triple(HttpStatusCode.Unauthorized, contractFixture("errors/unauthorized.json"), AppError.Unauthorized),
            Triple(HttpStatusCode.Forbidden, errorEnvelope("restricted"), AppError.Restricted),
            Triple(HttpStatusCode.Forbidden, errorEnvelope("permission_denied"), AppError.Forbidden),
            Triple(HttpStatusCode.NotFound, errorEnvelope("not_found"), AppError.NotFound),
            Triple(null, "", AppError.Network),
        )
        for ((status, body, expected) in cases) {
            val harness = Core2Harness(session()) { request ->
                when {
                    !request.url.encodedPath.endsWith("/vote") -> respondJson(contractFixture("http/users/myRestrictions.json"))
                    status == null -> throw IOException("Synthetic network failure")
                    else -> respondJson(body, status)
                }
            }
            val repository = repo(temporary.newFolder(), harness.client.links, FakeBackendGate(true))

            assertEquals(AppResult.Failure(expected), repository.vote(matan, sharedId, 1))

            // Only a restriction refreshes the capabilities; the caller still sees the action's own error.
            val restrictionsRead = harness.backendRequests.any { it.url.encodedPath == "/api/users/me/restrictions" }
            assertEquals(expected.toString(), expected == AppError.Restricted, restrictionsRead)
            if (restrictionsRead) assertEquals(5, repository.observeRestrictions().first().size)
        }
        for (status in listOf(HttpStatusCode.Conflict, HttpStatusCode.InternalServerError)) {
            val harness = Core2Harness(session()) { respondJson(errorEnvelope("limit"), status) }
            val repository = repo(temporary.newFolder(), harness.client.links, FakeBackendGate(true))

            val result = repository.report(matan, sharedId, ResourceReportReason.SPAM, null)

            assertTrue(status.toString(), (result as AppResult.Failure).error is AppError.Unknown)
        }
    }

    @Test fun `an upload stops at a 401 and keeps the remaining links and pin local without failing the scope`() = runTest {
        val first = Uuid.random().toString(); val second = Uuid.random().toString()
        val services = FakeBackendGate(false); val folder = temporary.newFolder()
        val harness = Core2Harness(session()) { request ->
            if (request.url.encodedPath == "/api/links/$first") respondJson(savedAs(first))
            else respondJson(contractFixture("errors/unauthorized.json"), HttpStatusCode.Unauthorized)
        }
        val repository = repo(folder, harness.client.links, services)
        repository.save(matan, first, LinkCategory.TASKS, url, null, LinkVisibility.PRIVATE, null)
        repository.save(matan, second, LinkCategory.NOTES, url, null, LinkVisibility.PRIVATE, null)
        repository.pin(matan, second)

        services.optedIn.value = true
        assertEquals(AppResult.Failure(AppError.Unauthorized), repository.refresh(matan))

        assertEquals(listOf("/api/links/$first", "/api/links/$second"), harness.backendRequests.map { it.url.encodedPath })
        assertEquals(SubjectLinksState.Loading, repository.observe(matan).first())
        services.optedIn.value = false
        val kept = repo(folder, harness.client.links, services).content(matan)
        assertEquals(listOf(second), kept.mine.map { it.id })
        assertTrue(kept.mine.single().local)
        assertEquals(second, kept.pinnedId)
    }

    @Test fun `an upload skips a link Backend refuses and sends the others`() = runTest {
        val refused = Uuid.random().toString(); val accepted = Uuid.random().toString()
        val services = FakeBackendGate(false)
        val harness = Core2Harness(session()) { request ->
            when (request.url.encodedPath) {
                "/api/links/$refused" -> respondJson(errorEnvelope("permission_denied"), HttpStatusCode.Forbidden)
                "/api/links/$accepted" -> respondJson(savedAs(accepted))
                else -> respondJson(contractFixture("http/links/subjectLinks.json"))
            }
        }
        val repository = repo(temporary.newFolder(), harness.client.links, services)
        repository.save(matan, refused, LinkCategory.TASKS, url, null, LinkVisibility.PRIVATE, null)
        repository.save(matan, accepted, LinkCategory.NOTES, url, null, LinkVisibility.PRIVATE, null)

        services.optedIn.value = true
        assertEquals(AppResult.Success(Unit), repository.refresh(matan))

        assertEquals(listOf("/api/links/$refused", "/api/links/$accepted", "/api/subjects/501/links"),
            harness.backendRequests.map { it.url.encodedPath })
        assertEquals(json("""{"subjectId":501,"subjectName":"Математический анализ","periodKey":"2026-1","category":"NOTES",""" +
            """"url":"$url","visibility":"PRIVATE"}"""), harness.backendRequests[1].bodyJson())
        val mine = repository.content(matan).mine
        assertTrue(mine.single { it.id == refused }.local)
        assertTrue(mine.none { it.id == accepted })
    }

    @Test fun `the demo and a session without the opt-in send nothing`() = runTest {
        val harness = Core2Harness(session()) { request -> error("Unexpected request ${request.url}") }
        val demo = repo(temporary.newFolder(), harness.client.links, FakeBackendGate(true), FakeDemoMode(active = true))
        val optedOut = repo(temporary.newFolder(), harness.client.links, FakeBackendGate(false))

        for (repository in listOf(demo, optedOut)) {
            repository.refresh(matan)
            repository.refreshRestrictions()
            repository.save(matan, id, LinkCategory.TASKS, url, null, LinkVisibility.PRIVATE, null)
            repository.pin(matan, id)
            repository.vote(matan, sharedId, 1)
            repository.report(matan, sharedId, ResourceReportReason.SPAM, null)
            repository.delete(matan, id)
        }

        assertTrue(harness.requests.isEmpty())
        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), optedOut.vote(matan, sharedId, 1))
    }

    private fun savedAs(id: String) = contractFixture("http/links/saveSubjectLink.json").replace(ownId, id)

    private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

    private fun HttpRequestData.bodyJson(): JsonElement? = when (val content = body) {
        is TextContent -> content.text
        is OutgoingContent.ByteArrayContent -> content.bytes().decodeToString()
        else -> null
    }?.let(::json)

    private fun repo(folder: File, api: SubjectLinksApi, services: FakeBackendGate, demo: DemoMode = noDemo()) =
        SubjectLinksRepositoryImpl(SubjectLinksFileStore(folder.toOkioPath()), api, services, clock, demo, dispatchers)

    private suspend fun SubjectLinksRepositoryImpl.content(scope: ResourceScope): SubjectLinksSnapshot =
        (observe(scope).first() as SubjectLinksState.Content).snapshot

    /** An in-memory Backend for the repository's own rules; the goldens below go through the real client. */
    private inner class FakeSubjectLinksApi : SubjectLinksApi {
        val mine = linkedMapOf<Uuid, WireLink>()
        val shared = mutableListOf<WireLink>()
        val saved = mutableListOf<SaveSubjectLinkRequest>()
        val audiences = listOf(WireAudience(7101, "ФИЗ ПИИКТ 3", 1, 1), WireAudience(7103, " ФИЗ ПИИКТ 3.2.1 ", 2, 3))
        val calls = mutableListOf<String>()
        var pinnedId: Uuid? = null
        var failNext = false
        var pauseFetch = false
        val entered = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)

        override suspend fun subjectLinks(subjectId: Long, period: String): SubjectLinksResponse {
            record("subjectLinks")
            if (pauseFetch) { entered.complete(Unit); check(release.await(10, TimeUnit.SECONDS)) }
            return response()
        }

        override suspend fun saveSubjectLink(id: Uuid, request: SaveSubjectLinkRequest): WireLink {
            record("saveSubjectLink")
            saved += request
            return link(id, LinkCategory.valueOf(request.category.name), isMine = true, request).also { mine[id] = it }
        }

        override suspend fun deleteSubjectLink(id: Uuid) {
            record("deleteSubjectLink")
            mine.remove(id)
        }

        override suspend fun pinSubjectLink(subjectId: Long, request: PinSubjectLinkRequest): SubjectLinksResponse {
            record("pinSubjectLink")
            pinnedId = request.linkId
            return response()
        }

        override suspend fun voteSubjectLink(id: Uuid, request: ResourceVoteRequest): WireLink {
            record("voteSubjectLink")
            val index = shared.indexOfFirst { it.id == id }
            val link = shared[index]
            return link.copy(myVote = request.value, score = link.score - link.myVote + request.value).also { shared[index] = it }
        }

        override suspend fun reportSubjectLink(id: Uuid, request: ModerationReportRequest): WireLink =
            error("Unexpected report of $id")

        override suspend fun myRestrictions(): List<WireRestriction> {
            record("myRestrictions")
            return emptyList()
        }

        fun link(id: Uuid, category: LinkCategory, isMine: Boolean, request: SaveSubjectLinkRequest? = null) = WireLink(id, scope.subjectId,
            scope.subjectName, scope.periodKey, WireCategory.valueOf(category.name), request?.url ?: url, request?.title,
            request?.visibility ?: WireVisibility.ALL, request?.flowId,
            audiences.firstOrNull { it.flowId == request?.flowId }?.label,
            if (request?.visibility == WireVisibility.PRIVATE) WireStatus.PRIVATE else WireStatus.PUBLISHED, null,
            0, 0, isMine, false, null, now)

        private fun record(name: String) {
            calls += name
            if (failNext) { failNext = false; throw BackendException.Transport(IOException("Synthetic network failure")) }
        }

        private fun response() = SubjectLinksResponse(mine.values.toList(), shared.toList(), emptyList(), pinnedId, audiences, true)
    }
}
