package dev.alllexey.itmowidgets.core.debug

import dev.alllexey.itmoapi.itmoid.TokenSet
import dev.alllexey.itmoapi.itmoid.TokenStorage
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.network.MyItmoClientFactory
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.FakeSessionDataCleaner
import dev.alllexey.itmowidgets.core.testing.FakeSessionTokenStore
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.testing.noDemo
import dev.alllexey.itmowidgets.testkit.FakeClock
import dev.alllexey.itmowidgets.testkit.bodyText
import dev.alllexey.itmowidgets.testkit.respondJson
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The debug token replacement over the real MyItmoApi 2.x client, with ITMO.ID answered by a MockEngine. */
class DefaultDebugRefreshTokenControllerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatchers = mainDispatcherRule.appDispatchers
    private val clock = FakeClock(Instant.parse("2026-07-24T00:00:00Z"))
    private val tokenStore = FakeSessionTokenStore(signedIn = false)
    private val cleaner = FakeSessionDataCleaner()
    private val session = InMemoryTokenStorage()
    private val requests = mutableListOf<HttpRequestData>()

    @Test
    fun `trims token validates it and clears session data`() = runTest {
        val controller = controller(status = HttpStatusCode.OK, body = TOKEN_RESPONSE)

        val result = controller.replaceRefreshToken("  test-refresh-token  ")

        assertEquals(AppResult.Success(Unit), result)
        assertEquals("test-refresh-token", tokenStore.refreshToken)
        assertEquals(1, requests.size)
        assertTrue(requests.single().bodyText().contains("refresh_token=test-refresh-token&"))
        assertEquals("refresh", session.tokens?.refreshToken)
        assertEquals(clock.now() + 300.seconds, session.tokens?.accessExpiresAt)
        assertEquals(1, cleaner.requests)
    }

    @Test
    fun `clears rejected token and returns typed error`() = runTest {
        val controller = controller(status = HttpStatusCode.BadRequest, body = """{"error":"invalid_grant"}""")

        val result = controller.replaceRefreshToken("rejected-token")

        assertEquals(AppResult.Failure(AppError.Unauthorized), result)
        assertNull(tokenStore.refreshToken)
        assertNull(session.tokens)
        assertEquals(2, cleaner.requests)
    }

    @Test
    fun `the demo session never reaches ITMO_ID`() = runTest {
        val controller = controller(status = HttpStatusCode.OK, body = TOKEN_RESPONSE, demo = FakeDemoMode(active = true))

        val result = controller.replaceRefreshToken("test-refresh-token")

        assertEquals(AppResult.Failure(AppError.DemoUnavailable), result)
        assertEquals(0, requests.size)
        assertNull(tokenStore.refreshToken)
        assertEquals(0, cleaner.requests)
    }

    private fun controller(status: HttpStatusCode, body: String, demo: DemoMode = noDemo()) =
        DefaultDebugRefreshTokenController(
            tokenStore = tokenStore,
            myItmo = MyItmoClientFactory.create(
                storage = session,
                engine = MockEngine { request ->
                    requests += request
                    respondJson(body, status)
                },
                clock = clock
            ),
            dataCleaners = setOf(cleaner),
            demo = demo,
            dispatchers = dispatchers
        )

    private class InMemoryTokenStorage : TokenStorage {
        var tokens: TokenSet? = null

        override suspend fun read(): TokenSet? = tokens

        override suspend fun write(tokens: TokenSet?) {
            this.tokens = tokens
        }
    }

    private companion object {
        const val TOKEN_RESPONSE = """{"access_token":"access","expires_in":300,"refresh_token":"refresh",""" +
            """"refresh_expires_in":600,"id_token":"header.payload.signature","session_state":"synthetic"}"""
    }
}
