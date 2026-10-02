package dev.alllexey.itmowidgets.feature.recordbook.data.bars

import api.bars.Bars
import dev.alllexey.itmowidgets.core.testing.noDemo
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import api.bars.BarsConfiguration
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.storage.TokenCipher
import dev.alllexey.itmowidgets.feature.recordbook.CountingBarsSessionListener
import java.net.UnknownHostException
import java.util.Base64
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BarsClientTest {
    private val old = "Bearer synthetic-old-credential"
    private val fresh = "Bearer synthetic-fresh-credential"
    private class Memory : BarsTokenPersistence {
        var value: String? = null
        override fun read() = value
        override fun write(value: String?) { this.value = value }
    }
    private val memory = Memory()
    private val store = BarsTokenStore(memory, object : TokenCipher {
        override fun encrypt(value: String) = Base64.getEncoder().encodeToString(value.toByteArray())
        override fun decrypt(value: String) = String(Base64.getDecoder().decode(value))
    })
    private val storage = OwnerBoundBarsStorage(store)
    private var isu: Int? = 123
    private val owner = object : CurrentUserProvider {
        override suspend fun getCurrentUser() = isu?.let { CurrentUser(it, null, null) }
    }
    private val silentCodes = mutableListOf<String?>()
    private val silentLogin = object : BarsSilentLogin {
        var requests = 0
        override suspend fun authorizationCode(state: String): String? { requests++; return silentCodes.removeFirstOrNull() }
    }
    private val backgroundRenewals = mutableListOf<BarsCookieRenewal>()
    private val backgroundLogin = object : BarsBackgroundLogin {
        var requests = 0
        override suspend fun renew(state: String): BarsCookieRenewal {
            requests++
            return backgroundRenewals.removeFirstOrNull() ?: BarsCookieRenewal.SessionEnded
        }
    }
    private val listener = CountingBarsSessionListener()
    private val server = MockWebServer()
    private val fake = FakeBars()
    private lateinit var client: BarsClient

    @Before fun start() {
        server.dispatcher = fake
        server.start()
        val bars = Bars(configuration()).apply { storage = this@BarsClientTest.storage }
        client = BarsClient(bars, storage, owner, silentLogin, backgroundLogin, listener, noDemo())
    }
    @After fun stop() = server.shutdown()

    private fun configuration() = object : BarsConfiguration.Default() {
        override fun getHost() = server.hostName
        override fun getRestUrl() = server.url("/backend/rest/").toString()
    }

    /** A client whose every `current_user` request fails before any answer, as for a backgrounded app on MIUI. */
    private fun offlineClient(): BarsClient {
        val offline = OkHttpClient.Builder().addInterceptor { chain ->
            if (chain.request().url.encodedPath.contains("users/current_user")) throw UnknownHostException("Synthetic")
            chain.proceed(chain.request())
        }.build()
        val bars = Bars(configuration(), offline).apply { storage = this@BarsClientTest.storage }
        return BarsClient(bars, storage, owner, silentLogin, backgroundLogin, listener, noDemo())
    }

    @Test fun `the demo session has no BARS and asks nothing`() = runTest {
        val bars = Bars(configuration()).apply { storage = this@BarsClientTest.storage }
        val demo = BarsClient(bars, storage, owner, silentLogin, backgroundLogin, listener, FakeDemoMode(active = true))

        assertEquals(AppResult.Failure(AppError.DemoUnavailable), demo.account { execute { bars.api.getDisciplines(true) } })
        assertEquals(AppResult.Failure(AppError.DemoUnavailable), demo.login("synthetic-code"))
        assertEquals(0, server.requestCount)
        assertEquals(0, silentLogin.requests)
    }

    @Test fun `token file is account-bound and cleared`() {
        store.install(123, old)
        assertEquals(old, store.load(123))
        assertNull(store.load(999))
        assertFalse(memory.value!!.contains(old))
        store.clear()
        assertNull(store.load(123))
    }
    @Test fun `missing session signs in silently before the first request`() = runTest {
        silentCodes += "synthetic-code"
        assertTrue(client.account { execute { client.bars.api.getDisciplines(true) } } is AppResult.Success)
        assertEquals(1, silentLogin.requests)
        assertEquals(fake.issued, store.load(123))
        assertEquals(listOf(null, fake.issued, fake.issued), fake.authorizations)
    }
    @Test fun `ended ITMO ID session never sends requests or uses MyITMO credentials`() = runTest {
        assertEquals(AppResult.Failure(AppError.Unauthorized), client.account { Unit })
        assertEquals(1, silentLogin.requests)
        assertEquals(0, server.requestCount)
        assertNull(store.load(123))
    }
    @Test fun `expired token is renewed once and the request retried`() = runTest {
        store.install(123, old)
        fake.rejected = old
        silentCodes += "synthetic-code"
        assertTrue(client.account { execute { client.bars.api.getDisciplines(true) } } is AppResult.Success)
        assertEquals(fake.issued, store.load(123))
        assertEquals(1, silentLogin.requests)
        fake.rejected = fake.issued
        assertEquals(AppResult.Failure(AppError.Unauthorized), client.account { execute { client.bars.api.getDisciplines(true) } })
        assertEquals(2, silentLogin.requests)
    }
    @Test fun `concurrent calls share a single renewal`() = runTest {
        store.install(123, old)
        fake.rejected = old
        silentCodes += "synthetic-code"
        val result = client.account {
            coroutineScope { List(4) { async { execute { client.bars.api.getDisciplines(true) } } }.awaitAll() }
        }
        assertTrue(result is AppResult.Success)
        assertEquals(1, silentLogin.requests)
        assertEquals(1, fake.logins)
    }
    @Test fun `changed-account response clears the session`() = runTest {
        store.install(123, old)
        fake.login = "999"
        assertEquals(AppResult.Failure(AppError.Unauthorized), client.account { Unit })
        assertNull(store.load(123))
    }
    @Test fun `signed-out app user cannot use a stored BARS session`() = runTest {
        store.install(123, old)
        isu = null
        assertEquals(AppResult.Failure(AppError.Unauthorized), client.account { Unit })
        assertEquals(0, server.requestCount)
    }
    @Test fun `period uses spring zero, verifies the write and skips it when already selected`() = runTest {
        store.install(123, old)
        assertTrue(client.account { selectPeriod("2025/2026", autumn = false) } is AppResult.Success)
        assertEquals(listOf("current_year=2025/2026", "current_term=0"), fake.settings)
        assertEquals("2025/2026" to 0, fake.year to fake.term)
        val requests = server.requestCount
        assertTrue(client.account { selectPeriod("2025/2026", autumn = false) } is AppResult.Success)
        assertEquals(2, fake.settings.size)
        assertEquals(requests + 2, server.requestCount)
    }
    @Test fun `failed period update is not treated as successful selected data`() = runTest {
        store.install(123, old)
        fake.ignoreWrites = true
        assertTrue(client.account { selectPeriod("2025/2026", autumn = false) } is AppResult.Failure)
    }
    @Test fun `login checks server identity before keeping a session`() = runTest {
        fake.login = "999"
        assertEquals(AppResult.Failure(AppError.Forbidden), client.login("synthetic-code"))
        assertNull(store.load(123))
        fake.login = "123"
        assertTrue(client.login("synthetic-code") is AppResult.Success)
        assertEquals(fake.issued, store.load(123))
    }
    @Test fun `network failure maps to a network error`() = runTest {
        store.install(123, old)
        server.shutdown()
        assertEquals(AppResult.Failure(AppError.Network), client.account { Unit })
    }
    @Test fun `background renews an expired session through cookies, not the WebView`() = runTest {
        store.install(123, old)
        fake.rejected = old
        backgroundRenewals += BarsCookieRenewal.Code("synthetic-code")
        val result = client.backgroundAccount { execute { client.bars.api.getDisciplines(true) } }
        assertTrue(result is BarsBackground.Success)
        assertEquals(fake.issued, store.load(123))
        assertEquals(1, backgroundLogin.requests)
        assertEquals(0, silentLogin.requests)
    }
    @Test fun `ended ITMO ID session in the background keeps the saved header`() = runTest {
        store.install(123, old)
        fake.rejected = old
        backgroundRenewals += BarsCookieRenewal.SessionEnded
        assertEquals(BarsBackground.SessionEnded, client.backgroundAccount { execute { client.bars.api.getDisciplines(true) } })
        assertEquals(old, store.load(123))
        assertEquals(0, silentLogin.requests)
    }
    @Test fun `network failure of the background renewal is a network failure`() = runTest {
        store.install(123, old)
        fake.rejected = old
        backgroundRenewals += BarsCookieRenewal.Failed(AppError.Network)
        assertEquals(BarsBackground.Failure(AppError.Network), client.backgroundAccount { Unit })
        assertEquals(old, store.load(123))
    }
    @Test fun `no network before any answer is a network failure that renews nothing and keeps the header`() = runTest {
        store.install(123, old)
        val offline = offlineClient()

        assertEquals(BarsBackground.Failure(AppError.Network), offline.backgroundAccount { Unit })
        assertEquals(AppResult.Failure(AppError.Network), offline.account { Unit })

        assertEquals(0, backgroundLogin.requests)
        assertEquals(0, silentLogin.requests)
        assertEquals(old, store.load(123))
        assertEquals(0, listener.answers)
    }
    @Test fun `background without a saved session sends nothing and renews nothing`() = runTest {
        assertEquals(BarsBackground.NoSession, client.backgroundAccount { Unit })
        assertEquals(0, server.requestCount)
        assertEquals(0, backgroundLogin.requests)
        assertEquals(0, silentLogin.requests)
    }
    @Test fun `screens renew through the WebView after background blocks, failed ones included`() = runTest {
        store.install(123, old)
        assertTrue(client.backgroundAccount { Unit } is BarsBackground.Success)
        assertEquals(
            BarsBackground.Failure(AppError.Unknown()),
            client.backgroundAccount<Unit> { throw IllegalStateException("synthetic failure") }
        )
        fake.rejected = old
        silentCodes += "synthetic-code"
        assertTrue(client.account { execute { client.bars.api.getDisciplines(true) } } is AppResult.Success)
        assertEquals(1, silentLogin.requests)
        assertEquals(0, backgroundLogin.requests)
    }
    @Test fun `account change during a background block is unauthorized`() = runTest {
        store.install(123, old)
        val result = client.backgroundAccount {
            isu = 999
            execute { client.bars.api.getDisciplines(true) }
        }
        assertEquals(BarsBackground.Failure(AppError.Unauthorized), result)
    }
    @Test fun `the listener hears successful account, login and background answers only`() = runTest {
        store.install(123, old)
        assertTrue(client.account { Unit } is AppResult.Success)
        assertEquals(1, listener.answers)
        assertTrue(client.account<Unit> { throw IllegalStateException("synthetic failure") } is AppResult.Failure)
        assertEquals(1, listener.answers)

        assertTrue(client.backgroundAccount { Unit } is BarsBackground.Success)
        assertEquals(2, listener.answers)
        fake.rejected = old
        backgroundRenewals += BarsCookieRenewal.SessionEnded
        assertEquals(BarsBackground.SessionEnded, client.backgroundAccount { execute { client.bars.api.getDisciplines(true) } })
        assertEquals(2, listener.answers)

        fake.login = "999"
        assertEquals(AppResult.Failure(AppError.Forbidden), client.login("synthetic-code"))
        assertEquals(2, listener.answers)
        fake.login = "123"
        assertTrue(client.login("synthetic-code") is AppResult.Success)
        assertEquals(3, listener.answers)
    }

    /** Stateful BARS stand-in: identity, selected period, one issued session and one rejected header. */
    private inner class FakeBars : Dispatcher() {
        var login = "123"
        var year = "2026/2027"
        var term = 1
        var rejected: String? = null
        var ignoreWrites = false
        var logins = 0
        val issued get() = fresh
        val settings = mutableListOf<String>()
        val authorizations = mutableListOf<String?>()
        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.path.orEmpty()
            val authorization = request.getHeader("Authorization")
            authorizations += authorization
            if (path.startsWith("/backend/rest/login")) { logins++; return MockResponse().setHeader("authorization", issued) }
            if (authorization == null || authorization == rejected) return MockResponse().setResponseCode(401)
            return when {
                path.startsWith("/backend/rest/users/current_user") -> MockResponse().setBody(
                    "{\"id\":1,\"login\":\"$login\",\"selected_year\":\"$year\",\"selected_term\":$term,\"user_roles\":[],\"personal_config\":[]}")
                path.startsWith("/backend/rest/config/personal") -> {
                    val body = request.body.readUtf8()
                    val name = Regex("\"name\":\"([^\"]+)\"").find(body)!!.groupValues[1]
                    val value = Regex("\"value\":\"([^\"]+)\"").find(body)!!.groupValues[1]
                    settings += "$name=$value"
                    if (!ignoreWrites) { if (name == "current_year") year = value else term = value.toInt() }
                    MockResponse().setBody(body)
                }
                else -> MockResponse().setBody("[]")
            }
        }
    }
}
