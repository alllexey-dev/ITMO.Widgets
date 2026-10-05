package dev.alllexey.itmowidgets.core.network

import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.core.network.Core2Harness.Companion.contractFixture
import dev.alllexey.itmowidgets.core.network.Core2Harness.Companion.errorEnvelope
import dev.alllexey.itmowidgets.core.network.Core2Harness.Companion.session
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.testkit.respondJson
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.IOException
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Core 2.0 over the MyItmoApi 2.x session: the token it sends and the released error semantics (report 92). */
class BackendClientFactoryTest {

    @Test
    fun `a request carries the stored access token and decodes Backend's fixture`() = runTest {
        val harness = Core2Harness(session()) { respondJson(contractFixture("http/users/myUserData.json")) }

        val data = harness.client.users.myUserData()

        assertEquals(100001, data.isu)
        assertEquals("Bearer stored-access", harness.requests.single().headers[HttpHeaders.Authorization])
    }

    @Test
    fun `an expiring access token is refreshed once by the one writer before the Backend request`() = runTest {
        val harness = Core2Harness(session(accessExpiresIn = (-10).minutes)) {
            respondJson(contractFixture("http/users/myUserData.json"))
        }

        harness.client.users.myUserData()

        assertEquals(listOf(Core2Harness.ITMO_ID_HOST, "backend.test"), harness.requests.map { it.url.host })
        assertEquals("Bearer refreshed-access", harness.requests.last().headers[HttpHeaders.Authorization])
        assertEquals("refreshed-refresh", harness.storage.tokens?.refreshToken)
    }

    @Test
    fun `no session or an expired one sends no Authorization header`() = runTest {
        for (stored in listOf(null, session(accessExpiresIn = 0.minutes, refreshExpiresIn = 0.minutes))) {
            val harness = Core2Harness(stored) { respondJson(contractFixture("http/users/myUserData.json")) }

            harness.client.users.myUserData()

            assertNull(harness.requests.single().headers[HttpHeaders.Authorization])
        }
    }

    @Test
    fun `Backend's statuses map with the released semantics`() = runTest {
        val cases = listOf(
            Answer(HttpStatusCode.Unauthorized, "") to AppError.Unauthorized,
            Answer(HttpStatusCode.Unauthorized, errorEnvelope("unauthorized")) to AppError.Unauthorized,
            Answer(HttpStatusCode.Forbidden, errorEnvelope("restricted")) to AppError.Restricted,
            Answer(HttpStatusCode.Forbidden, errorEnvelope("permission_denied")) to AppError.Forbidden,
            Answer(HttpStatusCode.Forbidden, errorEnvelope("access_denied")) to AppError.Forbidden,
            Answer(HttpStatusCode.Forbidden, errorEnvelope("csrf")) to AppError.Forbidden,
            Answer(HttpStatusCode.Forbidden, "") to AppError.Forbidden,
            Answer(HttpStatusCode.NotFound, errorEnvelope("not_found")) to AppError.NotFound,
        )
        for ((answer, expected) in cases) {
            val harness = Core2Harness(session()) {
                respond(answer.body, answer.status, headersOf(HttpHeaders.ContentType, "application/json"))
            }

            val error = failure { harness.client.users.myUserData() }

            assertEquals("$answer", expected, error.asAppError())
            assertEquals("$answer", expected, error.toAppError())
        }
    }

    @Test
    fun `another status or a broken answer is unknown and keeps the exception`() = runTest {
        val answers = listOf(
            Answer(HttpStatusCode.BadRequest, errorEnvelope("invalid_request")),
            Answer(HttpStatusCode.InternalServerError, errorEnvelope("internal_error")),
            Answer(HttpStatusCode.BadGateway, "<html>Bad gateway</html>"),
            Answer(HttpStatusCode.OK, """{"success":false,"data":null,"error":null}"""),
            Answer(HttpStatusCode.OK, "not json"),
        )
        for (answer in answers) {
            val harness = Core2Harness(session()) {
                respond(answer.body, answer.status, headersOf(HttpHeaders.ContentType, "application/json"))
            }

            val error = failure { harness.client.users.myUserData() }

            assertSame("$answer", error, (error.asAppError() as AppError.Unknown).cause)
            assertSame("$answer", error, (error.toAppError() as AppError.Unknown).cause)
        }
    }

    @Test
    fun `no answer is a network error, also inside the token refresh`() = runTest {
        val offline = Core2Harness(session()) { throw IOException("offline") }
        val refreshOffline = Core2Harness(
            session(accessExpiresIn = (-10).minutes),
            identity = { throw IOException("offline") }
        ) { throw AssertionError("Backend called without a token") }

        for (harness in listOf(offline, refreshOffline)) {
            val error = failure { harness.client.users.myUserData() }

            assertTrue(error.toString(), error is BackendException.Transport)
            assertEquals(AppError.Network, error.asAppError())
            assertEquals(AppError.Network, error.toAppError())
        }
        assertEquals("stored-refresh", refreshOffline.storage.tokens?.refreshToken)
        assertTrue(refreshOffline.backendRequests.isEmpty())
    }

    @Test
    fun `a refresh ITMO_ID rejects sends nothing and stays a network error as with Core 1_x`() = runTest {
        val harness = Core2Harness(
            session(accessExpiresIn = (-10).minutes),
            identity = { respondJson("""{"error":"invalid_grant"}""", HttpStatusCode.BadRequest) }
        ) { throw AssertionError("Backend called without a token") }

        val error = failure { harness.client.users.myUserData() }

        assertEquals(AppError.Network, error.toAppError())
        assertTrue(harness.backendRequests.isEmpty())
        assertEquals("stored-refresh", harness.storage.tokens?.refreshToken)
    }

    private data class Answer(val status: HttpStatusCode, val body: String)

    private suspend fun failure(block: suspend () -> Unit): BackendException {
        try {
            block()
        } catch (error: BackendException) {
            return error
        }
        throw AssertionError("Expected a BackendException")
    }
}
