package dev.alllexey.itmowidgets.core.network

import dev.alllexey.itmowidgets.core.session.ItmoIdCookies
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.parseQueryString
import io.ktor.utils.io.errors.IOException
import io.ktor.http.content.OutgoingContent
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
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
    fun anIsuCookieWithoutPathReachesOtherApexAddresses() = runTest {
        val client = client { request ->
            when (request.url.encodedPath) {
                "/pls/apex/f" -> if (request.url.encodedQuery == "p=2143:1") {
                    redirect("$APEX/apex_authentication.callback", setCookie = "ISU_AP_COOKIE=apex; Secure")
                } else {
                    respond("<html>portal</html>")
                }
                "/pls/apex/apex_authentication.callback" -> redirect("$APEX/f?p=2143:1:777")
                else -> error("Unexpected ${request.url}")
            }
        }

        assertEquals("777", client.signIn()?.id)
        assertEquals(listOf(null, "ISU_AP_COOKIE=apex", "ISU_AP_COOKIE=apex"), requests.map { it.headers[HttpHeaders.Cookie] })
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

    @Test
    fun aProcessIsPostedInTheSessionAndAPageInsteadIsALostSession() = runTest {
        var lost = false
        val client = client { request ->
            when (request.url.toString()) {
                "$APEX/f?p=2143:1" -> redirect("$APEX/f?p=2143:1:777", setCookie = "ORA_WWV_APP_2422=apex; Path=/; Secure")
                "$APEX/f?p=2143:1:777" -> respond("<html>portal</html>")
                "$APEX/wwv_flow.show" ->
                    if (lost) respond("<html>session ended</html>") else respond("{\"incoming\": []}", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                else -> error("Unexpected ${request.url}")
            }
        }
        val session = assertNotNull(client.signIn())

        val answer = session.process(2422, 4, "MAIN_FN", listOf("999001", "1"))
        lost = true

        assertEquals("{\"incoming\": []}", answer)
        assertNull(session.process(2422, 4, "MAIN_FN", listOf("999001", "2")))
        val post = requests[2]
        assertEquals(HttpMethod.Post, post.method)
        assertEquals("ORA_WWV_APP_2422=apex", post.headers[HttpHeaders.Cookie])
        val form = parseQueryString((post.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
        assertEquals("2422", form["p_flow_id"])
        assertEquals("4", form["p_flow_step_id"])
        assertEquals("777", form["p_instance"])
        assertEquals("APPLICATION_PROCESS=MAIN_FN", form["p_request"])
        assertEquals(listOf("999001", "1"), listOf(form["x01"], form["x02"]))
    }

    @Test
    fun aDownloadFollowsApexAndRefusesAPageOrTooMuch() = runTest {
        val pdf = "%PDF-1.4 synthetic".encodeToByteArray()
        val client = client { request ->
            when (request.url.toString()) {
                "$APEX/f?p=2143:1" -> redirect("$APEX/f?p=2143:1:777")
                "$APEX/f?p=2143:1:777" -> respond("<html>portal</html>")
                "$APEX/f?p=2422:0::DWNLD_F_GEN:NO::FILE:aa" -> redirect("f?p=2422:0:777:DWNLD_F_GEN:NO::FILE:aa")
                "$APEX/f?p=2422:0:777:DWNLD_F_GEN:NO::FILE:aa" -> respond(pdf, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/pdf"))
                "$APEX/f?p=2422:0::DWNLD_F_GEN:NO::FILE:bb" -> respond("<html>login</html>", headers = headersOf(HttpHeaders.ContentType, "text/html"))
                else -> error("Unexpected ${request.url}")
            }
        }
        val session = assertNotNull(client.signIn())

        assertContentEquals(pdf, session.download("f?p=2422:0::DWNLD_F_GEN:NO::FILE:aa", 1024))
        assertNull(session.download("f?p=2422:0::DWNLD_F_GEN:NO::FILE:bb", 1024))
        assertFailsWith<IsuException> { session.download("f?p=2422:0::DWNLD_F_GEN:NO::FILE:aa", 4) }
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
