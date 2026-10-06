package dev.alllexey.itmowidgets.feature.recordbook.data.bars

import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.testing.noDemo
import dev.alllexey.itmowidgets.feature.recordbook.CountingBarsSessionListener
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** The app's BARS seam over the real MyItmoApi 2.x client on a MockEngine ([BarsTestServer]). */
class BarsClientTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatchers = mainDispatcherRule.appDispatchers

    private val old = OLD_HEADER
    private val memory = MemoryBarsTokens()
    private val store = memory.store
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
    private val server = BarsTestServer()
    private val client = client()

    private fun client(demo: DemoMode = noDemo()): BarsClient {
        val renewal = BarsRenewal(silentLogin, backgroundLogin)
        return BarsClient(server.library(storage, renewal), renewal, storage, owner, listener, demo, dispatchers)
    }

    /** A client whose every `current_user` request fails before any answer, as for a backgrounded app on MIUI. */
    private fun offlineClient(): BarsClient {
        server.offline += "users/current_user"
        return client()
    }

    @Test fun `the demo session has no BARS and asks nothing`() = runTest {
        val demo = client(FakeDemoMode(active = true))

        assertEquals(AppResult.Failure(AppError.DemoUnavailable), demo.account { execute { getDisciplines(true) } })
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
    @Test fun `the storage never shows the saved header`() = runTest {
        store.install(123, old)
        storage.owner = 123
        assertEquals(old, storage.getAuthorization())
        assertFalse(storage.toString().contains(old))
    }
    @Test fun `missing session signs in silently before the first request`() = runTest {
        silentCodes += "synthetic-code"
        assertTrue(client.account { execute { getDisciplines(true) } } is AppResult.Success)
        assertEquals(1, silentLogin.requests)
        assertEquals(FRESH_HEADER, store.load(123))
        assertEquals(listOf(null, FRESH_HEADER, FRESH_HEADER), server.authorizations)
    }
    @Test fun `ended ITMO ID session never sends requests or uses MyITMO credentials`() = runTest {
        assertEquals(AppResult.Failure(AppError.Unauthorized), client.account { Unit })
        assertEquals(1, silentLogin.requests)
        assertEquals(0, server.requestCount)
        assertNull(store.load(123))
    }
    @Test fun `expired token is renewed once and the request retried`() = runTest {
        store.install(123, old)
        server.rejected = old
        silentCodes += "synthetic-code"
        assertTrue(client.account { execute { getDisciplines(true) } } is AppResult.Success)
        assertEquals(FRESH_HEADER, store.load(123))
        assertEquals(1, silentLogin.requests)
        server.rejected = FRESH_HEADER
        assertEquals(AppResult.Failure(AppError.Unauthorized), client.account { execute { getDisciplines(true) } })
        assertEquals(2, silentLogin.requests)
    }
    @Test fun `concurrent calls share a single renewal`() = runTest {
        store.install(123, old)
        server.rejected = old
        silentCodes += "synthetic-code"
        val result = client.account {
            coroutineScope { List(4) { async { execute { getDisciplines(true) } } }.awaitAll() }
        }
        assertTrue(result is AppResult.Success)
        assertEquals(1, silentLogin.requests)
        assertEquals(1, server.logins)
    }
    @Test fun `changed-account response clears the session`() = runTest {
        store.install(123, old)
        server.login = "999"
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
        assertEquals(listOf("current_year=2025/2026", "current_term=0"), server.settings)
        assertEquals("2025/2026" to 0, server.year to server.term)
        val requests = server.requestCount
        assertTrue(client.account { selectPeriod("2025/2026", autumn = false) } is AppResult.Success)
        assertEquals(2, server.settings.size)
        assertEquals(requests + 2, server.requestCount)
    }
    @Test fun `failed period update is not treated as successful selected data`() = runTest {
        store.install(123, old)
        server.ignoreWrites = true
        assertTrue(client.account { selectPeriod("2025/2026", autumn = false) } is AppResult.Failure)
    }
    @Test fun `login checks server identity before keeping a session`() = runTest {
        server.login = "999"
        assertEquals(AppResult.Failure(AppError.Forbidden), client.login("synthetic-code"))
        assertNull(store.load(123))
        server.login = "123"
        assertTrue(client.login("synthetic-code") is AppResult.Success)
        assertEquals(FRESH_HEADER, store.load(123))
    }
    @Test fun `network failure maps to a network error`() = runTest {
        store.install(123, old)
        server.offline += ""
        assertEquals(AppResult.Failure(AppError.Network), client.account { Unit })
    }
    @Test fun `background renews an expired session through cookies, not the WebView`() = runTest {
        store.install(123, old)
        server.rejected = old
        backgroundRenewals += BarsCookieRenewal.Code("synthetic-code")
        val result = client.backgroundAccount { execute { getDisciplines(true) } }
        assertTrue(result is BarsBackground.Success)
        assertEquals(FRESH_HEADER, store.load(123))
        assertEquals(1, backgroundLogin.requests)
        assertEquals(0, silentLogin.requests)
    }
    @Test fun `ended ITMO ID session in the background keeps the saved header`() = runTest {
        store.install(123, old)
        server.rejected = old
        backgroundRenewals += BarsCookieRenewal.SessionEnded
        assertEquals(BarsBackground.SessionEnded, client.backgroundAccount { execute { getDisciplines(true) } })
        assertEquals(old, store.load(123))
        assertEquals(0, silentLogin.requests)
    }
    @Test fun `network failure of the background renewal is a network failure`() = runTest {
        store.install(123, old)
        server.rejected = old
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
        server.rejected = old
        silentCodes += "synthetic-code"
        assertTrue(client.account { execute { getDisciplines(true) } } is AppResult.Success)
        assertEquals(1, silentLogin.requests)
        assertEquals(0, backgroundLogin.requests)
    }
    @Test fun `account change during a background block is unauthorized`() = runTest {
        store.install(123, old)
        val result = client.backgroundAccount {
            isu = 999
            execute { getDisciplines(true) }
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
        server.rejected = old
        backgroundRenewals += BarsCookieRenewal.SessionEnded
        assertEquals(BarsBackground.SessionEnded, client.backgroundAccount { execute { getDisciplines(true) } })
        assertEquals(2, listener.answers)

        server.login = "999"
        assertEquals(AppResult.Failure(AppError.Forbidden), client.login("synthetic-code"))
        assertEquals(2, listener.answers)
        server.login = "123"
        assertTrue(client.login("synthetic-code") is AppResult.Success)
        assertEquals(3, listener.answers)
    }
}
