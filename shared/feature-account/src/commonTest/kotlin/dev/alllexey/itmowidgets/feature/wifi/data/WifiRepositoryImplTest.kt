package dev.alllexey.itmowidgets.feature.wifi.data

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.network.IsuWebClient
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.session.ItmoIdCookies
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.InMemorySecureStore
import dev.alllexey.itmowidgets.feature.wifi.domain.WifiAccess
import dev.alllexey.itmowidgets.feature.wifi.domain.WifiCredentials
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.errors.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

class WifiRepositoryImplTest {

    private val dispatcher = StandardTestDispatcher()
    private val store = InMemorySecureStore()
    private var wifiPage: () -> String = { page("12345678") }
    private var requests = 0

    @Test
    fun readsIsuAndKeepsTheCredentialsForTheOwner() = runTest(dispatcher) {
        val repository = repository()

        assertEquals(AppResult.Success(WifiAccess.Ready(CREDENTIALS)), repository.refresh())
        assertEquals(CREDENTIALS, repository.cached())
        assertEquals("123456\n123456\n12345678", store.values[NAME])
        assertEquals(null, repository(isu = 654321).cached(), "another account never sees them")
    }

    @Test
    fun aPageWithoutAPasswordForgetsTheStoredOne() = runTest(dispatcher) {
        val repository = repository()
        repository.refresh()
        wifiPage = { page(null) }

        assertEquals(AppResult.Success(WifiAccess.NoPassword), repository.refresh())
        assertNull(repository.cached())
    }

    @Test
    fun noSsoCookiesNeedTheUserAndKeepWhatIsStored() = runTest(dispatcher) {
        repository().refresh()

        val withoutCookies = repository(cookie = null)

        assertEquals(AppResult.Success(WifiAccess.SignInRequired), withoutCookies.refresh())
        assertEquals(CREDENTIALS, withoutCookies.cached())
    }

    @Test
    fun noNetworkIsANetworkError() = runTest(dispatcher) {
        wifiPage = { throw IOException("offline") }

        assertEquals(AppResult.Failure(AppError.Network), repository().refresh())
    }

    @Test
    fun theDemoAnswersItsOwnAccountWithoutTheNetwork() = runTest(dispatcher) {
        val demo = repository(demo = true)

        assertEquals(AppResult.Success(WifiAccess.Ready(DemoWifi.CREDENTIALS)), demo.refresh())
        assertEquals(DemoWifi.CREDENTIALS, demo.cached())
        assertEquals(0, requests)
    }

    @Test
    fun signOutDeletesTheCredentials() = runTest(dispatcher) {
        val repository = repository()
        repository.refresh()

        repository.clearSessionData()

        assertTrue(NAME !in store.values)
        assertNull(repository.cached())
    }

    private fun repository(isu: Int = 123456, cookie: String? = "KEYCLOAK_IDENTITY=sso", demo: Boolean = false) =
        WifiRepositoryImpl(
            IsuWebClient(engine(), Cookies(cookie)),
            store,
            object : CurrentUserProvider {
                override suspend fun getCurrentUser() = CurrentUser(isu = isu, name = null, pictureUrl = null)
            },
            FakeDemoMode(demo),
            AppDispatchers(io = dispatcher, default = dispatcher, main = dispatcher),
        )

    /** ISU signs in at once with the SSO cookie and serves [wifiPage] in session 777. */
    private fun engine() = MockEngine { request ->
        requests++
        val url = request.url.toString()
        when {
            url == "$APEX/f?p=2143:1" -> respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, AUTH))
            url.startsWith(AUTH) -> respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "$APEX/f?p=2143:1:777"))
            url == "$APEX/f?p=2143:1:777" -> respond("portal")
            url == "$APEX/f?p=2156:6:777" -> respond(wifiPage())
            else -> error("Unexpected $url")
        }
    }

    private class Cookies(private val header: String?) : ItmoIdCookies {
        override suspend fun cookieHeader(url: String): String? = header

        override suspend fun store(url: String, setCookies: List<String>) = Unit
    }

    private fun page(password: String?): String {
        val span = password?.let { """<span class="wifi-pass wifi-pass-hidden text-bg">$it</span>""" }.orEmpty()
        return """<input type="hidden" value="123456" id="USER_ID" /><span id="P6_USERNAME" class="display_only text-bg">123456</span>""" +
            """<span id="P6_PASS_CURRENT" class="display_only" >$span</span></div><div><label for="P6_PASS_NEW">"""
    }

    private companion object {
        const val NAME = "wifi_credentials.enc"
        const val APEX = "https://isu.ifmo.ru/pls/apex"
        const val AUTH = "https://id.itmo.ru/auth/realms/itmo/protocol/openid-connect/auth?client_id=isu"
        val CREDENTIALS = WifiCredentials("123456", "12345678")
    }
}
