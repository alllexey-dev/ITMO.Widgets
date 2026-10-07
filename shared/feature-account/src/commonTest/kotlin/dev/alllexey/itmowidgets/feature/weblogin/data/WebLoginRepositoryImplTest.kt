package dev.alllexey.itmowidgets.feature.weblogin.data

import dev.alllexey.itmowidgets.client.AccessTokenSource
import dev.alllexey.itmowidgets.client.BackendClient
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.FakeBackendGate
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.weblogin.WebLoginPreview
import dev.alllexey.itmowidgets.testkit.respondJson
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException

/** Browser sign-in over the real Core 2.0 client and a MockEngine: routes, mapping, errors and the gates. */
class WebLoginRepositoryImplTest {

    private val challengeId = Uuid.parse("00000000-0000-4000-8000-000000000501")

    @Test
    fun previewAndApprovalGoToBackendWithTheCodeAndTheChallenge() = runTest {
        val harness = BackendHarness { request ->
            when (request.method) {
                HttpMethod.Get -> respondJson(PREVIEW_FIXTURE)
                else -> respondJson(APPROVE_FIXTURE)
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
            harness.requests.map { "${it.method.value} ${it.url.encodedPath}" }
        )
        assertTrue(harness.requests.all { it.headers[HttpHeaders.Authorization] == "Bearer stored-access" })
    }

    @Test
    fun aUserAgentIsTrimmedAndABlankOneReadsAsNone() = runTest {
        for ((wire, expected) in listOf("\" Chrome \"" to "Chrome", "\"  \"" to null)) {
            val harness = BackendHarness { respondJson(preview(userAgent = wire)) }

            val result = repository(harness).preview("ABCD2345")

            assertEquals(expected, assertIs<AppResult.Success<WebLoginPreview>>(result).value.userAgent)
        }
    }

    @Test
    fun withoutTheConnectionNothingReachesBackend() = runTest {
        val harness = BackendHarness { throw AssertionError("Backend called without the opt-in") }
        val repository = repository(harness, gate = FakeBackendGate(optedIn = false))

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), repository.preview("ABCD2345"))
        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), repository.approve(challengeId))
        assertTrue(harness.requests.isEmpty())
    }

    @Test
    fun theDemoSessionSendsNothingEvenWithTheStoredOptIn() = runTest {
        val demo = FakeDemoMode(active = true)
        val harness = BackendHarness { throw AssertionError("Backend called in the demo") }
        val repository = repository(harness, gate = FakeBackendGate(optedIn = true, demo = demo), demo = demo)

        assertEquals(AppResult.Failure(AppError.DemoUnavailable), repository.preview("ABCD2345"))
        assertEquals(AppResult.Failure(AppError.DemoUnavailable), repository.approve(challengeId))
        assertTrue(harness.requests.isEmpty())
    }

    @Test
    fun backendRefusalsKeepTheReleasedMeaning() = runTest {
        val cases = listOf(
            refusal(HttpStatusCode.NotFound, "not_found") to AppError.NotFound,
            refusal(HttpStatusCode.Unauthorized, "unauthorized") to AppError.Unauthorized,
            refusal(HttpStatusCode.Forbidden, "restricted") to AppError.Restricted,
            refusal(HttpStatusCode.Forbidden, "permission_denied") to AppError.Forbidden
        )
        for ((answer, expected) in cases) {
            val repository = repository(BackendHarness(answer))

            assertEquals(AppResult.Failure(expected), repository.preview("ABCD2345"))
            assertEquals(AppResult.Failure(expected), repository.approve(challengeId))
        }
    }

    @Test
    fun noAnswerIsANetworkErrorAndABrokenOneIsUnknown() = runTest {
        val offline = repository(BackendHarness { throw IOException("offline") })
        assertEquals(AppResult.Failure(AppError.Network), offline.preview("ABCD2345"))

        val empty = repository(BackendHarness { respondJson("""{"success":true,"data":null,"error":null}""") })
        assertIs<AppError.Unknown>(assertIs<AppResult.Failure>(empty.preview("ABCD2345")).error)
    }

    private fun TestScope.repository(
        harness: BackendHarness,
        gate: FakeBackendGate = FakeBackendGate(optedIn = true),
        demo: FakeDemoMode = FakeDemoMode(),
    ): WebLoginRepositoryImpl {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return WebLoginRepositoryImpl(gate, harness.client.users, demo, AppDispatchers(dispatcher, dispatcher, dispatcher))
    }

    private fun preview(userAgent: String) =
        """{"success":true,"data":{"challengeId":"$challengeId","userAgent":$userAgent,""" +
            """"createdAt":"2026-10-05T08:58:00Z","expiresAt":"2026-10-05T09:03:00Z"},"error":null}"""

    private fun refusal(status: HttpStatusCode, code: String): suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData =
        { respondJson(errorEnvelope(code), status) }

    /** The Core 2.0 client over one MockEngine with the stored access token; [requests] records every request. */
    private class BackendHarness(backend: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) {
        val requests = mutableListOf<HttpRequestData>()
        val client = BackendClient(
            "https://backend.test",
            AccessTokenSource { "stored-access" },
            MockEngine { request -> requests += request; backend(request) },
        )
    }

    private companion object {
        /** Backend's contract fixtures `http/users/{webLoginPreview,approveWebLogin}.json` of `:shared:backend-client`. */
        const val PREVIEW_FIXTURE = """{"success":true,"data":{"challengeId":"00000000-0000-4000-8000-000000000501",""" +
            """"userAgent":null,"createdAt":"2026-10-05T08:58:00Z","expiresAt":"2026-10-05T09:03:00Z"},"error":null}"""
        const val APPROVE_FIXTURE = """{"success":true,"data":{},"error":null}"""

        /** Backend's error envelope (`GlobalExceptionHandler`) with a synthetic message. */
        fun errorEnvelope(code: String): String =
            """{"success":false,"data":null,"error":{"message":"synthetic message","code":"$code"}}"""
    }
}
