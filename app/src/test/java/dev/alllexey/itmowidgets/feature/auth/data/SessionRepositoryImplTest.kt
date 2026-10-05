package dev.alllexey.itmowidgets.feature.auth.data

import dev.alllexey.itmoapi.itmoid.TokenSet
import dev.alllexey.itmoapi.itmoid.TokenStorage
import dev.alllexey.itmowidgets.core.demo.DemoPeople
import dev.alllexey.itmowidgets.core.network.MyItmoClientFactory
import dev.alllexey.itmowidgets.core.notification.FcmTokenSync
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.BackendDeviceSession
import dev.alllexey.itmowidgets.core.session.BackendIdentitySync
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.session.SessionLifecycleEffects
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.core.storage.DemoPreferences
import dev.alllexey.itmowidgets.core.storage.MyItmoStorage
import dev.alllexey.itmowidgets.core.storage.TokenCipher
import dev.alllexey.itmowidgets.core.testing.FakeSessionDataCleaner
import dev.alllexey.itmowidgets.core.testing.InMemoryPreferencesDataStore
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.testing.RecordingAppLog
import dev.alllexey.itmowidgets.core.testing.RecordingDiagnostics
import dev.alllexey.itmowidgets.testkit.FakeClock
import dev.alllexey.itmowidgets.testkit.bodyText
import dev.alllexey.itmowidgets.testkit.respondJson
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import java.io.File
import java.io.IOException
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The session over a real `MyItmoStorage` and the MyItmoApi 2.x client, with ITMO.ID answered by a MockEngine. */
class SessionRepositoryImplTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val dispatchers = mainDispatcherRule.appDispatchers
    private val clock = FakeClock(Instant.parse("2026-07-24T00:00:00Z"))

    @Test
    fun `initializes as signed out without a refresh token`() = runTest {
        val fixture = fixture(session = null)

        fixture.repository.initialize()

        assertEquals(SessionState.SignedOut, fixture.repository.state.value)
        assertEquals(0, fixture.identitySync.requests)
    }

    @Test
    fun `initializes as reauthentication required for an expired token`() = runTest {
        val fixture = fixture(session = stored(refreshExpiresAt = clock.now() - 1.seconds))

        fixture.repository.initialize()

        assertEquals(SessionState.ReauthenticationRequired, fixture.repository.state.value)
        assertTrue(fixture.requests.isEmpty())
    }

    @Test
    fun `a refresh-token-only state needs a new sign-in like 2_2`() = runTest {
        val fixture = fixture(session = null)
        fixture.storage.replaceWithRefreshToken("stored-refresh")

        fixture.repository.initialize()

        assertEquals(SessionState.ReauthenticationRequired, fixture.repository.state.value)
    }

    @Test
    fun `restores the local user without requiring a network request`() = runTest {
        val user = CurrentUser(123456, "Иванов Иван", null)
        val fixture = fixture(session = stored(), currentUser = user)

        fixture.repository.initialize()

        assertEquals(SessionState.SignedIn(user), fixture.repository.state.value)
        assertEquals(1, fixture.identitySync.requests)
        assertEquals(1, fixture.deviceSession.registerRequests)
        assertTrue(fixture.requests.isEmpty())
    }

    @Test
    fun `validates a manual token with one refresh before replacing the current session`() = runTest {
        val fixture = fixture(session = stored()) { respondJson(TOKEN_RESPONSE) }

        val result = fixture.repository.signInWithRefreshToken("  candidate-token  ")

        assertEquals(AppResult.Success(Unit), result)
        val form = fixture.requests.single().bodyText()
        assertTrue(form, "grant_type=refresh_token" in form && "refresh_token=candidate-token" in form)
        fixture.assertStoredTokens()
        assertEquals(1, fixture.cleaner.requests)
        assertEquals(listOf("prepare", "signed-in"), fixture.effects.events)
        assertTrue(fixture.repository.state.value is SessionState.SignedIn)
    }

    @Test
    fun `keeps the current session when manual token validation has a network error`() = runTest {
        val fixture = fixture(session = stored()) { throw IOException("offline") }

        val result = fixture.repository.signInWithRefreshToken("candidate-token")

        assertEquals(AppResult.Failure(AppError.Network), result)
        fixture.assertSessionKept()
    }

    @Test
    fun `a rejected manual token is unauthorized and keeps the current session`() = runTest {
        val fixture = fixture(session = stored()) {
            respondJson("""{"error":"invalid_grant"}""", HttpStatusCode.BadRequest)
        }

        val result = fixture.repository.signInWithRefreshToken("candidate-token")

        assertEquals(AppResult.Failure(AppError.Unauthorized), result)
        fixture.assertSessionKept()
    }

    @Test
    fun `an ITMO_ID 5xx is a retriable error that keeps the current session`() = runTest {
        val fixture = fixture(session = stored()) { respondJson("{}", HttpStatusCode.BadGateway) }

        val result = fixture.repository.signInWithRefreshToken("candidate-token")

        assertTrue(result.toString(), (result as AppResult.Failure).error is AppError.Unknown)
        fixture.assertSessionKept()
    }

    @Test
    fun `rejects an incomplete interactive token response`() = runTest {
        val fixture = fixture(session = null)

        val result = fixture.repository.completeItmoIdLogin("{\"access_token\":\"only\"}")

        assertEquals(AppResult.Failure(AppError.Unauthorized), result)
        assertEquals(0, fixture.cleaner.requests)
    }

    @Test
    fun `accepts a complete Keycloak token response with fields the app does not read`() = runTest {
        val fixture = fixture(session = null)

        val result = fixture.repository.completeItmoIdLogin(
            """{"access_token":" access ","expires_in":300,"refresh_expires_in":600,"refresh_token":"refresh",""" +
                """"token_type":"Bearer","id_token":"header.payload.signature","not-before-policy":0,""" +
                """"session_state":"synthetic-session","scope":"openid profile"}"""
        )

        assertEquals(AppResult.Success(Unit), result)
        fixture.assertStoredTokens()
        assertEquals(1, fixture.cleaner.requests)
        assertTrue(fixture.requests.isEmpty())
    }

    @Test
    fun `rejects token responses without a token, with null or invalid expirations, or that are not an object`() = runTest {
        val complete = mapOf(
            "access_token" to "\"access\"", "expires_in" to "300", "refresh_token" to "\"refresh\"",
            "refresh_expires_in" to "600", "id_token" to "\"header.payload.signature\""
        )
        fun response(fields: Map<String, String>) = fields.entries.joinToString(",", "{", "}") { "\"${it.key}\":${it.value}" }
        val responses = complete.keys.map { key -> response(complete - key) } +
            complete.keys.map { key -> response(complete + (key to "null")) } +
            listOf(
                response(complete + ("id_token" to "\"  \"")),
                response(complete + ("expires_in" to "0")),
                response(complete + ("refresh_expires_in" to "-1")),
                "null", "[]", "not json", "{\"access_token\":\"only\"} trailing"
            )
        for (body in responses) {
            val fixture = fixture(session = null)

            assertEquals(body, AppResult.Failure(AppError.Unauthorized), fixture.repository.completeItmoIdLogin(body))
            assertEquals(body, 0, fixture.cleaner.requests)
        }
    }

    @Test
    fun `unregisters device and clears private data before tokens on logout`() = runTest {
        val order = mutableListOf<String>()
        val fixture = fixture(session = stored(), order = order)
        fixture.cleaner.onClear = {
            assertEquals(SessionState.SigningOut, fixture.repository.state.value)
        }

        fixture.repository.signOut()

        assertEquals(
            listOf("unregister", "prepare", "clean", "tokens", "signed-out"),
            order
        )
        assertEquals(SessionState.SignedOut, fixture.repository.state.value)
        assertFalse(fixture.storage.hasRefreshToken())
    }

    @Test
    fun `local logout succeeds when backend is unavailable`() = runTest {
        val fixture = fixture(
            session = stored(),
            unregisterError = IOException("offline")
        )

        fixture.repository.signOut()

        assertEquals(SessionState.SignedOut, fixture.repository.state.value)
        assertNull(fixture.storage.read())
    }

    @Test
    fun `starts the demo without tokens, Backend, background work or ITMO requests`() = runTest {
        val fixture = fixture(session = stored())

        fixture.repository.startDemo()

        assertEquals(SessionState.SignedIn(DemoPeople.ME, demo = true), fixture.repository.state.value)
        assertEquals(1, fixture.cleaner.requests)
        assertEquals(listOf("prepare"), fixture.effects.events)
        assertEquals(0, fixture.identitySync.requests)
        assertEquals(0, fixture.tokenSync.requests)
        assertEquals(0, fixture.deviceSession.registerRequests)
        assertEquals(0, fixture.deviceSession.unregisterRequests)
        assertTrue(fixture.settings.getDemoActive())
        assertFalse(fixture.storage.hasRefreshToken())
        assertTrue(fixture.requests.isEmpty())
    }

    @Test
    fun `initializes as the demo without checking a token`() = runTest {
        val fixture = fixture(session = stored(refreshExpiresAt = clock.now() - 1.seconds))
        fixture.settings.setDemoActive(true)

        fixture.repository.initialize()

        assertEquals(SessionState.SignedIn(DemoPeople.ME, demo = true), fixture.repository.state.value)
        assertEquals(0, fixture.identitySync.requests)
        assertEquals(0, fixture.deviceSession.registerRequests)
        assertTrue(fixture.requests.isEmpty())
    }

    @Test
    fun `signing out of the demo keeps the device registration alone`() = runTest {
        val order = mutableListOf<String>()
        val fixture = fixture(session = null, order = order)
        fixture.repository.startDemo()
        order.clear()

        fixture.repository.signOut()

        assertEquals(listOf("clean", "signed-out"), order)
        assertEquals(0, fixture.deviceSession.unregisterRequests)
        assertFalse(fixture.settings.getDemoActive())
        assertEquals(SessionState.SignedOut, fixture.repository.state.value)
        assertTrue(fixture.requests.isEmpty())
    }

    @Test
    fun `a real sign-in ends a leftover demo flag`() = runTest {
        val fixture = fixture(session = null) { respondJson(TOKEN_RESPONSE) }
        fixture.settings.setDemoActive(true)

        fixture.repository.signInWithRefreshToken("candidate-token")

        assertFalse(fixture.settings.getDemoActive())
        assertEquals(false, (fixture.repository.state.value as SessionState.SignedIn).demo)
    }

    private suspend fun fixture(
        session: TokenSet?,
        currentUser: CurrentUser? = null,
        unregisterError: Exception? = null,
        order: MutableList<String> = mutableListOf(),
        answer: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = {
            throw AssertionError("Unexpected request ${it.url}")
        }
    ): Fixture {
        val storage = MyItmoStorage(
            tokenFile = File(folder.newFolder(), "myitmo_tokens.enc"),
            tokenCipher = PlainTokenCipher,
            clock = java.time.Clock.fixed(java.time.Instant.parse("2026-07-24T00:00:00Z"), ZoneOffset.UTC),
            log = RecordingAppLog()
        )
        storage.write(session)
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            requests += request
            answer(request)
        }
        val client = MyItmoClientFactory.create(
            storage = OrderedTokenStorage(storage, order),
            engine = engine,
            clock = clock
        )
        val cleaner = FakeSessionDataCleaner(order)
        val effects = FakeEffects(order)
        val identitySync = FakeIdentitySync()
        val deviceSession = FakeDeviceSession(order, unregisterError)
        val settings = DemoPreferences(InMemoryPreferencesDataStore())
        val tokenSync = FakeTokenSync()
        val transitions = SessionTransitions(
            myItmo = client,
            currentUserProvider = object : CurrentUserProvider {
                override suspend fun getCurrentUser(): CurrentUser? = currentUser
            },
            dataCleaners = { setOf(cleaner) },
            lifecycleEffects = effects,
            backendIdentitySync = identitySync,
            backendDeviceSession = deviceSession,
            fcmTokenSync = tokenSync,
            diagnostics = RecordingDiagnostics(),
            demoPreferences = settings,
            dispatchers = dispatchers
        )
        val repository = SessionRepositoryImpl(
            tokenStore = storage,
            myItmo = client,
            clock = clock,
            transitions = transitions,
            demo = DataStoreDemoMode(settings),
            dispatchers = dispatchers
        )
        return Fixture(
            repository,
            storage,
            requests,
            cleaner,
            effects,
            identitySync,
            deviceSession,
            settings,
            tokenSync
        )
    }

    private fun stored(refreshExpiresAt: Instant = clock.now() + 30.days) = TokenSet(
        accessToken = "stored-access",
        accessExpiresAt = clock.now() + 300.seconds,
        refreshToken = "stored-refresh",
        refreshExpiresAt = refreshExpiresAt,
        idToken = "stored.id.token"
    )

    private inner class Fixture(
        val repository: SessionRepositoryImpl,
        val storage: MyItmoStorage,
        val requests: List<HttpRequestData>,
        val cleaner: FakeSessionDataCleaner,
        val effects: FakeEffects,
        val identitySync: FakeIdentitySync,
        val deviceSession: FakeDeviceSession,
        val settings: DemoPreferences,
        val tokenSync: FakeTokenSync
    ) {
        /** [TOKEN_RESPONSE] with its lifetimes counted from the test clock, as 2.2 stored them. */
        fun assertStoredTokens() {
            assertEquals("access", storage.getAccessToken())
            assertEquals((clock.now() + 300.seconds).toEpochMilliseconds(), storage.getAccessExpiresAt())
            assertEquals("refresh", storage.getRefreshToken())
            assertEquals((clock.now() + 600.seconds).toEpochMilliseconds(), storage.getRefreshExpiresAt())
            assertEquals("header.payload.signature", storage.getIdToken())
        }

        fun assertSessionKept() {
            assertEquals(0, cleaner.requests)
            assertEquals("stored-access", storage.getAccessToken())
            assertEquals("stored-refresh", storage.getRefreshToken())
            assertTrue(effects.events.isEmpty())
        }
    }

    /** Records where in [order] the session's tokens are cleared. */
    private class OrderedTokenStorage(
        private val delegate: TokenStorage,
        private val order: MutableList<String>
    ) : TokenStorage {
        override suspend fun read(): TokenSet? = delegate.read()

        override suspend fun write(tokens: TokenSet?) {
            if (tokens == null) order += "tokens"
            delegate.write(tokens)
        }
    }

    private object PlainTokenCipher : TokenCipher {
        override fun encrypt(value: String): String = value

        override fun decrypt(value: String): String = value
    }

    private class FakeTokenSync : FcmTokenSync {
        var requests = 0

        override suspend fun sync() {
            requests += 1
        }
    }

    private class FakeEffects(
        private val order: MutableList<String>
    ) : SessionLifecycleEffects {
        val events = mutableListOf<String>()

        override suspend fun prepareForSessionChange() {
            events += "prepare"
            order += "prepare"
        }

        override suspend fun onSignedIn() {
            events += "signed-in"
        }

        override suspend fun onSignedOut() {
            events += "signed-out"
            order += "signed-out"
        }
    }

    private class FakeIdentitySync : BackendIdentitySync {
        var requests = 0

        override suspend fun sync(scheduleRetry: Boolean): Boolean {
            requests += 1
            return true
        }
    }

    private class FakeDeviceSession(
        private val order: MutableList<String>,
        private val unregisterError: Exception?
    ) : BackendDeviceSession {
        var registerRequests = 0

        override suspend fun registerCurrentDevice() {
            registerRequests += 1
        }

        var unregisterRequests = 0

        override suspend fun unregisterCurrentDevice() {
            unregisterRequests += 1
            order += "unregister"
            unregisterError?.let { throw it }
        }
    }

    private companion object {
        const val TOKEN_RESPONSE = """{"access_token":"access","expires_in":300,"refresh_token":"refresh",""" +
            """"refresh_expires_in":600,"id_token":"header.payload.signature","session_state":"synthetic"}"""
    }
}
