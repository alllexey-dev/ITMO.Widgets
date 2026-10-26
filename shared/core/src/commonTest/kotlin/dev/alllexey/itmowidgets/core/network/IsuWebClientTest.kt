package dev.alllexey.itmowidgets.core.network

import dev.alllexey.itmowidgets.core.session.ItmoIdCookies
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.errors.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** The ISU sign-in over synthetic redirects: which cookie goes where, and when the user is needed. */
class IsuWebClientTest {

    private val cookies = FakeCookies("KEYCLOAK_IDENTITY=sso")
    private val requests = mutableListOf<HttpRequestData>()

    @Test
    fun signsInThroughItmoIdAndReadsAPageInTheSameSession() = runTest {
        val client = client { request ->
            when {
                request.url.toString() == "$APEX/f?p=2143:1" ->
                    redirect("$AUTH?client_id=isu", setCookie = "ORA_WWV_APP_2143=apex; Path=/; Secure")
                request.url.host == "id.itmo.ru" ->
                    redirect("$APEX/f?p=2143:1:777", setCookie = "KEYCLOAK_SESSION=rotated; Path=/auth/realms/itmo/")
                request.url.toString() == "$APEX/f?p=2143:1:777" -> respond("<html>portal</html>")
                request.url.toString() == "$APEX/f?p=2156:6:777" -> respond("<html>wifi</html>")
                else -> error("Unexpected ${request.url}")
            }
        }

        val session = client.signIn()
        val page = session?.page(2156, "6")

        assertEquals("<html>wifi</html>", page)
        assertEquals(listOf(null, "KEYCLOAK_IDENTITY=sso", "ORA_WWV_APP_2143=apex", "ORA_WWV_APP_2143=apex"), requests.map { it.headers[HttpHeaders.Cookie] })
        val stored = cookies.stored.single()
        assertTrue(stored.startsWith("KEYCLOAK_SESSION=rotated") && "Path=/auth/realms/itmo/" in stored && "\$" !in stored, stored)
        assertTrue(requests.all { it.headers[HttpHeaders.UserAgent].orEmpty().startsWith("Mozilla/5.0") })
    }

    @Test
    fun withoutSsoCookiesItAsksNothingOfItmoIdAndNeedsTheUser() = runTest {
        cookies.header = null
        val client = client { redirect("$AUTH?client_id=isu") }

        assertNull(client.signIn())
        assertEquals(1, requests.size, "no request reaches ITMO.ID without its cookies")
    }

    @Test
    fun itmoIdsSignInFormNeedsTheUser() = runTest {
        val client = client { request ->
            if (request.url.host == "isu.ifmo.ru") redirect("$AUTH?client_id=isu")
            else respond("<form id=\"kc-form-login\"></form>")
        }

        assertNull(client.signIn())
    }

    @Test
    fun aPageThatEndsOutsideTheAppIsALostSession() = runTest {
        val client = client { request ->
            when (request.url.toString()) {
                "$APEX/f?p=2143:1" -> redirect("$APEX/f?p=2143:1:777")
                "$APEX/f?p=2143:1:777", "$APEX/f?p=2143:1:778" -> respond("portal")
                else -> redirect("f?p=2143:1:778")
            }
        }

        assertNull(client.signIn()?.page(2156, "6"))
    }

    @Test
    fun anotherHostOrPlainHttpIsRefused() = runTest {
        assertFailsWith<IsuException> { client { redirect("https://example.com/") }.signIn() }
        assertFailsWith<IsuException> { client { redirect("http://isu.ifmo.ru/pls/apex/f?p=2143:1:1") }.signIn() }
    }

    @Test
    fun aServerErrorFailsAndANetworkFailureIsThrownAsItIs() = runTest {
        assertFailsWith<IsuException> { client { respond("busy", HttpStatusCode.ServiceUnavailable) }.signIn() }
        assertFailsWith<IOException> { client { throw IOException("offline") }.signIn() }
    }

    @Test
    fun aRedirectLoopEnds() = runTest {
        assertFailsWith<IsuException> { client { redirect("$APEX/f?p=2143:1") }.signIn() }
    }

    private fun client(answer: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): IsuWebClient =
        IsuWebClient(MockEngine { request -> requests += request; answer(request) }, cookies)

    private fun MockRequestHandleScope.redirect(location: String, setCookie: String? = null): HttpResponseData {
        val headers = listOfNotNull(HttpHeaders.Location to listOf(location), setCookie?.let { HttpHeaders.SetCookie to listOf(it) })
        return respond("", HttpStatusCode.Found, headersOf(*headers.toTypedArray()))
    }

    private class FakeCookies(var header: String?) : ItmoIdCookies {
        val stored = mutableListOf<String>()

        override suspend fun cookieHeader(url: String): String? = header?.takeIf { url.startsWith("https://id.itmo.ru/") }

        override suspend fun store(url: String, setCookies: List<String>) {
            stored += setCookies
        }
    }

    private companion object {
        const val APEX = "https://isu.ifmo.ru/pls/apex"
        const val AUTH = "https://id.itmo.ru/auth/realms/itmo/protocol/openid-connect/auth"
    }
}
