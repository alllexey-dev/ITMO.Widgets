package dev.alllexey.itmowidgets.client.support

import dev.alllexey.itmowidgets.client.BackendClient
import dev.alllexey.itmowidgets.client.error.BackendException
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.util.flattenEntries
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * One public function of an area API called with synthetic arguments. Each area lists one case per public function
 * in its `<Area>RouteCases`; the area's request assertions run them, and the conformance test (CO-09b) aggregates
 * every area's cases.
 */
class RouteCase(val name: String, val call: suspend BackendClient.() -> Unit) {
    override fun toString(): String = name
}

/** What one call sent: [path] is percent-encoded, [query] keeps the order and repeats, [body] is `null` if none. */
data class RecordedRequest(
    val method: HttpMethod,
    val path: String,
    val query: List<Pair<String, String>>,
    val body: String?,
)

/** Runs this case against a [MockBackend] that answers [response] and returns the one request it sent. */
suspend fun RouteCase.record(response: String = """{"success":true}"""): RecordedRequest =
    recordRequest(name, response) { call(client) }

/**
 * Runs [call] against a [MockBackend] that answers [response] and returns the one request it sent. The call's own
 * outcome is not checked beyond a contract failure, which the default empty envelope causes for every call that
 * returns a value; decoding is the subject of each area's decode tests.
 */
suspend fun recordRequest(
    name: String,
    response: String = """{"success":true}""",
    call: suspend MockBackend.() -> Unit,
): RecordedRequest {
    val backend = MockBackend { ok(response) }
    try {
        backend.call()
    } catch (_: BackendException.Contract) {
        // The answer does not matter here.
    }
    val requests = backend.requests
    if (requests.size != 1) fail("$name sent ${requests.size} requests instead of one")
    return requests.single().recorded()
}

/** Asserts the request this case sends; [body] is compared with [assertJsonEquals]. */
suspend fun RouteCase.assertRequest(
    method: HttpMethod,
    path: String,
    query: List<Pair<String, String>> = emptyList(),
    body: String? = null,
) {
    val request = record()
    assertEquals(method, request.method, "$name method")
    assertEquals(path, request.path, "$name path")
    assertEquals(query, request.query, "$name query")
    when {
        body == null -> assertEquals(null, request.body, "$name body")
        request.body == null -> fail("$name sent no body")
        else -> assertJsonEquals(body, request.body)
    }
}

private fun HttpRequestData.recorded() = RecordedRequest(
    method = method,
    path = url.encodedPath,
    query = url.parameters.flattenEntries(),
    body = when (val content = body) {
        is OutgoingContent.NoContent -> null
        is TextContent -> content.text
        is OutgoingContent.ByteArrayContent -> content.bytes().decodeToString()
        else -> fail("Unexpected request body ${content::class.simpleName}")
    },
)
