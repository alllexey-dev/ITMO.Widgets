package dev.alllexey.itmowidgets.feature.recordbook.data.bars

import dev.alllexey.itmoapi.bars.auth.BarsLogin
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.testing.InMemorySecureStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest

/** The Keychain copy of the ITMO.ID cookies and the replay over it (SP-21); every cookie is synthetic. */
class KeychainItmoIdCookiesTest {

    private val secrets = InMemorySecureStore()
    private val clock = MovableClock(Instant.fromEpochSeconds(1_800_000_000))
    private val dispatchers = AppDispatchers(Dispatchers.Default, Dispatchers.Default, Dispatchers.Default)
    private val cookies = KeychainItmoIdCookies(secrets, clock, dispatchers)
    private val requests = mutableListOf<HttpRequestData>()

    @Test
    fun theWebKitCopyKeepsItmoIdCookiesAndTheReplaySendsThemToTheAuthorizationUrlOnly() = runTest {
        cookies.replaceFromWebKit(
            listOf(
                webKit("KEYCLOAK_IDENTITY", "identity-1", "id.itmo.ru", "/auth/realms/itmo/", expiresIn = 90.days),
                webKit("KEYCLOAK_SESSION", "session-1", ".id.itmo.ru", "/auth/realms/itmo/", expiresIn = 90.days),
                webKit("AUTH_SESSION_ID", "auth-1", "id.itmo.ru", "/auth/realms/itmo/", expiresIn = null),
                webKit("_ym_uid", "metrica", ".itmo.ru", "/", expiresIn = 365.days),
                webKit("KC_AUTH_SESSION_HASH", "stale", "id.itmo.ru", "/auth/realms/itmo/", expiresIn = (-1).hours),
                webKit("MY_ITMO", "other-host", "my.itmo.ru", "/", expiresIn = 1.days),
            )
        )
        val login = BarsLogin(MockEngine { request ->
            requests += request
            val state = request.url.parameters["state"]
            respond(
                content = "",
                status = HttpStatusCode.Found,
                headers = headersOf(
                    HttpHeaders.Location to listOf("$REDIRECT?state=$state&code=synthetic-code"),
                    HttpHeaders.SetCookie to listOf(
                        "KC_RESTART=; Path=/auth/realms/itmo/; Max-Age=0; HttpOnly",
                        "KC_RESTART=restart-2; Path=/auth/realms/itmo/; HttpOnly",
                        "KEYCLOAK_IDENTITY=identity-2; Path=/auth/realms/itmo/; Max-Age=7776000; Secure; HttpOnly",
                        "FOREIGN=x; Domain=example.com; Path=/",
                    ),
                ),
            )
        })

        val renewal = BarsCookieSilentLogin(login, cookies).renew("synthetic-state")

        assertEquals("Code", assertIs<BarsCookieRenewal.Code>(renewal).toString())
        assertEquals("synthetic-code", renewal.code)
        val request = requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals(login.loginUrl("synthetic-state"), request.url.toString())
        assertEquals(
            setOf("KEYCLOAK_IDENTITY=identity-1", "KEYCLOAK_SESSION=session-1", "AUTH_SESSION_ID=auth-1"),
            request.headers[HttpHeaders.Cookie]?.split("; ")?.toSet()
        )
        // The answer's cookies replace the copy's, the last of a name winning; a foreign Domain is ignored.
        assertEquals(
            setOf(
                "KEYCLOAK_IDENTITY=identity-2",
                "KEYCLOAK_SESSION=session-1",
                "AUTH_SESSION_ID=auth-1",
                "KC_RESTART=restart-2",
            ),
            cookies.cookieHeader(login.loginUrl("next"))?.split("; ")?.toSet()
        )
        assertTrue(secrets.values.getValue(KeychainItmoIdCookies.ITEM).startsWith("{\"version\":1,"))
    }

    @Test
    fun theHeaderMatchesHostPathSecureAndExpiry() = runTest {
        cookies.replaceFromWebKit(
            listOf(
                webKit("ROOT", "root", "id.itmo.ru", "/", expiresIn = 1.days, secure = false),
                webKit("REALM", "realm", "id.itmo.ru", "/auth/realms/itmo/", expiresIn = 2.days),
                webKit("SHORT", "short", ".id.itmo.ru", "/auth", expiresIn = 1.hours),
            )
        )

        assertEquals(
            "REALM=realm; SHORT=short; ROOT=root",
            cookies.cookieHeader("$ISSUER/protocol/openid-connect/auth")
        )
        assertEquals("ROOT=root", cookies.cookieHeader("https://id.itmo.ru/authx"))
        // Host-only cookies never reach a subdomain; Domain cookies do.
        assertEquals("SHORT=short", cookies.cookieHeader("https://sso.id.itmo.ru/auth/x"))
        assertNull(cookies.cookieHeader("http://id.itmo.ru/"))
        assertNull(cookies.cookieHeader("https://bars.itmo.ru/"))

        clock.now += 3.hours
        assertEquals("REALM=realm; ROOT=root", cookies.cookieHeader("$ISSUER/x"))
    }

    @Test
    fun anEmptyCopyRemovesTheItemAndAnUnknownVersionReadsAsNoCookies() = runTest {
        cookies.replaceFromWebKit(
            listOf(webKit("KEYCLOAK_IDENTITY", "identity", "id.itmo.ru", "/", expiresIn = 1.days))
        )
        assertTrue(KeychainItmoIdCookies.ITEM in secrets.values)

        cookies.replaceFromWebKit(listOf(webKit("_ym_uid", "metrica", ".itmo.ru", "/", expiresIn = 1.days)))
        assertTrue(KeychainItmoIdCookies.ITEM !in secrets.values)

        secrets.values[KeychainItmoIdCookies.ITEM] = """{"version":2,"cookies":[]}"""
        assertNull(cookies.cookieHeader("$ISSUER/x"))
        secrets.values[KeychainItmoIdCookies.ITEM] = "not json"
        assertNull(cookies.cookieHeader("$ISSUER/x"))
    }

    @Test
    fun withoutACopyTheReplaySendsNothingAndTheSessionEnded() = runTest {
        val login = BarsLogin(MockEngine { request ->
            requests += request
            respond("", HttpStatusCode.InternalServerError)
        })

        assertEquals(BarsCookieRenewal.SessionEnded, BarsCookieSilentLogin(login, cookies).renew("synthetic-state"))
        assertEquals(emptyList(), requests)
    }

    private fun webKit(
        name: String,
        value: String,
        domain: String,
        path: String,
        expiresIn: Duration?,
        secure: Boolean = true,
    ) = WebKitCookie(name, value, domain, path, secure, expiresIn?.let { (clock.now + it).toEpochMilliseconds() })

    private class MovableClock(var now: Instant) : Clock {
        override fun now(): Instant = now
    }

    private companion object {
        const val ISSUER = "https://id.itmo.ru/auth/realms/itmo"
        val REDIRECT = BarsLogin(MockEngine { respond("") }).configuration.redirectUri
    }
}
