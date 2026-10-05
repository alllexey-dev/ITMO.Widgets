package dev.alllexey.itmowidgets.client.http

import dev.alllexey.itmowidgets.client.support.MockBackend
import dev.alllexey.itmowidgets.client.support.Probe
import dev.alllexey.itmowidgets.client.support.runSuspend
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RouteTest {

    @Test
    fun pathIsAbsoluteAndEachSegmentIsEncodedOnItsOwn() = runSuspend {
        val backend = MockBackend(baseUrl = "https://backend.test/ignored/")

        backend.http.callUnit(BackendRoute(HttpMethod.Get, listOf("api", "users", "a/b c?#")))

        assertEquals("https://backend.test/api/users/a%2Fb%20c%3F%23", backend.lastRequest.url.toString())
    }

    @Test
    fun queryOmitsNullValues() = runSuspend {
        val backend = MockBackend()

        backend.http.callUnit(
            BackendRoute(
                HttpMethod.Get,
                listOf("api", "schedule"),
                query = listOf("date_start" to "2026-01-05", "platform" to null, "date_end" to "2026-01-11"),
            ),
        )

        assertEquals(
            "https://backend.test/api/schedule?date_start=2026-01-05&date_end=2026-01-11",
            backend.lastRequest.url.toString(),
        )
    }

    @Test
    fun jsonBodyOmitsNullsAndCarriesItsContentType() = runSuspend {
        val backend = MockBackend()

        backend.http.callUnit(
            BackendRoute(HttpMethod.Delete, listOf("api", "device", "current"), body = jsonBody(Probe("Synthetic"))),
        )

        val request = backend.lastRequest
        assertEquals(HttpMethod.Delete, request.method)
        val body = assertIs<TextContent>(request.body)
        assertEquals("""{"name":"Synthetic"}""", body.text)
        assertTrue(body.contentType.match(ContentType.Application.Json))
    }

    @Test
    fun noBodyWithoutJsonBody() = runSuspend {
        val backend = MockBackend()

        backend.http.callUnit(BackendRoute(HttpMethod.Post, listOf("api", "friends", "1", "accept")))

        assertIs<OutgoingContent.NoContent>(backend.lastRequest.body)
    }
}
