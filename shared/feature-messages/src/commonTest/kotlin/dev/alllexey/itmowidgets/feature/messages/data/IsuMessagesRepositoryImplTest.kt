package dev.alllexey.itmowidgets.feature.messages.data

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.network.IsuWebClient
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.session.ItmoIdCookies
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.feature.messages.domain.AttachmentFiles
import dev.alllexey.itmowidgets.feature.messages.domain.IsuAttachment
import dev.alllexey.itmowidgets.feature.messages.domain.IsuFolder
import dev.alllexey.itmowidgets.testkit.bodyText
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
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

class IsuMessagesRepositoryImplTest {

    private val dispatcher = StandardTestDispatcher()
    private val cookies = FakeCookies("KEYCLOAK_IDENTITY=kc-old")
    private val files = FakeFiles()
    private val requests = mutableListOf<HttpRequestData>()
    private var ownUserId = IsuFixtures.ISU
    private var list: () -> String = { IsuFixtures.PAGE }
    private var keycloakSignsIn = true
    private var logins = 0

    @Test
    fun theLoginReplaysItmoIdAndTheListNamesTheOwnIsu() = runTest(dispatcher) {
        val result = repository().page(IsuFolder.INBOX, 1)

        assertEquals(2, result.value().messages.size)
        // ITMO.ID's cookies went to ITMO.ID only, ISU's to ISU only.
        requests.filter { it.url.host == "id.itmo.ru" }.forEach { assertEquals("KEYCLOAK_IDENTITY=kc-old", it.headers[HttpHeaders.Cookie]) }
        requests.filter { it.url.host == "isu.ifmo.ru" }.forEach { assertFalse("KEYCLOAK" in it.headers[HttpHeaders.Cookie].orEmpty()) }
        assertEquals("ISU_SESSION=s1", requests.last().headers[HttpHeaders.Cookie])
        // The rotated identity went back to the platform store, as the WebView would keep it.
        assertTrue(cookies.stored.single().startsWith("KEYCLOAK_IDENTITY=kc-new"))
        val form = parseQueryString(requests.last().bodyText())
        assertEquals(HttpMethod.Post, requests.last().method)
        assertEquals("999001", form["x01"])
        assertEquals("1", form["x02"])
        assertEquals("4", form["p_flow_step_id"])
        assertEquals("777", form["p_instance"])
        assertEquals("APPLICATION_PROCESS=MAIN_FN", form["p_request"])
        assertFalse(requests.any { "2422:3" in it.url.toString() }, "the request that marks a message read is never sent")
    }

    @Test
    fun theArchiveIsPageEightWithItsFlag() = runTest(dispatcher) {
        val repository = repository()
        repository.page(IsuFolder.INBOX, 1)
        repository.page(IsuFolder.ARCHIVE, 2)

        assertEquals(1, logins, "the APEX session is reused")
        val form = parseQueryString(requests.last().bodyText())
        assertEquals("8", form["p_flow_step_id"])
        assertEquals("1", form["x03"])
        assertEquals("2", form["x02"])
    }

    @Test
    fun aLostSessionLogsInAgainOnce() = runTest(dispatcher) {
        var answers = 0
        list = { if (answers++ == 0) IsuFixtures.LOST else IsuFixtures.PAGE }

        val result = repository().page(IsuFolder.INBOX, 1)

        assertIs<AppResult.Success<*>>(result)
        assertEquals(2, logins)
    }

    @Test
    fun theItmoIdFormMeansTheUserHasToSignIn() = runTest(dispatcher) {
        keycloakSignsIn = false

        assertEquals(AppResult.Failure(AppError.Unauthorized), repository().page(IsuFolder.INBOX, 1))
    }

    @Test
    fun noItmoIdCookiesMeansTheUserHasToSignIn() = runTest(dispatcher) {
        cookies.header = null
        keycloakSignsIn = false

        assertEquals(AppResult.Failure(AppError.Unauthorized), repository().page(IsuFolder.INBOX, 1))
    }

    @Test
    fun anotherAccountOnTheIsuPageIsRefused() = runTest(dispatcher) {
        ownUserId = 999002

        assertEquals(AppResult.Failure(AppError.Forbidden), repository().page(IsuFolder.INBOX, 1))
        assertFalse(requests.any { it.method == HttpMethod.Post }, "no list of another account is asked for")
    }

    @Test
    fun noSignedInUserAsksNothing() = runTest(dispatcher) {
        val repository = repository(user = null)

        assertEquals(AppResult.Failure(AppError.Unauthorized), repository.page(IsuFolder.INBOX, 1))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun aChangedAnswerFailsSoft() = runTest(dispatcher) {
        list = { """{"something": "else"}""" }

        assertIs<AppResult.Failure>(repository().page(IsuFolder.INBOX, 1))
    }

    @Test
    fun theDemoReadsItsOwnMessagesAndNeverTheNetwork() = runTest(dispatcher) {
        val repository = repository(demo = true)

        assertTrue(repository.page(IsuFolder.INBOX, 1).value().messages.isNotEmpty())
        assertEquals(AppResult.Failure(AppError.DemoUnavailable), repository.download(IsuAttachment("0123456789abcdef0123456789abcdef", "a.pdf", "application/pdf")))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun anAttachmentIsDownloadedAndSaved() = runTest(dispatcher) {
        val result = repository().download(IsuAttachment("0123456789abcdef0123456789abcdef", "План/занятий.pdf", "application/pdf"))

        assertEquals("file://saved/План_занятий.pdf", result.value().uri)
        assertContentEquals(PDF, files.bytes)
        assertTrue(requests.last().url.toString().endsWith("f?p=2422:0::DWNLD_F_GEN:NO::FILE:0123456789abcdef0123456789abcdef"))
    }

    @Test
    fun aForeignFileIdIsNeverRequested() = runTest(dispatcher) {
        assertEquals(AppResult.Failure(AppError.NotFound), repository().download(IsuAttachment("../x", "x", null)))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun signOutDropsTheSessionAndTheFile() = runTest(dispatcher) {
        val repository = repository()
        repository.page(IsuFolder.INBOX, 1)

        repository.clearSessionData()
        repository.page(IsuFolder.INBOX, 1)

        assertTrue(files.cleared)
        assertEquals(2, logins)
    }

    private fun TestScope.repository(demo: Boolean = false, user: CurrentUser? = CurrentUser(IsuFixtures.ISU, "A", null)): IsuMessagesRepositoryImpl {
        val dispatchers = AppDispatchers(dispatcher, dispatcher, dispatcher)
        val demoMode = FakeDemoMode(demo)
        val client = IsuWebClient(MockEngine { request -> requests += request; answer(request) }, cookies)
        return IsuMessagesRepositoryImpl(client, FixedUser(user), files, demoMode, dispatchers)
    }

    /** ISU and ITMO.ID as the browser saw them: the portal sends to ITMO.ID, which sends back with a code. */
    private fun MockRequestHandleScope.answer(request: HttpRequestData): HttpResponseData {
        val url = request.url.toString()
        return when {
            url == "https://isu.ifmo.ru/pls/apex/f?p=2143:1" -> {
                logins++
                redirect("https://id.itmo.ru/auth/realms/itmo/protocol/openid-connect/auth?client_id=isu&state=1", "ISU_SESSION=s1; Path=/")
            }
            request.url.host == "id.itmo.ru" -> when {
                keycloakSignsIn && request.headers[HttpHeaders.Cookie] != null ->
                    redirect("/pls/apex/callback?code=c", "KEYCLOAK_IDENTITY=kc-new; Path=/auth/realms/itmo/; Secure; HttpOnly", host = "https://isu.ifmo.ru")
                else -> html(IsuFixtures.KEYCLOAK_FORM)
            }
            url == "https://isu.ifmo.ru/pls/apex/callback?code=c" -> redirect("/pls/apex/f?p=2143:1:777:")
            url == "https://isu.ifmo.ru/pls/apex/f?p=2143:1:777:" -> html("<html>portal</html>")
            url == "https://isu.ifmo.ru/pls/apex/f?p=2422:4:777" -> html(IsuFixtures.inboxPage(ownUserId))
            url == "https://isu.ifmo.ru/pls/apex/wwv_flow.show" -> respond(list(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            url.startsWith("https://isu.ifmo.ru/pls/apex/f?p=2422:0::DWNLD_F_GEN") -> respond(PDF, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/pdf"))
            else -> error("Unexpected request $url")
        }
    }

    private fun MockRequestHandleScope.redirect(location: String, setCookie: String? = null, host: String = ""): HttpResponseData {
        val headers = buildList {
            add(HttpHeaders.Location to listOf(host + location))
            if (setCookie != null) add(HttpHeaders.SetCookie to listOf(setCookie))
        }
        return respond("", HttpStatusCode.Found, headersOf(*headers.toTypedArray()))
    }

    private fun MockRequestHandleScope.html(body: String): HttpResponseData =
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"))

    private class FakeCookies(var header: String?) : ItmoIdCookies {
        val stored = mutableListOf<String>()
        override suspend fun cookieHeader(url: String): String? = header
        override suspend fun store(url: String, setCookies: List<String>) {
            stored += setCookies
        }
    }

    private class FakeFiles : AttachmentFiles {
        var bytes: ByteArray? = null
        var cleared = false
        override suspend fun save(name: String, mimeType: String, bytes: ByteArray): String {
            this.bytes = bytes
            return "file://saved/$name"
        }

        override suspend fun clear() {
            cleared = true
        }
    }

    private class FixedUser(private val user: CurrentUser?) : CurrentUserProvider {
        override suspend fun getCurrentUser(): CurrentUser? = user
    }

    private fun <T> AppResult<T>.value(): T = assertIs<AppResult.Success<T>>(this).value

    private companion object {
        val PDF = "%PDF-1.4 synthetic".encodeToByteArray()
    }
}
