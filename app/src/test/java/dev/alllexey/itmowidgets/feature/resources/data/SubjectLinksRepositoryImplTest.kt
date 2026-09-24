package dev.alllexey.itmowidgets.feature.resources.data

import api.myitmo.MyItmo
import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.ItmoWidgetsImpl
import dev.alllexey.itmowidgets.core.model.ApiResponse
import dev.alllexey.itmowidgets.core.model.resources.LinkAudience as WireAudience
import dev.alllexey.itmowidgets.core.model.resources.PinSubjectLinkRequest
import dev.alllexey.itmowidgets.core.model.resources.ResourceVoteRequest
import dev.alllexey.itmowidgets.core.model.resources.SaveSubjectLinkRequest
import dev.alllexey.itmowidgets.core.model.resources.SubjectLinksResponse
import dev.alllexey.itmowidgets.core.model.resources.UserRestriction as WireRestriction
import dev.alllexey.itmowidgets.core.resources.LinkAudience
import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.LinkVisibility
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.resources.SubjectLinksSnapshot
import dev.alllexey.itmowidgets.core.resources.SubjectLinksState
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import java.io.File
import java.io.IOException
import java.lang.reflect.Proxy
import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import dev.alllexey.itmowidgets.core.model.resources.LinkCategory as WireCategory
import dev.alllexey.itmowidgets.core.model.resources.LinkVisibility as WireVisibility
import dev.alllexey.itmowidgets.core.model.resources.SubjectLink as WireLink
import dev.alllexey.itmowidgets.core.model.resources.SubjectLinkStatus as WireStatus

@OptIn(ExperimentalCoroutinesApi::class)
class SubjectLinksRepositoryImplTest {
    @get:Rule val temporary = TemporaryFolder()
    private val scope = ResourceScope(42, "Предмет", "2026-1")
    private val gson = ItmoWidgetsImpl(MyItmo()).gson
    private val now = OffsetDateTime.parse("2026-09-22T09:00:00Z")
    private val clock = Clock.fixed(now.toInstant(), ZoneOffset.UTC)
    private val id = UUID.randomUUID().toString()
    private val url = "https://github.com/example"

    @Test fun `without the opt-in links stay on the device and survive a restart`() = runTest {
        val api = FakeApi(); val services = Services(false); val folder = temporary.newFolder()
        val repository = repo(folder, api, services)

        assertTrue(repository.save(scope, id, LinkCategory.TASKS, url, "Лабы", LinkVisibility.PRIVATE, null) is AppResult.Success)
        assertTrue(repository.refresh(scope) is AppResult.Success)

        assertTrue(api.calls.isEmpty())
        val restored = repo(folder, api, services).content(scope).mine.single()
        assertEquals(id, restored.id)
        assertEquals("Лабы", restored.title)
        assertTrue(restored.local)
    }

    @Test fun `without the opt-in shared actions and non-private visibility need the connection`() = runTest {
        val repository = repo(temporary.newFolder(), FakeApi(), Services(false))
        val disabled = AppResult.Failure(AppError.CustomServicesDisabled)

        assertEquals(disabled, repository.save(scope, id, LinkCategory.TASKS, url, null, LinkVisibility.FLOW, 7103))
        assertEquals(disabled, repository.vote(scope, id, 1))
        assertEquals(disabled, repository.setSaved(scope, id, true))
        assertEquals(disabled, repository.pin(scope, id))
    }

    @Test fun `the first refresh with the opt-in uploads local links as private and drops them locally`() = runTest {
        val api = FakeApi(); val services = Services(false); val folder = temporary.newFolder()
        val repository = repo(folder, api, services)
        repository.save(scope, id, LinkCategory.TASKS, url, "Лабы", LinkVisibility.PRIVATE, null)
        repository.pin(scope, id)

        services.enabled.value = true
        assertTrue(repository.refresh(scope) is AppResult.Success)

        assertEquals(listOf("saveSubjectLink", "pinSubjectLink", "subjectLinks"), api.calls)
        assertEquals(WireVisibility.PRIVATE, api.saved.single().visibility)
        val link = repository.content(scope).mine.single()
        assertEquals(id, link.id)
        assertFalse(link.local)
        assertEquals(id, repository.content(scope).pinnedId)
        services.enabled.value = false
        assertTrue(repo(folder, api, services).content(scope).mine.isEmpty())
    }

    @Test fun `a flow link sends its flow and reads back the flow name and the audiences`() = runTest {
        val api = FakeApi(); val repository = repo(temporary.newFolder(), api, Services(true))
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
        val api = FakeApi(); val repository = repo(temporary.newFolder(), api, Services(true))
        repository.refresh(scope)

        assertTrue(repository.save(scope, id, LinkCategory.TASKS, url, null, LinkVisibility.FLOW, null) is AppResult.Failure)
        assertTrue(repository.save(scope, id, LinkCategory.TASKS, url, null, LinkVisibility.ALL, 7103) is AppResult.Failure)
        assertTrue(api.saved.isEmpty())
        assertTrue(repository.save(scope, id, LinkCategory.TASKS, url, null, LinkVisibility.ALL, null) is AppResult.Success)
        assertEquals(null, api.saved.single().flowId)
    }

    @Test fun `a network error keeps the cached snapshot`() = runTest {
        val api = FakeApi(); val repository = repo(temporary.newFolder(), api, Services(true))
        api.shared += api.link(UUID.randomUUID(), LinkCategory.SCORES, isMine = false)
        repository.refresh(scope)
        val before = repository.content(scope)

        api.failNext = true
        assertEquals(AppResult.Failure(AppError.Network), repository.refresh(scope))
        api.failNext = true
        assertEquals(AppResult.Failure(AppError.Network), repository.vote(scope, before.shared.single().id, 1))

        assertEquals(before, repository.content(scope))
    }

    @Test fun `an action answer updates the cached snapshot`() = runTest {
        val api = FakeApi(); val repository = repo(temporary.newFolder(), api, Services(true))
        val shared = api.link(UUID.randomUUID(), LinkCategory.SCORES, isMine = false)
        api.shared += shared
        repository.refresh(scope)

        assertTrue(repository.vote(scope, shared.id.toString(), 1) is AppResult.Success)

        val link = repository.content(scope).shared.single()
        assertEquals(1, link.myVote)
        assertEquals(1, link.score)
    }

    @Test fun `the first load of a scope that fails is an error`() = runTest {
        val api = FakeApi().apply { failNext = true }
        val repository = repo(temporary.newFolder(), api, Services(true))

        repository.refresh(scope)

        assertEquals(SubjectLinksState.Error(AppError.Network), repository.observe(scope).first())
    }

    @Test fun `session cleanup deletes the file and ignores a late answer`() = runTest {
        val api = FakeApi(); val services = Services(false); val folder = temporary.newFolder()
        val repository = repo(folder, api, services)
        repository.save(scope, id, LinkCategory.TASKS, url, null, LinkVisibility.PRIVATE, null)
        services.enabled.value = true
        api.pauseFetch = true

        val pending = async { repository.refresh(scope) }
        api.entered.await()
        val cleaning = async { repository.clearSessionData() }
        runCurrent()
        api.release.countDown()
        cleaning.await()

        assertEquals(AppResult.Failure(AppError.Unauthorized), pending.await())
        assertFalse(File(folder, "cache.json").exists())
        services.enabled.value = false
        assertTrue(repository.content(scope).mine.isEmpty())
        assertTrue(repo(folder, api, services).content(scope).mine.isEmpty())
    }

    @Test fun `a corrupted file is not replaced with an empty one`() = runTest {
        val folder = temporary.newFolder(); val original = "{unreadable"
        File(folder, "cache.json").writeText(original)
        val repository = repo(folder, FakeApi(), Services(false))

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
        val repository = repo(folder, FakeApi(), Services(false))

        val restored = repository.content(scope).mine.single()
        assertEquals(id, restored.id)
        assertTrue(restored.local)
        assertTrue(repository.content(scope).shared.isEmpty())
    }

    private fun repo(folder: File, api: FakeApi, services: Services) =
        SubjectLinksRepositoryImpl(SubjectLinksFileStore(folder, gson), api.instance, services, clock)

    private suspend fun SubjectLinksRepositoryImpl.content(scope: ResourceScope): SubjectLinksSnapshot =
        (observe(scope).first() as SubjectLinksState.Content).snapshot

    private class Services(on: Boolean) : CustomServicesRepository {
        val enabled = MutableStateFlow(on)
        override fun observeEnabled() = enabled
        override suspend fun isEnabled() = enabled.value
        override suspend fun setEnabled(enabled: Boolean) { this.enabled.value = enabled }
    }

    private inner class FakeApi {
        val mine = linkedMapOf<UUID, WireLink>()
        val shared = mutableListOf<WireLink>()
        val saved = mutableListOf<SaveSubjectLinkRequest>()
        val audiences = listOf(WireAudience(7101, "ФИЗ ПИИКТ 3", 1, 1), WireAudience(7103, " ФИЗ ПИИКТ 3.2.1 ", 2, 3))
        val calls = mutableListOf<String>()
        var pinnedId: UUID? = null
        var failNext = false
        var pauseFetch = false
        val entered = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)

        val instance = Proxy.newProxyInstance(ItmoWidgetsApi::class.java.classLoader, arrayOf(ItmoWidgetsApi::class.java)) { _, method, raw ->
            val args = raw.orEmpty()
            calls += method.name
            if (failNext) { failNext = false; throw IOException("Synthetic network failure") }
            val result: Any = when (method.name) {
                "subjectLinks" -> {
                    if (pauseFetch) { entered.complete(Unit); check(release.await(10, TimeUnit.SECONDS)) }
                    response()
                }
                "saveSubjectLink" -> {
                    val key = args[0] as UUID; val request = args[1] as SaveSubjectLinkRequest
                    saved += request
                    link(key, LinkCategory.valueOf(request.category.name), isMine = true, request).also { mine[key] = it }
                }
                "pinSubjectLink" -> { pinnedId = (args[1] as PinSubjectLinkRequest).linkId; response() }
                "voteSubjectLink" -> {
                    val key = args[0] as UUID; val value = (args[1] as ResourceVoteRequest).value
                    val index = shared.indexOfFirst { it.id == key }
                    shared[index].copy(myVote = value, score = shared[index].score - shared[index].myVote + value).also { shared[index] = it }
                }
                "myRestrictions" -> emptyList<WireRestriction>()
                else -> error("Unexpected API method: ${method.name}")
            }
            ApiResponse.success(result)
        } as ItmoWidgetsApi

        fun link(id: UUID, category: LinkCategory, isMine: Boolean, request: SaveSubjectLinkRequest? = null) = WireLink(id, scope.subjectId,
            scope.subjectName, scope.periodKey, WireCategory.valueOf(category.name), request?.url ?: url, request?.title,
            request?.visibility ?: WireVisibility.ALL, request?.flowId,
            audiences.firstOrNull { it.flowId == request?.flowId }?.label,
            if (request?.visibility == WireVisibility.PRIVATE) WireStatus.PRIVATE else WireStatus.PUBLISHED, null,
            0, 0, isMine, false, false, null, now)

        private fun response() = SubjectLinksResponse(mine.values.toList(), shared.toList(), emptyList(), pinnedId, audiences, true)
    }
}
