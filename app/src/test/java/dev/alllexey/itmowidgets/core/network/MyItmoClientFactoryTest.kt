package dev.alllexey.itmowidgets.core.network

import dev.alllexey.itmoapi.itmoid.TokenSet
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.storage.MyItmoStorage
import dev.alllexey.itmowidgets.core.storage.TokenCipher
import dev.alllexey.itmowidgets.core.testing.RecordingAppLog
import dev.alllexey.itmowidgets.core.work.CheckOutcome
import dev.alllexey.itmowidgets.core.work.outcomeOf
import dev.alllexey.itmowidgets.testkit.FakeClock
import dev.alllexey.itmowidgets.testkit.bodyText
import dev.alllexey.itmowidgets.testkit.respondJson
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import java.io.File
import java.io.IOException
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The 2.x client the factory builds, over `MyItmoStorage` and a MockEngine standing in for ITMO.ID and MyITMO. */
class MyItmoClientFactoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val clock = FakeClock(Instant.parse("2026-07-24T00:00:00Z"))
    private val requests = mutableListOf<HttpRequestData>()
    private val storage by lazy {
        MyItmoStorage(
            tokenFile = File(folder.root, "myitmo_tokens.enc"),
            tokenCipher = PlainTokenCipher,
            clock = java.time.Clock.fixed(java.time.Instant.parse("2026-07-24T00:00:00Z"), ZoneOffset.UTC),
            log = RecordingAppLog()
        )
    }

    @Test
    fun `a code exchange is stored through the client's one token writer`() = runTest {
        val client = client { respondJson(TOKEN_RESPONSE) }

        val tokens = client.identity.exchange(code = "synthetic-code", verifier = "v".repeat(43))
        client.tokens.replaceTokens(tokens)

        val form = requests.single().bodyText()
        assertTrue(form, "grant_type=authorization_code" in form && "code=synthetic-code" in form)
        assertEquals("access", storage.getAccessToken())
        assertEquals((clock.now() + 300.seconds).toEpochMilliseconds(), storage.getAccessExpiresAt())
        assertEquals("refresh", storage.getRefreshToken())
        assertEquals((clock.now() + 600.seconds).toEpochMilliseconds(), storage.getRefreshExpiresAt())
        assertEquals("header.payload.signature", storage.getIdToken())
    }

    @Test
    fun `an expired access token is refreshed once before the MyITMO request`() = runTest {
        storage.write(stored(accessExpiresAt = clock.now() - 1.minutes))
        val client = client { request ->
            if (request.url.host == "id.itmo.ru") respondJson(TOKEN_RESPONSE) else respondJson("""{"error_code":0,"result":[]}""")
        }

        client.system.getDashboard()

        assertEquals(listOf("id.itmo.ru", "my.itmo.ru"), requests.map { it.url.host })
        assertTrue("refresh_token=stored-refresh" in requests.first().bodyText())
        assertEquals("Bearer access", requests.last().headers[HttpHeaders.Authorization])
        assertEquals("access", storage.getAccessToken())
        assertEquals("refresh", storage.getRefreshToken())
    }

    @Test
    fun `a rejected refresh needs a new sign-in and leaves the stored session to the app`() = runTest {
        val expired = stored(accessExpiresAt = clock.now() - 1.minutes)
        storage.write(expired)
        val client = client { respondJson("""{"error":"invalid_grant"}""", HttpStatusCode.BadRequest) }

        val error = failure { client.tokens.validAccessToken() }

        assertEquals(AppError.Unauthorized, error)
        assertEquals(CheckOutcome.DONE, outcomeOf(listOf(error)))
        assertEquals("stored-refresh", storage.getRefreshToken())
    }

    @Test
    fun `an ITMO_ID 5xx during a refresh is retriable and keeps the session`() = runTest {
        storage.write(stored(accessExpiresAt = clock.now() - 1.minutes))
        val client = client { respondJson("""{"message":"unavailable"}""", HttpStatusCode.ServiceUnavailable) }

        val error = failure { client.tokens.validAccessToken() }

        assertTrue(error.toString(), error is AppError.Unknown)
        assertEquals(CheckOutcome.RETRY, outcomeOf(listOf(error)))
        assertEquals("stored-access", storage.getAccessToken())
        assertEquals("stored-refresh", storage.getRefreshToken())
    }

    @Test
    fun `a garbled token response is retriable and keeps the session`() = runTest {
        storage.write(stored(accessExpiresAt = clock.now() - 1.minutes))
        val client = client { respondJson("not json") }

        val error = failure { client.tokens.validAccessToken() }

        assertEquals(CheckOutcome.RETRY, outcomeOf(listOf(error)))
        assertEquals("stored-refresh", storage.getRefreshToken())
    }

    @Test
    fun `network loss is a network error, also inside a refresh`() = runTest {
        storage.write(stored(accessExpiresAt = clock.now() - 1.minutes))
        val client = client { throw IOException("offline") }

        assertEquals(AppError.Network, failure { client.tokens.validAccessToken() })
        assertEquals(AppError.Network, failure { client.system.getDashboard() })
        assertEquals("stored-refresh", storage.getRefreshToken())
    }

    @Test
    fun `MyITMO answering 401 after one refresh needs a new sign-in`() = runTest {
        storage.write(stored(accessExpiresAt = clock.now() + 1.minutes + 1.seconds))
        val client = client { request ->
            if (request.url.host == "id.itmo.ru") respondJson(TOKEN_RESPONSE) else respondJson("{}", HttpStatusCode.Unauthorized)
        }

        assertEquals(AppError.Unauthorized, failure { client.system.getDashboard() })
    }

    private fun client(answer: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): MyItmoClient {
        val engine = MockEngine { request ->
            requests += request
            answer(request)
        }
        return MyItmoClientFactory.create(storage = storage, engine = engine, clock = clock)
    }

    private suspend fun failure(block: suspend () -> Unit): AppError {
        try {
            block()
        } catch (error: Exception) {
            return error.toAppError()
        }
        throw AssertionError("Expected a failure")
    }

    private fun stored(accessExpiresAt: Instant) = TokenSet(
        accessToken = "stored-access",
        accessExpiresAt = accessExpiresAt,
        refreshToken = "stored-refresh",
        refreshExpiresAt = clock.now() + 30.minutes,
        idToken = "stored.id.token"
    )

    private object PlainTokenCipher : TokenCipher {
        override fun encrypt(value: String): String = value

        override fun decrypt(value: String): String = value
    }

    private companion object {
        const val TOKEN_RESPONSE = """{"access_token":"access","expires_in":300,"refresh_token":"refresh",""" +
            """"refresh_expires_in":600,"id_token":"header.payload.signature","session_state":"synthetic"}"""
    }
}
