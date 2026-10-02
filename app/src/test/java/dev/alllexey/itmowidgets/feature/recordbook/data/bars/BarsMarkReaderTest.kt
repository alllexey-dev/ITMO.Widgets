package dev.alllexey.itmowidgets.feature.recordbook.data.bars

import api.bars.Bars
import dev.alllexey.itmowidgets.core.testing.noDemo
import api.bars.BarsConfiguration
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.storage.TokenCipher
import dev.alllexey.itmowidgets.feature.recordbook.CountingBarsSessionListener
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.BarsCheckpointMark
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.StudyHalf
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BarsMarkReaderTest {
    private val old = "Bearer synthetic-old-credential"
    private val memory = object : BarsTokenPersistence {
        var value: String? = null
        override fun read() = value
        override fun write(value: String?) { this.value = value }
    }
    private val store = BarsTokenStore(memory, object : TokenCipher {
        override fun encrypt(value: String) = value
        override fun decrypt(value: String) = value
    })
    private val storage = OwnerBoundBarsStorage(store)
    private val owner = object : CurrentUserProvider {
        override suspend fun getCurrentUser() = CurrentUser(123, null, null)
    }
    private val silentLogin = object : BarsSilentLogin {
        override suspend fun authorizationCode(state: String): String? = null
    }
    private val renewals = mutableListOf<BarsCookieRenewal>()
    private val backgroundLogin = object : BarsBackgroundLogin {
        var requests = 0
        override suspend fun renew(state: String): BarsCookieRenewal {
            requests++
            return renewals.removeFirstOrNull() ?: BarsCookieRenewal.SessionEnded
        }
    }
    private val server = MockWebServer()
    private val fake = FakeJournals()
    private lateinit var reader: BarsMarkReader

    @Before fun start() {
        server.dispatcher = fake
        server.start()
        val bars = Bars(object : BarsConfiguration.Default() {
            override fun getHost() = server.hostName
            override fun getRestUrl() = server.url("/backend/rest/").toString()
        }).apply { storage = this@BarsMarkReaderTest.storage }
        reader = BarsMarkReader(BarsClient(bars, storage, owner, silentLogin, backgroundLogin, CountingBarsSessionListener(), noDemo()))
    }

    @After fun stop() = server.shutdown()

    @Test fun `the half is selected and every journal becomes a plan of own marks`() = runTest {
        store.install(123, old)
        fake.year = "2026/2027"
        fake.term = 1

        val read = reader.read(HALF) as BarsMarkRead.Journals

        assertEquals(0, read.skipped)
        assertEquals(listOf(1L, 2L), read.plans.map { it.planId })
        val first = read.plans.first()
        assertEquals("Тестовый предмет 1", first.name)
        assertEquals("flow", first.type)
        assertEquals("7", first.identifier)
        assertEquals(listOf(BarsCheckpointMark(10, 7.5, false)), first.marks)
        assertEquals(7.5, first.score!!, 0.0)
        assertEquals(emptyList<String>(), fake.settings)
    }

    @Test fun `the period the user had is selected again afterwards`() = runTest {
        store.install(123, old)
        fake.year = "2025/2026"
        fake.term = 0

        assertTrue(reader.read(HALF) is BarsMarkRead.Journals)

        assertEquals(
            listOf("current_year=2026/2027", "current_term=1", "current_year=2025/2026", "current_term=0"),
            fake.settings
        )
        assertEquals("2025/2026" to 0, fake.year to fake.term)
    }

    @Test fun `a plan the mapper rejects is skipped and the rest is read`() = runTest {
        store.install(123, old)
        fake.courseProjects += 2L

        val read = reader.read(HALF) as BarsMarkRead.Journals

        assertEquals(1, read.skipped)
        assertEquals(listOf(1L), read.plans.map { it.planId })
    }

    @Test fun `without a saved session nothing is requested`() = runTest {
        assertEquals(BarsMarkRead.NoSession, reader.read(HALF))
        assertEquals(0, server.requestCount)
        assertEquals(0, backgroundLogin.requests)
    }

    @Test fun `an ended ITMO ID session is reported as such`() = runTest {
        store.install(123, old)
        fake.rejected = old
        renewals += BarsCookieRenewal.SessionEnded

        assertEquals(BarsMarkRead.SessionEnded, reader.read(HALF))
        assertEquals(1, backgroundLogin.requests)
    }

    @Test fun `a lost journal fails the read and the period is still given back`() = runTest {
        store.install(123, old)
        fake.year = "2025/2026"
        fake.term = 0
        fake.disconnected += 2L

        assertEquals(BarsMarkRead.Failure(AppError.Network), reader.read(HALF))
        assertEquals(listOf("current_year=2025/2026", "current_term=0"), fake.settings.takeLast(2))
    }

    @Test fun `the network lost on the second journal of three fails the whole read`() = runTest {
        store.install(123, old)
        fake.plans = listOf(1L, 2L, 3L)
        fake.disconnected += 2L

        val read = reader.read(HALF)

        assertEquals(BarsMarkRead.Failure(AppError.Network), read)
        assertEquals(0, backgroundLogin.requests)
    }

    private inner class FakeJournals : Dispatcher() {
        var year = "2026/2027"
        var term = 1
        var rejected: String? = null
        val settings = CopyOnWriteArrayList<String>()
        val courseProjects = mutableSetOf<Long>()
        val disconnected = mutableSetOf<Long>()
        var plans = listOf(1L, 2L)

        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.path.orEmpty()
            val authorization = request.getHeader("Authorization")
            if (authorization == null || authorization == rejected) return MockResponse().setResponseCode(401)
            val journal = Regex("/backend/rest/marks/(\\d+)/flow/7/student").find(path)?.groupValues?.get(1)?.toLong()
            return when {
                path.startsWith("/backend/rest/users/current_user") -> MockResponse().setBody(
                    "{\"id\":1,\"login\":\"123\",\"selected_year\":\"$year\",\"selected_term\":$term,\"user_roles\":[],\"personal_config\":[]}")
                path.startsWith("/backend/rest/config/personal") -> {
                    val body = request.body.readUtf8()
                    val name = Regex("\"name\":\"([^\"]+)\"").find(body)!!.groupValues[1]
                    val value = Regex("\"value\":\"([^\"]+)\"").find(body)!!.groupValues[1]
                    settings += "$name=$value"
                    if (name == "current_year") year = value else term = value.toInt()
                    MockResponse().setBody(body)
                }
                path.startsWith("/backend/rest/journal/disciplines") ->
                    MockResponse().setBody("[{\"id\":90,\"name\":\"Тестовый предмет\",\"checkpoint_plan_ids\":${plans.joinToString(",", "[", "]")}}]")
                path.startsWith("/backend/rest/journal/groups-and-flows") ->
                    MockResponse().setBody("[{\"type\":\"flow\",\"name\":\"Поток\",\"identifier\":\"7\",\"checkpoint_plan_ids\":${plans.joinToString(",", "[", "]")}}]")
                journal != null && journal in disconnected -> MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)
                journal != null -> MockResponse().setBody(journal(journal, courseProject = journal in courseProjects))
                else -> MockResponse().setResponseCode(404)
            }
        }

        private fun journal(planId: Long, courseProject: Boolean): String {
            val checkpoint = planId * 10
            return """{"students":[{"student_login":"123","marks":{"regular":[{"id":1,"checkpoint_id":$checkpoint,
                "checkpoint_plan_id":$planId,"mark":7.5,"is_absent":false}],"total":7.5,"active_approvals":[]}}],
                "headers":{"plan":{"id":$planId,"year":"2026/2027","discipline":{"id":${90 + planId},"name":"Тестовый предмет $planId"},
                "regular_checkpoints":[{"id":$checkpoint,"name":"Работа","type":"Тест","min_grade":1.0,"max_grade":10.0,"key":true,
                "sub_checkpoints":[]}],"has_course_project":$courseProject},"type":"flow","identifier":"7"}}"""
        }
    }

    private companion object {
        val HALF = StudyHalf(2026, 1)
    }
}
