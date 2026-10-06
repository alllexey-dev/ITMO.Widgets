package dev.alllexey.itmowidgets.feature.recordbook.data.bars

import dev.alllexey.itmoapi.bars.auth.BarsLogin
import dev.alllexey.itmowidgets.core.result.AppError
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.net.UnknownHostException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The cookie renewal over the real MyItmoApi 2.x [BarsLogin] on a MockEngine standing in for ITMO.ID. */
class BarsCookieSilentLoginTest {

    private val cookie = "SYNTHETIC_SESSION=synthetic-value; SYNTHETIC_ID=synthetic-id"
    private val callback = "https://bars.itmo.ru/rest/login"
    private val cookies = FakeCookies(cookie)
    private val answers = ArrayDeque<MockRequestHandleScope.() -> HttpResponseData>()
    private val requests = mutableListOf<HttpRequestData>()
    private var offline = false
    private val engine = MockEngine { request ->
        if (offline) throw UnknownHostException("Synthetic")
        requests += request
        answers.removeFirst().invoke(this)
    }
    private val bars = BarsLogin(engine)
    private val login = BarsCookieSilentLogin(bars, cookies)

    private fun enqueue(answer: MockRequestHandleScope.() -> HttpResponseData) = answers.addLast(answer)

    private fun MockRequestHandleScope.redirect(location: String, setCookie: String) = respond(
        "", HttpStatusCode.Found,
        headersOf(HttpHeaders.Location to listOf(location), HttpHeaders.SetCookie to listOf(setCookie))
    )

    @Test fun `callback with the same state is a code and the answer's cookies go back for the request URL`() = runTest {
        enqueue { redirect("$callback?state=s1&code=synthetic-code", "SYNTHETIC_SESSION=renewed; Path=/auth/realms/itmo/") }

        val result = login.renew("s1")

        assertTrue(result is BarsCookieRenewal.Code)
        assertEquals("synthetic-code", (result as BarsCookieRenewal.Code).code)
        val loginUrl = bars.loginUrl("s1")
        assertEquals(listOf(loginUrl), cookies.reads)
        assertEquals(loginUrl, requests.single().url.toString())
        assertEquals(listOf(cookie), requests.single().headers.getAll(HttpHeaders.Cookie))
        assertEquals(listOf(loginUrl to listOf("SYNTHETIC_SESSION=renewed; Path=/auth/realms/itmo/")), cookies.stored)
    }

    @Test fun `callback with another state fails and stores only the answer's cookies`() = runTest {
        enqueue { redirect("$callback?state=other&code=synthetic-code", "SYNTHETIC_SESSION=renewed") }

        assertEquals(BarsCookieRenewal.Failed(AppError.Unknown()), login.renew("s1"))
        assertEquals(listOf("SYNTHETIC_SESSION=renewed"), cookies.stored.flatMap { it.second })
    }

    @Test fun `login page means the session ended`() = runTest {
        enqueue { respond("<html>synthetic login form</html>", HttpStatusCode.OK) }

        assertEquals(BarsCookieRenewal.SessionEnded, login.renew("s1"))
    }

    @Test fun `no cookies mean the session ended without a request`() = runTest {
        cookies.header = null
        assertEquals(BarsCookieRenewal.SessionEnded, login.renew("s1"))
        cookies.header = ""
        assertEquals(BarsCookieRenewal.SessionEnded, login.renew("s1"))

        assertEquals(0, requests.size)
        assertTrue(cookies.stored.isEmpty())
    }

    @Test fun `a server error is a failure and a lost network is a network failure`() = runTest {
        enqueue { respond("", HttpStatusCode.ServiceUnavailable) }
        assertEquals(BarsCookieRenewal.Failed(AppError.Unknown()), login.renew("s1"))

        offline = true
        assertEquals(BarsCookieRenewal.Failed(AppError.Network), login.renew("s1"))
    }

    @Test fun `no network before any answer is a network failure and stores no cookies`() = runTest {
        offline = true

        assertEquals(BarsCookieRenewal.Failed(AppError.Network), login.renew("s1"))
        assertEquals(1, cookies.reads.size)
        assertTrue(cookies.stored.isEmpty())
        assertEquals(0, requests.size)
    }

    @Test fun `unavailable cookie store is a failure without a request`() = runTest {
        cookies.failure = IllegalStateException("WebView is being updated")

        assertEquals(BarsCookieRenewal.Failed(AppError.Unknown()), login.renew("s1"))
        assertEquals(0, requests.size)
    }

    @Test fun `code is hidden from the text form`() {
        assertFalse(BarsCookieRenewal.Code("x").toString().contains("x"))
        assertFalse(BarsCookieRenewal.Code("synthetic-code").toString().contains("synthetic-code"))
    }

    private class FakeCookies(var header: String?) : ItmoIdCookies {
        var failure: Exception? = null
        val reads = mutableListOf<String>()
        val stored = mutableListOf<Pair<String, List<String>>>()

        override suspend fun cookieHeader(url: String): String? {
            failure?.let { throw it }
            reads += url
            return header
        }

        override suspend fun store(url: String, setCookies: List<String>) {
            if (setCookies.isNotEmpty()) stored += url to setCookies
        }
    }
}
