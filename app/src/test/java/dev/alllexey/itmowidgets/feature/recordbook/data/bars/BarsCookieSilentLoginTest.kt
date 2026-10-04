package dev.alllexey.itmowidgets.feature.recordbook.data.bars

import api.bars.Bars
import api.bars.BarsConfiguration
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import java.net.UnknownHostException
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class BarsCookieSilentLoginTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatchers = mainDispatcherRule.appDispatchers

    private val cookie = "SYNTHETIC_SESSION=synthetic-value; SYNTHETIC_ID=synthetic-id"
    private val callback = "https://bars.itmo.ru/rest/login"
    private val server = MockWebServer()
    private val cookies = FakeCookies(cookie)
    private lateinit var bars: Bars
    private lateinit var login: BarsCookieSilentLogin

    @Before fun start() {
        server.start()
        bars = Bars(configuration())
        login = BarsCookieSilentLogin(bars.authHelper, cookies, dispatchers)
    }

    @After fun stop() = server.shutdown()

    private fun configuration() = object : BarsConfiguration.Default() {
        override fun getIssuer() = server.url("/auth/realms/itmo").toString()
    }

    @Test fun `callback with the same state is a code and the answer's cookies go back for the request URL`() = runTest {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "$callback?state=s1&code=synthetic-code")
            .addHeader("Set-Cookie", "SYNTHETIC_SESSION=renewed; Path=/auth/realms/itmo/"))

        val result = login.renew("s1")

        assertTrue(result is BarsCookieRenewal.Code)
        assertEquals("synthetic-code", (result as BarsCookieRenewal.Code).code)
        val loginUrl = bars.authHelper.getLoginUrl("s1")
        assertEquals(listOf(loginUrl), cookies.reads)
        assertEquals(listOf(cookie), server.takeRequest().headers.values("Cookie"))
        assertEquals(1, server.requestCount)
        assertEquals(listOf(loginUrl to listOf("SYNTHETIC_SESSION=renewed; Path=/auth/realms/itmo/")), cookies.stored)
    }

    @Test fun `callback with another state fails and stores only the answer's cookies`() = runTest {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "$callback?state=other&code=synthetic-code")
            .addHeader("Set-Cookie", "SYNTHETIC_SESSION=renewed"))

        assertEquals(BarsCookieRenewal.Failed(AppError.Unknown()), login.renew("s1"))
        assertEquals(listOf("SYNTHETIC_SESSION=renewed"), cookies.stored.flatMap { it.second })
    }

    @Test fun `login page means the session ended`() = runTest {
        server.enqueue(MockResponse().setBody("<html>synthetic login form</html>"))

        assertEquals(BarsCookieRenewal.SessionEnded, login.renew("s1"))
    }

    @Test fun `no cookies mean the session ended without a request`() = runTest {
        cookies.header = null
        assertEquals(BarsCookieRenewal.SessionEnded, login.renew("s1"))
        cookies.header = ""
        assertEquals(BarsCookieRenewal.SessionEnded, login.renew("s1"))

        assertEquals(0, server.requestCount)
        assertTrue(cookies.stored.isEmpty())
    }

    @Test fun `server error is a failure and a stopped server is a network failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        assertEquals(BarsCookieRenewal.Failed(AppError.Unknown()), login.renew("s1"))

        server.shutdown()
        assertEquals(BarsCookieRenewal.Failed(AppError.Network), login.renew("s1"))
    }

    @Test fun `no network before any answer is a network failure and stores no cookies`() = runTest {
        val offline = Bars(configuration(), OkHttpClient.Builder().addInterceptor { throw UnknownHostException("Synthetic") }.build())
        val login = BarsCookieSilentLogin(offline.authHelper, cookies, dispatchers)

        assertEquals(BarsCookieRenewal.Failed(AppError.Network), login.renew("s1"))
        assertEquals(1, cookies.reads.size)
        assertTrue(cookies.stored.isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test fun `unavailable cookie store is a failure without a request`() = runTest {
        cookies.failure = IllegalStateException("WebView is being updated")

        assertEquals(BarsCookieRenewal.Failed(AppError.Unknown()), login.renew("s1"))
        assertEquals(0, server.requestCount)
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
