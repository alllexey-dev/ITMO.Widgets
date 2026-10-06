package dev.alllexey.itmowidgets.feature.recordbook.data.sheets

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.feature.recordbook.MainDispatcherRule
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetFixtures
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetTab
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The public sheets' client on the production Ktor configuration over a `MockEngine` for docs.google.com. */
class PublicSheetClientTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatchers = mainDispatcherRule.appDispatchers

    private val id = "1TestSheetIdForUnitTests_0123456789-abc"
    private val answers = ArrayDeque<MockRequestHandleScope.(HttpRequestData) -> HttpResponseData>()
    private val requests = mutableListOf<HttpRequestData>()
    private val engine = MockEngine { request ->
        requests += request
        answers.removeFirst()(request)
    }
    private val client = client()

    private fun client(demo: FakeDemoMode = FakeDemoMode()) =
        PublicSheetClient(publicSheetHttpClient(engine), Url("https://docs.google.com/"), demo, dispatchers)

    private fun enqueue(answer: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) = answers.addLast(answer)

    private fun csv(name: String = "grades_multiheader.csv") =
        enqueue { respond(SheetFixtures.text(name), headers = headersOf(HttpHeaders.ContentType, "text/csv; charset=utf-8")) }

    private fun html(name: String, code: Int = 200) = enqueue {
        respond(
            SheetFixtures.text(name),
            HttpStatusCode.fromValue(code),
            headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8")
        )
    }

    private fun status(code: Int) = enqueue { respond("", HttpStatusCode.fromValue(code)) }

    private fun redirect(location: String) = enqueue {
        respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, location))
    }

    private fun HttpRequestData.pathAndQuery(): String = url.encodedPathAndQuery

    @Test fun `a csv export is loaded without cookies`() = runTest {
        csv()

        val result = client.grid(id, 22)

        assertEquals("66,3", (result as SheetFetch.Loaded).value.cell(5, 11))
        val request = requests.single()
        assertEquals("https://docs.google.com/spreadsheets/d/$id/export?format=csv&gid=22", request.url.toString())
        assertNull(request.headers[HttpHeaders.Cookie])
    }

    @Test fun `the requests carry the public web timeouts`() = runTest {
        csv()

        client.grid(id, 22)

        val timeouts = requests.single().getCapabilityOrNull(HttpTimeoutCapability)!!
        assertEquals(15_000L, timeouts.connectTimeoutMillis)
        assertEquals(30_000L, timeouts.socketTimeoutMillis)
        assertEquals(90_000L, timeouts.requestTimeoutMillis)
    }

    @Test fun `a redirect to the download host is followed and its cookie is not kept`() = runTest {
        enqueue {
            respond(
                "", HttpStatusCode.Found,
                headersOf(HttpHeaders.Location to listOf("/download/synthetic.csv"), HttpHeaders.SetCookie to listOf("NID=1"))
            )
        }
        csv()

        assertTrue(client.grid(id, 22) is SheetFetch.Loaded)
        assertEquals(2, requests.size)
        assertEquals("/download/synthetic.csv", requests[1].pathAndQuery())
        assertNull(requests[1].headers[HttpHeaders.Cookie])
    }

    @Test fun `a redirect from https to http is not followed`() = runTest {
        redirect("http://docs.google.com/spreadsheets/d/$id/export?format=csv&gid=22")

        assertEquals(SheetFetch.Failed(AppError.Unknown()), client.grid(id, 22))
        assertEquals(1, requests.size)
    }

    @Test fun `a forbidden export falls back to the html tab`() = runTest {
        html("login.html", code = 401)
        html("grid_htmlview.html")

        val result = client.grid(id, 22)

        assertEquals("66,3", (result as SheetFetch.Loaded).value.cell(5, 11))
        assertEquals("/spreadsheets/d/$id/htmlview/sheet?headers=false&gid=22", requests[1].pathAndQuery())
    }

    @Test fun `a non-csv answer also falls back to the html tab`() = runTest {
        html("tabs_htmlview.html")
        html("grid_htmlview.html")

        assertTrue(client.grid(id, 22) is SheetFetch.Loaded)
        assertEquals(2, requests.size)
    }

    @Test fun `a sheet behind a sign-in is closed`() = runTest {
        html("login.html", code = 401)
        html("login.html", code = 401)
        assertEquals(SheetFetch.Closed, client.grid(id, 22))

        redirect("/v3/signin/identifier?continue=x")
        html("login.html")
        assertEquals(SheetFetch.Closed, client.grid(id, 22))
        assertEquals(4, requests.size)
    }

    @Test fun `a gone tab is missing and a gone sheet is closed`() = runTest {
        status(404)
        assertEquals(SheetFetch.Missing, client.grid(id, 22))

        status(404)
        assertEquals(SheetFetch.Closed, client.tabs(id))
    }

    @Test fun `an answer over the limit is too large`() = runTest {
        enqueue {
            respond(
                "a,b\n",
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("text/csv"),
                    HttpHeaders.ContentLength to listOf((PublicSheetClient.MAX_BYTES + 1L).toString())
                )
            )
        }
        assertEquals(SheetFetch.TooLarge, client.grid(id, 22))

        val big = ByteArray(PublicSheetClient.MAX_BYTES + 1) { 'a'.code.toByte() }
        enqueue { respond(ByteReadChannel(big), headers = headersOf(HttpHeaders.ContentType, "text/csv")) }
        assertEquals(SheetFetch.TooLarge, client.grid(id, 22))
    }

    @Test fun `server errors and rate limits are network failures`() = runTest {
        status(503)
        assertEquals(SheetFetch.Failed(AppError.Network), client.grid(id, 22))

        status(429)
        assertEquals(SheetFetch.Failed(AppError.Network), client.tabs(id))
    }

    @Test fun `a lost connection is a network failure`() = runTest {
        enqueue { throw IOException("connection reset") }

        assertEquals(SheetFetch.Failed(AppError.Network), client.grid(id, 22))
    }

    @Test fun `the demo session downloads nothing`() = runTest {
        assertEquals(SheetFetch.Failed(AppError.DemoUnavailable), client(FakeDemoMode(active = true)).grid(id, 22))
        assertTrue(requests.isEmpty())
    }

    @Test fun `the tabs come from the html view`() = runTest {
        html("tabs_htmlview.html")

        val result = client.tabs(id)

        assertEquals(
            listOf(SheetTab(0, "Шаблон"), SheetTab(11, "All"), SheetTab(22, "P3110"), SheetTab(33, "BARS (Fall semester 2026)")),
            (result as SheetFetch.Loaded).value,
        )
        assertEquals("/spreadsheets/d/$id/htmlview", requests.single().pathAndQuery())
    }
}
