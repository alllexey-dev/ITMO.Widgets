package dev.alllexey.itmowidgets.feature.recordbook.data.sheets

import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.testing.blockingIoAppDispatchers
import dev.alllexey.itmowidgets.core.testing.noDemo
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.testing.MutableClock
import dev.alllexey.itmowidgets.di.NetworkModule
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkEventKind
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.StudyHalf
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.RowSearch
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetChange
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetCheck
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetFixtures
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetInspection
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetScore
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetStatus
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetTotals
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.toKotlinInstant
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SheetScoresRepositoryImplTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** The fake network holds a request on a blocked thread; see [blockingIoAppDispatchers]. */
    private val dispatchers = blockingIoAppDispatchers(mainDispatcherRule.dispatcher)

    @get:Rule val temporary = TemporaryFolder()

    private val id = "1TestSheetIdForUnitTests_0123456789-abc"
    private val url = "https://docs.google.com/spreadsheets/d/$id/edit#gid=22"
    private val scope = ResourceScope(1, "Тестовый предмет", "2026-1")
    private val directory by lazy { File(temporary.root, "sheet_scores") }
    private val store get() = SheetScoresFileStore(directory.toOkioPath())
    private val clock = MutableClock(Instant.parse("2026-09-07T09:00:00Z"))
    private var isu: Int? = 123456
    private val users = object : CurrentUserProvider {
        override suspend fun getCurrentUser() = isu?.let { CurrentUser(it, "Тестов Тест Тестович", null) }
    }

    private val server = MockWebServer()
    private var tabsPage: () -> MockResponse = { html("tabs_htmlview.html") }
    private val tabs = mutableMapOf(
        "0" to { csvText("ИСУ,ФИО,Группа\n") },
        "11" to { csv("roster.csv") },
        "22" to { csv("grades_multiheader.csv") },
        "33" to { csv("name_only.csv") },
    )
    private val requests: MutableList<String> = Collections.synchronizedList(mutableListOf())
    /** When set, a tab download takes its answer, announces itself through [held] and waits on it. */
    @Volatile private var gate: CountDownLatch? = null
    @Volatile private var held = CountDownLatch(1)

    @Before fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl!!
                requests += request.path!!
                return when {
                    path.encodedPath.endsWith("/htmlview") -> tabsPage()
                    path.encodedPath.endsWith("/export") -> {
                        val response = tabs[path.queryParameter("gid")]?.invoke() ?: MockResponse().setResponseCode(404)
                        gate?.let { held.countDown(); it.await(10, TimeUnit.SECONDS) }
                        response
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After fun stop() {
        gate?.countDown()
        server.shutdown()
    }

    @Test
    fun `the demo shows its connected total and downloads nothing`() = runTest {
        val repository = repository(FakeDemoMode(active = true))

        val scores = repository.observe().first()
        val inspection = repository.inspect(url)

        assertEquals("64", scores.single().value)
        assertEquals(SheetInspection.Failed(SheetStatus.NETWORK), inspection)
        repository.disconnect(scope)
        assertEquals(SheetCheck(emptyList(), emptyList()), repository.check(StudyHalf(2026, 1)))
        assertTrue(requests.isEmpty())
        assertFalse(directory.exists())
    }

    private fun repository(demo: DemoMode = noDemo()) = SheetScoresRepositoryImpl(
        PublicSheetClient(NetworkModule.providePublicWebClient(), server.url("/"), demo, dispatchers), store, users, clock,
        FixedAcademicTime(), demo,
        dispatchers = dispatchers,
    )

    private fun html(name: String) = MockResponse().setHeader("Content-Type", "text/html; charset=utf-8").setBody(SheetFixtures.text(name))
    private fun csvText(text: String) = MockResponse().setHeader("Content-Type", "text/csv; charset=utf-8").setBody(text)
    private fun csv(name: String) = csvText(SheetFixtures.text(name))

    /** The grades tab with [transform] applied to its rows. */
    private fun grades(transform: (List<List<String>>) -> List<List<String>>): MockResponse =
        csvText(transform(SheetFixtures.csv("grades_multiheader.csv").rows).joinToString("\n") { row ->
            row.joinToString(",") { "\"" + it.replace("\"", "\"\"") + "\"" }
        })

    private fun withOwnTotal(value: String) = grades { rows -> rows.mapIndexed { i, row -> if (i == 5) row.take(11) + value + row.drop(12) else row } }

    private suspend fun SheetScoresRepositoryImpl.connectGrades(target: ResourceScope = scope): SheetScore {
        val ready = inspect(url) as SheetInspection.Ready
        val match = (ready.search as RowSearch.Found).matches.single { it.tab.gid == 22L }
        val tab = ready.workbook.tabs.single { it.tab.gid == 22L }
        val total = SheetTotals.detect(SheetTotals.cells(tab, match))!!
        assertEquals(AppResult.Success(Unit), connect(target, url, match, total))
        return observe().first().single { it.scope == target }
    }

    @Test fun `inspect downloads every tab, the link's tab first, and finds the own row`() = runTest {
        val ready = repository().inspect(url) as SheetInspection.Ready

        assertEquals(listOf(22L, 0L, 11L, 33L), ready.workbook.tabs.map { it.tab.gid })
        assertEquals(listOf("P3110", "All"), (ready.search as RowSearch.Found).matches.map { it.tab.name })
    }

    @Test fun `a sign-in page instead of the tab list is closed`() = runTest {
        tabsPage = { MockResponse().setResponseCode(302).setHeader("Location", "/v3/signin/identifier") }

        assertEquals(SheetInspection.Failed(SheetStatus.CLOSED), repository().inspect(url))
    }

    @Test fun `a lost tab fails the inspection as no connection`() = runTest {
        tabs["11"] = { MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST) }

        assertEquals(SheetInspection.Failed(SheetStatus.NETWORK), repository().inspect(url))
    }

    @Test fun `too large tabs are skipped and all of them are too large`() = runTest {
        val tooLarge = { csvText("a").setHeader("Content-Length", PublicSheetClient.MAX_BYTES + 1L) }
        tabs["33"] = tooLarge

        val ready = repository().inspect(url) as SheetInspection.Ready
        assertEquals(listOf(22L, 0L, 11L), ready.workbook.tabs.map { it.tab.gid })

        tabs.keys.toList().forEach { tabs[it] = tooLarge }
        assertEquals(SheetInspection.Failed(SheetStatus.TOO_LARGE), repository().inspect(url))
    }

    @Test fun `a connection is stored for the account with its value as the baseline`() = runTest {
        val repository = repository()

        val score = repository.connectGrades()

        assertEquals("66,3", score.value)
        assertEquals("66,3", score.baseline)
        assertTrue(score.tracked)
        assertEquals(SheetStatus.OK, score.status)
        assertEquals(22L, score.tabGid)
        assertEquals("ИТОГО баллов", score.column.headerPath)
        val stored = store.read()!!
        assertEquals(123456, stored.owner)
        assertEquals(listOf(score), stored.connections.map { it.toModel() })
    }

    @Test fun `refresh stores the new value and moves the baseline without news`() = runTest {
        val repository = repository()
        repository.connectGrades()
        tabs["22"] = { withOwnTotal("70") }
        clock.now += Duration.ofHours(1)

        repository.refresh(scope)

        val score = repository.observe().first().single()
        assertEquals("70", score.value)
        assertEquals("70", score.baseline)
        assertEquals(clock.instant().toKotlinInstant(), score.updatedAt)
        assertEquals(SheetCheck(emptyList(), emptyList()), repository.check(StudyHalf(2026, 1)))
    }

    @Test fun `refresh finds a moved row and keeps the value when the column or the network is gone`() = runTest {
        val repository = repository()
        val connected = repository.connectGrades()
        tabs["22"] = { grades { rows -> rows.take(4) + rows.drop(4).reversed() } }

        repository.refresh(scope)
        assertEquals("66,3", repository.observe().first().single().value)

        tabs["22"] = { grades { rows -> rows.map { it.take(10) } } }
        repository.refresh(scope)
        assertEquals(SheetStatus.COLUMN_NOT_FOUND, repository.observe().first().single().status)
        assertEquals("66,3", repository.observe().first().single().value)

        tabs["22"] = { MockResponse().setResponseCode(503) }
        clock.now += Duration.ofHours(1)
        repository.refresh(scope)
        val failed = repository.observe().first().single()
        assertEquals(SheetStatus.NETWORK, failed.status)
        assertEquals("66,3", failed.value)
        assertEquals(connected.updatedAt, failed.updatedAt)
    }

    @Test fun `a background check reports a changed total of the half-year only`() = runTest {
        val repository = repository()
        repository.connectGrades()
        val previous = ResourceScope(2, "Тестовый предмет прошлого года", "2025-2")
        repository.connectGrades(previous)
        tabs["22"] = { withOwnTotal("70") }
        requests.clear()

        val check = repository.check(StudyHalf(2026, 1))

        assertEquals(SheetCheck(listOf(SheetChange(scope, MarkEventKind.MARK_CHANGED)), emptyList()), check)
        assertEquals(1, requests.size)
        assertEquals("66,3", repository.observe().first().single { it.scope == previous }.value)
    }

    @Test fun `an untracked connection only takes the baseline and is tracked again`() = runTest {
        val repository = repository()
        repository.connectGrades()
        repository.untrack()
        assertFalse(repository.observe().first().single().tracked)
        tabs["22"] = { withOwnTotal("70") }

        assertEquals(SheetCheck(emptyList(), emptyList()), repository.check(StudyHalf(2026, 1)))
        assertTrue(repository.observe().first().single().tracked)

        tabs["22"] = { withOwnTotal("75") }
        assertEquals(listOf(SheetChange(scope, MarkEventKind.MARK_CHANGED)), repository.check(StudyHalf(2026, 1)).changes)
    }

    @Test fun `a network failure in the check is an error`() = runTest {
        val repository = repository()
        repository.connectGrades()
        tabs["22"] = { MockResponse().setResponseCode(503) }

        assertEquals(SheetCheck(emptyList(), listOf(AppError.Network)), repository.check(StudyHalf(2026, 1)))
    }

    @Test fun `a reading for a disconnected or replaced connection is dropped`() = runTest {
        val repository = repository()
        repository.connectGrades()
        tabs["22"] = { withOwnTotal("70") }
        gate = CountDownLatch(1)

        val refresh = async { repository.refresh(scope) }
        runCurrent()
        assertTrue(held.await(10, TimeUnit.SECONDS))
        repository.disconnect(scope)
        gate!!.countDown()
        refresh.await()
        assertEquals(emptyList<SheetScore>(), repository.observe().first())

        gate = null
        tabs["22"] = { csv("grades_multiheader.csv") }
        repository.connectGrades()
        tabs["22"] = { withOwnTotal("70") }
        held = CountDownLatch(1)
        val release = CountDownLatch(1)
        gate = release
        val again = async { repository.refresh(scope) }
        runCurrent()
        assertTrue(held.await(10, TimeUnit.SECONDS))
        gate = null
        tabs["22"] = { csv("grades_multiheader.csv") }
        clock.now += Duration.ofMinutes(1)
        val reconnected = repository.connectGrades()
        release.countDown()
        again.await()
        assertEquals(listOf(reconnected), repository.observe().first())
        assertEquals("66,3", store.read()!!.connections.single().value)
    }

    @Test fun `a session cleared during a download writes nothing`() = runTest {
        val repository = repository()
        repository.connectGrades()
        tabs["22"] = { withOwnTotal("70") }
        val release = CountDownLatch(1)
        gate = release

        val check = async { repository.check(StudyHalf(2026, 1)) }

        runCurrent()
        assertTrue(held.await(10, TimeUnit.SECONDS))
        repository.clearSessionData()
        release.countDown()

        assertEquals(emptyList<SheetChange>(), check.await().changes)
        assertFalse(File(directory, "state.json").exists())
        assertEquals(emptyList<SheetScore>(), repository.observe().first())
    }

    @Test fun `another account's file and a corrupt file give no connections`() = runTest {
        repository().connectGrades()
        isu = 654321

        assertEquals(emptyList<SheetScore>(), repository().observe().first())
        assertFalse(directory.exists())

        directory.mkdirs()
        File(directory, "state.json").writeText("{broken")
        assertEquals(emptyList<SheetScore>(), repository().observe().first())
    }

    @Test fun `another total keeps the connection time and takes its value as the baseline`() = runTest {
        val repository = repository()
        val connected = repository.connectGrades()
        clock.now += Duration.ofHours(1)
        val ready = repository.inspect(url) as SheetInspection.Ready
        val match = (ready.search as RowSearch.Found).matches.single { it.tab.gid == 22L }
        val grade = SheetTotals.cells(ready.workbook.tabs.single { it.tab.gid == 22L }, match).single { it.column == 12 }

        assertEquals(AppResult.Success(Unit), repository.changeTotal(scope, match, grade))

        val score = repository.observe().first().single()
        assertEquals("Оценка · 60-100", score.column.headerPath)
        assertEquals(12, score.column.index)
        assertEquals("5A", score.value)
        assertEquals("5A", score.baseline)
        assertEquals(connected.connectedAt, score.connectedAt)
        assertEquals(clock.instant().toKotlinInstant(), score.updatedAt)
        assertNull(ResourceScope(9, "x", "2026-1").let { s -> repository.observe().first().firstOrNull { it.scope == s } })
    }
}
