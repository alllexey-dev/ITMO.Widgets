package dev.alllexey.itmowidgets.feature.recordbook.data.sheets

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.testing.noDemo
import dev.alllexey.itmowidgets.di.NetworkModule
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetFixtures
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetTab
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PublicSheetClientTest {
    private val id = "1TestSheetIdForUnitTests_0123456789-abc"
    private val server = MockWebServer()
    private lateinit var client: PublicSheetClient

    @Before fun start() {
        server.start()
        client = PublicSheetClient(NetworkModule.providePublicWebClient(), server.url("/"), noDemo())
    }

    @After fun stop() = server.shutdown()

    private fun csv(name: String = "grades_multiheader.csv") = MockResponse()
        .setHeader("Content-Type", "text/csv; charset=utf-8")
        .setBody(SheetFixtures.text(name))

    private fun html(name: String, code: Int = 200) = MockResponse().setResponseCode(code)
        .setHeader("Content-Type", "text/html; charset=utf-8")
        .setBody(SheetFixtures.text(name))

    @Test fun `a csv export is loaded without cookies`() = runTest {
        server.enqueue(csv())

        val result = client.grid(id, 22)

        assertEquals("66,3", (result as SheetFetch.Loaded).value.cell(5, 11))
        val request = server.takeRequest()
        assertEquals("/spreadsheets/d/$id/export?format=csv&gid=22", request.path)
        assertNull(request.getHeader("Cookie"))
    }

    @Test fun `a redirect to the download host is followed`() = runTest {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/download/synthetic.csv"))
        server.enqueue(csv())

        assertTrue(client.grid(id, 22) is SheetFetch.Loaded)
        assertEquals(2, server.requestCount)
    }

    @Test fun `a forbidden export falls back to the html tab`() = runTest {
        server.enqueue(html("login.html", code = 401))
        server.enqueue(html("grid_htmlview.html"))

        val result = client.grid(id, 22)

        assertEquals("66,3", (result as SheetFetch.Loaded).value.cell(5, 11))
        server.takeRequest()
        assertEquals("/spreadsheets/d/$id/htmlview/sheet?headers=false&gid=22", server.takeRequest().path)
    }

    @Test fun `a non-csv answer also falls back to the html tab`() = runTest {
        server.enqueue(html("tabs_htmlview.html"))
        server.enqueue(html("grid_htmlview.html"))

        assertTrue(client.grid(id, 22) is SheetFetch.Loaded)
        assertEquals(2, server.requestCount)
    }

    @Test fun `a sheet behind a sign-in is closed`() = runTest {
        server.enqueue(html("login.html", code = 401))
        server.enqueue(html("login.html", code = 401))
        assertEquals(SheetFetch.Closed, client.grid(id, 22))

        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/v3/signin/identifier?continue=x"))
        server.enqueue(html("login.html"))
        assertEquals(SheetFetch.Closed, client.grid(id, 22))
        assertEquals(4, server.requestCount)
    }

    @Test fun `a gone tab is missing and a gone sheet is closed`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        assertEquals(SheetFetch.Missing, client.grid(id, 22))

        server.enqueue(MockResponse().setResponseCode(404))
        assertEquals(SheetFetch.Closed, client.tabs(id))
    }

    @Test fun `an answer over the limit is too large`() = runTest {
        server.enqueue(csv().setBody("a,b\n").setHeader("Content-Length", PublicSheetClient.MAX_BYTES + 1L))
        assertEquals(SheetFetch.TooLarge, client.grid(id, 22))

        val big = Buffer().write(ByteArray(PublicSheetClient.MAX_BYTES + 1) { 'a'.code.toByte() })
        server.enqueue(MockResponse().setHeader("Content-Type", "text/csv").setChunkedBody(big, 64 * 1024))
        assertEquals(SheetFetch.TooLarge, client.grid(id, 22))
    }

    @Test fun `server errors and rate limits are network failures`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        assertEquals(SheetFetch.Failed(AppError.Network), client.grid(id, 22))

        server.enqueue(MockResponse().setResponseCode(429))
        assertEquals(SheetFetch.Failed(AppError.Network), client.tabs(id))
    }

    @Test fun `a lost connection is a network failure`() = runTest {
        server.dispatcher = object : Dispatcher() {
            private val disconnect = MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)
            override fun dispatch(request: RecordedRequest) = disconnect
            override fun peek() = disconnect
        }
        assertEquals(SheetFetch.Failed(AppError.Network), client.grid(id, 22))
    }

    @Test fun `the tabs come from the html view`() = runTest {
        server.enqueue(html("tabs_htmlview.html"))

        val result = client.tabs(id)

        assertEquals(
            listOf(SheetTab(0, "Шаблон"), SheetTab(11, "All"), SheetTab(22, "P3110"), SheetTab(33, "BARS (Fall semester 2026)")),
            (result as SheetFetch.Loaded).value,
        )
        assertEquals("/spreadsheets/d/$id/htmlview", server.takeRequest().path)
    }
}
