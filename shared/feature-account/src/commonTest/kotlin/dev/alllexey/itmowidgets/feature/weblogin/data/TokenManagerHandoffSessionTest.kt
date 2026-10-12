package dev.alllexey.itmowidgets.feature.weblogin.data

import dev.alllexey.itmoapi.itmoid.TokenSet
import dev.alllexey.itmoapi.itmoid.TokenStorage
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmoapi.myitmo.MyItmoConfiguration
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.feature.weblogin.domain.HandoffToken
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
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException

/** The hand-off over the MyItmoApi 2.x client and an in-memory token storage, with ITMO.ID answered by a MockEngine. */
class TokenManagerHandoffSessionTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = FakeClock(Instant.parse("2026-10-12T09:00:00Z"))
    private val previous = TokenSet(
        accessToken = "stored-access",
        // Still valid: the hand-off refreshes anyway.
        accessExpiresAt = clock.now() + 300.seconds,
        refreshToken = "stored-refresh",
        refreshExpiresAt = clock.now() + 30.days,
        idToken = "stored.id.token",
    )

    @Test
    fun keepsTheNewTokensAndReturnsThePreviousRefreshTokenOnce() = runTest(dispatcher) {
        val fixture = fixture { respondJson(tokenResponse("rotated-refresh")) }

        val result = fixture.session.rotate()

        val token = assertIs<AppResult.Success<HandoffToken>>(result).value
        assertEquals("stored-refresh", token.refreshToken)
        assertEquals(previous.refreshExpiresAt, token.refreshExpiresAt)
        assertTrue("refresh_token=stored-refresh" in fixture.requests.single().bodyText())
        // The phone's session is the new set, and the handed-out token was never written anywhere.
        assertEquals(listOf("rotated-refresh"), fixture.storage.writes.map { it?.refreshToken })
    }

    @Test
    fun aRejectedRefreshIsUnauthorizedAndKeepsTheSession() = runTest(dispatcher) {
        val fixture = fixture { respondJson("""{"error":"invalid_grant"}""", HttpStatusCode.BadRequest) }

        assertEquals(AppResult.Failure(AppError.Unauthorized), fixture.session.rotate())
        assertEquals("stored-refresh", fixture.storage.current?.refreshToken)
        assertTrue(fixture.storage.writes.isEmpty())
    }

    @Test
    fun aNetworkFailureIsRetriable() = runTest(dispatcher) {
        val fixture = fixture { throw IOException("offline") }

        assertEquals(AppResult.Failure(AppError.Network), fixture.session.rotate())
        assertTrue(fixture.storage.writes.isEmpty())
    }

    @Test
    fun aRefreshThatKeepsTheSameTokenHandsNothingOut() = runTest(dispatcher) {
        val fixture = fixture { respondJson(tokenResponse("stored-refresh")) }

        assertIs<AppError.Unknown>(assertIs<AppResult.Failure>(fixture.session.rotate()).error)
    }

    @Test
    fun theDemoSessionAndASignedOutPhoneSendNothing() = runTest(dispatcher) {
        val demo = fixture(demo = true)
        val signedOut = fixture(session = null)

        assertEquals(AppResult.Failure(AppError.DemoUnavailable), demo.session.rotate())
        assertEquals(AppResult.Failure(AppError.Unauthorized), signedOut.session.rotate())
        assertTrue(demo.requests.isEmpty() && signedOut.requests.isEmpty())
    }

    private fun fixture(
        session: TokenSet? = previous,
        demo: Boolean = false,
        answer: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = {
            throw AssertionError("Unexpected request ${it.url}")
        },
    ): Fixture {
        val storage = RecordingStorage(session)
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            requests += request
            answer(request)
        }
        val client = MyItmoClient(MyItmoConfiguration.DEFAULT, storage = storage, engine = engine, clock = clock)
        val dispatchers = AppDispatchers(io = dispatcher, default = dispatcher, main = dispatcher)
        return Fixture(TokenManagerHandoffSession(client, storage, FakeDemoMode(demo), dispatchers), storage, requests)
    }

    private class Fixture(
        val session: TokenManagerHandoffSession,
        val storage: RecordingStorage,
        val requests: List<HttpRequestData>,
    )

    private class RecordingStorage(var current: TokenSet?) : TokenStorage {
        val writes = mutableListOf<TokenSet?>()

        override suspend fun read(): TokenSet? = current

        override suspend fun write(tokens: TokenSet?) {
            writes += tokens
            current = tokens
        }
    }

    private fun tokenResponse(refreshToken: String) =
        """{"access_token":"access","expires_in":1800,"refresh_token":"$refreshToken",""" +
            """"refresh_expires_in":2592000,"id_token":"header.payload.signature"}"""
}
