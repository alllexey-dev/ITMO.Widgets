package dev.alllexey.itmowidgets.feature.auth.data

import dev.alllexey.itmoapi.itmoid.TokenSet
import dev.alllexey.itmoapi.itmoid.TokenStorage
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoPeople
import dev.alllexey.itmowidgets.core.network.MyItmoClientFactory
import dev.alllexey.itmowidgets.core.notification.FcmTokenSync
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.BackendDeviceSession
import dev.alllexey.itmowidgets.core.session.BackendIdentitySync
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.session.SessionLifecycleEffects
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.core.storage.DemoPreferences
import dev.alllexey.itmowidgets.core.testing.FakeSessionDataCleaner
import dev.alllexey.itmowidgets.core.testing.InMemoryPreferencesDataStore
import dev.alllexey.itmowidgets.core.testing.RecordingDiagnostics
import dev.alllexey.itmowidgets.testkit.FakeClock
import dev.alllexey.itmowidgets.testkit.bodyText
import dev.alllexey.itmowidgets.testkit.respondJson
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException

/** The session over an in-memory token file and the MyItmoApi 2.x client, with ITMO.ID answered by a MockEngine. */
class SessionRepositoryImplTest {

    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = AppDispatchers(io = dispatcher, default = dispatcher, main = dispatcher)
    private val clock = FakeClock(Instant.parse("2026-07-24T00:00:00Z"))

    @Test
    fun initializesAsSignedOutWithoutARefreshToken() = runTest(dispatcher) {
        val fixture = fixture(session = null)

        fixture.repository.initialize()

        assertEquals(SessionState.SignedOut, fixture.repository.state.value)
        assertEquals(0, fixture.identitySync.requests)
    }

    @Test
    fun initializesAsReauthenticationRequiredForAnExpiredToken() = runTest(dispatcher) {
        val fixture = fixture(session = stored(refreshExpiresAt = clock.now() - 1.seconds))

        fixture.repository.initialize()

        assertEquals(SessionState.ReauthenticationRequired, fixture.repository.state.value)
        assertTrue(fixture.requests.isEmpty())
    }

    @Test
    fun aRefreshTokenOnlyStateNeedsANewSignInLike22() = runTest(dispatcher) {
        val fixture = fixture(session = null)
        fixture.storage.replaceWithRefreshToken("stored-refresh")

        fixture.repository.initialize()

        assertEquals(SessionState.ReauthenticationRequired, fixture.repository.state.value)
    }

    @Test
    fun restoresTheLocalUserWithoutRequiringANetworkRequest() = runTest(dispatcher) {
        val user = CurrentUser(123456, "Иванов Иван", null)
        val fixture = fixture(session = stored(), currentUser = user)

        fixture.repository.initialize()

        assertEquals(SessionState.SignedIn(user), fixture.repository.state.value)
        assertEquals(1, fixture.identitySync.requests)
        assertEquals(1, fixture.deviceSession.registerRequests)
        assertTrue(fixture.requests.isEmpty())
    }

    @Test
    fun validatesAManualTokenWithOneRefreshBeforeReplacingTheCurrentSession() = runTest(dispatcher) {
        val fixture = fixture(session = stored()) { respondJson(TOKEN_RESPONSE) }

        val result = fixture.repository.signInWithRefreshToken("  candidate-token  ")

        assertEquals(AppResult.Success(Unit), result)
        val form = fixture.requests.single().bodyText()
        assertTrue("grant_type=refresh_token" in form && "refresh_token=candidate-token" in form, form)
        fixture.assertStoredTokens()
        assertEquals(1, fixture.cleaner.requests)
        assertEquals(listOf("prepare", "signed-in"), fixture.effects.events)
        assertTrue(fixture.repository.state.value is SessionState.SignedIn)
    }

    @Test
    fun keepsTheCurrentSessionWhenManualTokenValidationHasANetworkError() = runTest(dispatcher) {
        val fixture = fixture(session = stored()) { throw IOException("offline") }

        val result = fixture.repository.signInWithRefreshToken("candidate-token")

        assertEquals(AppResult.Failure(AppError.Network), result)
        fixture.assertSessionKept()
    }

    @Test
    fun aRejectedManualTokenIsUnauthorizedAndKeepsTheCurrentSession() = runTest(dispatcher) {
        val fixture = fixture(session = stored()) {
            respondJson("""{"error":"invalid_grant"}""", HttpStatusCode.BadRequest)
        }

        val result = fixture.repository.signInWithRefreshToken("candidate-token")

        assertEquals(AppResult.Failure(AppError.Unauthorized), result)
        fixture.assertSessionKept()
    }

    @Test
    fun anItmoId5xxIsARetriableErrorThatKeepsTheCurrentSession() = runTest(dispatcher) {
        val fixture = fixture(session = stored()) { respondJson("{}", HttpStatusCode.BadGateway) }

        val result = fixture.repository.signInWithRefreshToken("candidate-token")

        assertTrue((result as AppResult.Failure).error is AppError.Unknown, result.toString())
        fixture.assertSessionKept()
    }

    @Test
    fun rejectsAnIncompleteInteractiveTokenResponse() = runTest(dispatcher) {
        val fixture = fixture(session = null)

        val result = fixture.repository.completeItmoIdLogin("{\"access_token\":\"only\"}")

        assertEquals(AppResult.Failure(AppError.Unauthorized), result)
        assertEquals(0, fixture.cleaner.requests)
    }

    @Test
    fun acceptsACompleteKeycloakTokenResponseWithFieldsTheAppDoesNotRead() = runTest(dispatcher) {
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
    fun rejectsTokenResponsesWithoutATokenWithNullOrInvalidExpirationsOrThatAreNotAnObject() = runTest(dispatcher) {
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

            assertEquals(AppResult.Failure(AppError.Unauthorized), fixture.repository.completeItmoIdLogin(body), body)
            assertEquals(0, fixture.cleaner.requests, body)
        }
    }

    @Test
    fun unregistersDeviceAndClearsPrivateDataBeforeTokensOnLogout() = runTest(dispatcher) {
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
    fun localLogoutSucceedsWhenBackendIsUnavailable() = runTest(dispatcher) {
        val fixture = fixture(
            session = stored(),
            unregisterError = IOException("offline")
        )

        fixture.repository.signOut()

        assertEquals(SessionState.SignedOut, fixture.repository.state.value)
        assertNull(fixture.storage.read())
    }

    @Test
    fun startsTheDemoWithoutTokensBackendBackgroundWorkOrItmoRequests() = runTest(dispatcher) {
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
    fun initializesAsTheDemoWithoutCheckingAToken() = runTest(dispatcher) {
        val fixture = fixture(session = stored(refreshExpiresAt = clock.now() - 1.seconds))
        fixture.settings.setDemoActive(true)

        fixture.repository.initialize()

        assertEquals(SessionState.SignedIn(DemoPeople.ME, demo = true), fixture.repository.state.value)
        assertEquals(0, fixture.identitySync.requests)
        assertEquals(0, fixture.deviceSession.registerRequests)
        assertTrue(fixture.requests.isEmpty())
    }

    @Test
    fun signingOutOfTheDemoKeepsTheDeviceRegistrationAlone() = runTest(dispatcher) {
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
    fun aRealSignInEndsALeftoverDemoFlag() = runTest(dispatcher) {
        val fixture = fixture(session = null) { respondJson(TOKEN_RESPONSE) }
        fixture.settings.setDemoActive(true)

        fixture.repository.signInWithRefreshToken("candidate-token")

        assertFalse(fixture.settings.getDemoActive())
        assertEquals(false, (fixture.repository.state.value as SessionState.SignedIn).demo)
    }

    @Test
    fun signOutRunsEveryCleanerExactlyOnce() = runTest(dispatcher) {
        val second = FakeSessionDataCleaner()
        val fixture = fixture(session = stored(), extraCleaners = listOf(second))

        fixture.repository.signOut()

        assertEquals(1, fixture.cleaner.requests)
        assertEquals(1, second.requests)
    }

    @Test
    fun leavingTheDemoRunsEveryCleanerExactlyOnce() = runTest(dispatcher) {
        val second = FakeSessionDataCleaner()
        val fixture = fixture(session = null, extraCleaners = listOf(second))
        fixture.repository.startDemo()
        assertEquals(1, fixture.cleaner.requests)
        assertEquals(1, second.requests)

        fixture.repository.signOut()

        assertEquals(2, fixture.cleaner.requests)
        assertEquals(2, second.requests)
        assertEquals(SessionState.SignedOut, fixture.repository.state.value)
    }

    @Test
    fun aHugeTokenLifetimeSaturatesLikeTwoPointTwo() = runTest(dispatcher) {
        val fixture = fixture(session = null)

        val result = fixture.repository.completeItmoIdLogin(
            """{"access_token":"access","expires_in":${Long.MAX_VALUE},"refresh_token":"refresh",""" +
                """"refresh_expires_in":${Long.MAX_VALUE / 1000},"id_token":"header.payload.signature"}"""
        )

        assertEquals(AppResult.Success(Unit), result)
        assertEquals(Long.MAX_VALUE, fixture.storage.accessExpiresAt)
        assertEquals(Long.MAX_VALUE, fixture.storage.refreshExpiresAt)
    }

    private suspend fun fixture(
        session: TokenSet?,
        currentUser: CurrentUser? = null,
        unregisterError: Exception? = null,
        order: MutableList<String> = mutableListOf(),
        extraCleaners: List<SessionDataCleaner> = emptyList(),
        answer: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = {
            throw AssertionError("Unexpected request ${it.url}")
        }
    ): Fixture {
        val storage = InMemoryTokenFile()
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
        val cleaners = listOf(cleaner) + extraCleaners
        val transitions = SessionTransitions(
            myItmo = client,
            currentUserProvider = object : CurrentUserProvider {
                override suspend fun getCurrentUser(): CurrentUser? = currentUser
            },
            dataCleaners = { cleaners },
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
        val storage: InMemoryTokenFile,
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
            assertEquals("access", storage.accessToken)
            assertEquals((clock.now() + 300.seconds).toEpochMilliseconds(), storage.accessExpiresAt)
            assertEquals("refresh", storage.refreshToken)
            assertEquals((clock.now() + 600.seconds).toEpochMilliseconds(), storage.refreshExpiresAt)
            assertEquals("header.payload.signature", storage.getIdToken())
        }

        fun assertSessionKept() {
            assertEquals(0, cleaner.requests)
            assertEquals("stored-access", storage.accessToken)
            assertEquals("stored-refresh", storage.refreshToken)
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
