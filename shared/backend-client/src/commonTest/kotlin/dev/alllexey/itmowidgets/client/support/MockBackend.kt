package dev.alllexey.itmowidgets.client.support

import dev.alllexey.itmowidgets.client.AccessTokenSource
import dev.alllexey.itmowidgets.client.BackendClient
import dev.alllexey.itmowidgets.client.http.BackendHttp
import dev.alllexey.itmowidgets.client.http.BackendRoute
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.serialization.Serializable

const val TEST_BASE_URL = "https://backend.test"

/** A [BackendHttp] and a [BackendClient] over one [MockEngine]; [requests] records what reached the engine. */
class MockBackend(
    tokens: AccessTokenSource = AccessTokenSource { null },
    baseUrl: String = TEST_BASE_URL,
    private val handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = {
        ok("""{"success":true}""")
    },
) {
    val engine = MockEngine { request -> handler(request) }

    internal val http = BackendHttp(baseUrl, tokens, engine)

    val client = BackendClient(baseUrl, tokens, engine)

    val requests: List<HttpRequestData> get() = engine.requestHistory

    val lastRequest: HttpRequestData get() = requests.single()
}

fun MockRequestHandleScope.ok(body: String): HttpResponseData = json(HttpStatusCode.OK, body)

fun MockRequestHandleScope.json(status: HttpStatusCode, body: String): HttpResponseData =
    respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

fun MockRequestHandleScope.html(status: HttpStatusCode, body: String): HttpResponseData =
    respond(body, status, headersOf(HttpHeaders.ContentType, "text/html"))

/** Backend's error envelope (`GlobalExceptionHandler`) with a synthetic message. */
fun errorEnvelope(code: String): String =
    """{"success":false,"data":null,"error":{"message":"synthetic message","code":"$code"}}"""

internal fun probeRoute(): BackendRoute = BackendRoute(HttpMethod.Get, listOf("api", "probe"))

/** A synthetic DTO with an `init` invariant, standing in for the area models. */
@Serializable
data class Probe(val name: String, val note: String? = null) {
    init {
        require(name.isNotBlank()) { "name is blank" }
    }
}
