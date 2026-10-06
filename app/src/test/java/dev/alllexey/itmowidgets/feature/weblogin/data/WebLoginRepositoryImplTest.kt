package dev.alllexey.itmowidgets.feature.weblogin.data

import dev.alllexey.itmowidgets.core.network.Core2Harness
import dev.alllexey.itmowidgets.core.network.Core2Harness.Companion.contractFixture
import dev.alllexey.itmowidgets.core.network.Core2Harness.Companion.errorEnvelope
import dev.alllexey.itmowidgets.core.network.Core2Harness.Companion.session
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.FakeBackendGate
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.weblogin.WebLoginPreview
import dev.alllexey.itmowidgets.testkit.respondJson
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Browser sign-in over Core 2.0 and MockEngine. */
class WebLoginRepositoryImplTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val challengeId = Uuid.parse("00000000-0000-4000-8000-000000000501")

    @Test fun `preview and approval go to Backend with the code and the challenge`() = runTest {
        val harness = Core2Harness(session()) { request ->
            when (request.method) {
                HttpMethod.Get -> respondJson(contractFixture("http/users/webLoginPreview.json"))
                else -> respondJson(contractFixture("http/users/approveWebLogin.json"))
            }
        }
        val repository = repository(harness)

        assertEquals(
            AppResult.Success(
                WebLoginPreview(challengeId, null, Instant.parse("2026-10-05T08:58:00Z"), Instant.parse("2026-10-05T09:03:00Z"))
            ),
            repository.preview("ABCD2345")
        )
        assertEquals(AppResult.Success(Unit), repository.approve(challengeId))
        assertEquals(
            listOf("GET /api/users/me/web-login/ABCD2345", "POST /api/users/me/web-login/$challengeId/approve"),
            harness.requests.map(HttpRequestData::line)
        )
        assertTrue(harness.requests.all { it.headers[HttpHeaders.Authorization] == "Bearer stored-access" })
    }

    @Test fun `a user agent is trimmed and a blank one reads as none`() = runTest {
        for ((wire, expected) in listOf("\" Chrome \"" to "Chrome", "\"  \"" to null)) {
            val harness = Core2Harness(session()) { respondJson(preview(userAgent = wire)) }

            val result = repository(harness).preview("ABCD2345")

            assertEquals(expected, (result as AppResult.Success).value.userAgent)
        }
    }

    @Test fun `without the connection nothing reaches Backend`() = runTest {
        val harness = Core2Harness(session()) { throw AssertionError("Backend called without the opt-in") }
        val repository = repository(harness, gate = FakeBackendGate(optedIn = false))

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), repository.preview("ABCD2345"))
        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), repository.approve(challengeId))
        assertTrue(harness.requests.isEmpty())
    }

    @Test fun `the demo session sends nothing, even with the stored opt-in`() = runTest {
        val demo = FakeDemoMode(active = true)
        val harness = Core2Harness(session()) { throw AssertionError("Backend called in the demo") }
        val repository = repository(harness, gate = FakeBackendGate(optedIn = true, demo = demo), demo = demo)

        assertEquals(AppResult.Failure(AppError.DemoUnavailable), repository.preview("ABCD2345"))
        assertEquals(AppResult.Failure(AppError.DemoUnavailable), repository.approve(challengeId))
        assertTrue(harness.requests.isEmpty())
    }

    @Test fun `Backend's refusals keep the released meaning`() = runTest {
        val cases = listOf(
            error(HttpStatusCode.NotFound, "not_found") to AppError.NotFound,
            error(HttpStatusCode.Unauthorized, "unauthorized") to AppError.Unauthorized,
            error(HttpStatusCode.Forbidden, "restricted") to AppError.Restricted,
            error(HttpStatusCode.Forbidden, "permission_denied") to AppError.Forbidden
        )
        for ((answer, expected) in cases) {
            val repository = repository(Core2Harness(session(), backend = answer))

            assertEquals(AppResult.Failure(expected), repository.preview("ABCD2345"))
            assertEquals(AppResult.Failure(expected), repository.approve(challengeId))
        }
    }

    @Test fun `no answer is a network error and a broken one is unknown`() = runTest {
        val offline = repository(Core2Harness(session()) { throw IOException("offline") })
        assertEquals(AppResult.Failure(AppError.Network), offline.preview("ABCD2345"))

        val empty = repository(Core2Harness(session()) { respondJson("""{"success":true,"data":null,"error":null}""") })
        assertTrue((empty.preview("ABCD2345") as AppResult.Failure).error is AppError.Unknown)
    }

    private fun repository(
        harness: Core2Harness,
        gate: FakeBackendGate = FakeBackendGate(optedIn = true),
        demo: FakeDemoMode = FakeDemoMode(),
    ) = WebLoginRepositoryImpl(gate, harness.client.users, demo, mainDispatcherRule.appDispatchers)

    private fun preview(userAgent: String) =
        """{"success":true,"data":{"challengeId":"$challengeId","userAgent":$userAgent,""" +
            """"createdAt":"2026-10-05T08:58:00Z","expiresAt":"2026-10-05T09:03:00Z"},"error":null}"""

    private fun error(status: HttpStatusCode, code: String): suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData =
        { respondJson(errorEnvelope(code), status) }
}

private val HttpRequestData.line: String get() = "${method.value} ${url.encodedPath}"
