package dev.alllexey.itmowidgets.feature.connections.data

import dev.alllexey.itmoapi.itmoid.InMemoryTokenStorage
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmoapi.myitmo.MyItmoConfiguration
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.storage.SignInLabelPreferences
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.InMemoryPreferencesDataStore
import dev.alllexey.itmowidgets.feature.connections.domain.ClientSignInAttempt
import dev.alllexey.itmowidgets.feature.connections.domain.ClientSignInResult
import dev.alllexey.itmowidgets.feature.connections.domain.ItmoGrants
import dev.alllexey.itmowidgets.feature.connections.presentation.FakeItmoLogins
import dev.alllexey.itmowidgets.feature.connections.presentation.Samples
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * The label of a client's sign-in (plan 2.2): an ordinary browser unless the switch is turned off; and the finish:
 * the code exchanged, the account checked and, when asked, the session's `KEYCLOAK_IDENTITY` checked (ADR 0040).
 */
class ClientSignInImplTest {

    private val dispatcher = StandardTestDispatcher()
    private val label = SignInLabelPreferences(InMemoryPreferencesDataStore())
    private val requests = mutableListOf<String>()
    private val logouts = mutableListOf<String?>()
    private val configuration = MyItmoConfiguration.DEFAULT
    private val dev = MyItmoConfiguration.DEV.itmoId

    /** The token answer per ITMO OIDC client; the dev client's carries [devIdToken]. */
    private var devIdToken = jwt("""{"isu":100001,"sid":"sid-new"}""")
    private val engine = MockEngine { request ->
        val url = request.url.toString()
        requests += url
        val client = (request.body as? FormDataContent)?.formData?.get("client_id")
        when {
            url.startsWith(configuration.itmoId.tokenEndpoint) -> respond(
                if (client == dev.clientId) tokens("test-dev-refresh-token", devIdToken) else TOKENS,
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
            url.startsWith(configuration.itmoId.logoutEndpoint) -> {
                logouts += client
                respond("", HttpStatusCode.NoContent)
            }
            else -> respond("", HttpStatusCode.NoContent)
        }
    }
    private val signIn = ClientSignInImpl(
        oidc = ItmoOidcClients(
            MyItmoClient(configuration, storage = InMemoryTokenStorage(), engine = engine, clock = Clock.System),
            engine,
            Clock.System,
        ),
        logins = FakeItmoLogins(),
        users = object : CurrentUserProvider {
            override suspend fun getCurrentUser() = CurrentUser(isu = 100001, name = null, pictureUrl = null)
        },
        label = label,
        clock = Clock.System,
        demo = FakeDemoMode(),
        dispatchers = AppDispatchers(io = dispatcher, default = dispatcher, main = dispatcher),
    )

    @Test
    fun byDefaultTheClientSignsInAsAnOrdinaryBrowser() = runTest(dispatcher) {
        val attempt = signIn.begin(Samples.request(), MY_ITMO, withIdentity = false)

        assertTrue("Chrome/" in attempt.userAgent && "(X11; Linux x86_64)" in attempt.userAgent, attempt.userAgent)
        assertFalse("bot" in attempt.userAgent)
        assertTrue(attempt.url.startsWith("https://id.itmo.ru/"))
    }

    @Test
    fun theSwitchOffSignsInWithTheHonestLabel() = runTest(dispatcher) {
        label.setBrowserLike(false)

        val attempt = signIn.begin(Samples.request(), MY_ITMO, withIdentity = false)

        assertTrue(attempt.userAgent.startsWith("ITMO Widgets agent nas bot/1.2"), attempt.userAgent)
    }

    @Test
    fun withoutIdentityTheCookieIsIgnored() = runTest(dispatcher) {
        val attempt = signIn.begin(Samples.request(), MY_ITMO, withIdentity = false)

        val session = assertIs<ClientSignInResult.Success>(attempt.finish(callback(attempt), cookie("sid-new"))).session

        assertEquals("sid-new", session.sid)
        assertNull(session.identity)
    }

    @Test
    fun theCookieOfTheSameSessionIsTakenWithItsExp() = runTest(dispatcher) {
        val attempt = signIn.begin(Samples.mcpRequest(), MY_ITMO, withIdentity = true)

        val session = assertIs<ClientSignInResult.Success>(attempt.finish(callback(attempt), cookie("sid-new"))).session

        assertEquals(cookie("sid-new"), session.identity?.value)
        assertEquals(Instant.fromEpochSeconds(EXP), session.identity?.expiresAt)
        assertFalse(cookie("sid-new") in session.identity.toString())
        assertTrue(requests.none { it.startsWith(configuration.itmoId.logoutEndpoint) })
    }

    @Test
    fun aMissingOrForeignCookieEndsTheNewSession() = runTest(dispatcher) {
        for (value in listOf(null, cookie("sid-other"), "not-a-jwt")) {
            requests.clear()
            val attempt = signIn.begin(Samples.mcpRequest(), MY_ITMO, withIdentity = true)

            assertEquals(ClientSignInResult.IdentityMissing, attempt.finish(callback(attempt), value), value)
            assertTrue(requests.any { it.startsWith(configuration.itmoId.logoutEndpoint) }, value)
        }
    }

    @Test
    fun aFurtherClientIsASilentAuthorizeInTheSamePageWhoseTokenJoinsTheSession() = runTest(dispatcher) {
        val attempt = signIn.begin(Samples.mcpRequest(), BOTH, withIdentity = false)
        assertFalse(attempt.lastStep)
        assertTrue("prompt" !in attempt.url)

        val next = assertIs<ClientSignInResult.Next>(attempt.finish(callback(attempt), null))
        val silent = Url(next.url)
        assertEquals("none", silent.parameters["prompt"])
        assertEquals(dev.clientId, silent.parameters["client_id"])
        assertTrue(attempt.lastStep)
        assertFalse(attempt.isCallback(callback(attempt)))
        assertTrue(attempt.isCallback(callback(next.url, dev.redirectUri)))

        val session = assertIs<ClientSignInResult.Success>(attempt.finish(callback(next.url, dev.redirectUri), null)).session

        assertEquals("sid-new", session.sid)
        assertEquals("test-refresh-token", session.refreshFor(ItmoGrants.MY_ITMO)?.value)
        assertEquals("test-dev-refresh-token", session.refreshFor(ItmoGrants.MY_ITMO_DEV)?.value)
        assertFalse("test-dev-refresh-token" in session.refreshFor(ItmoGrants.MY_ITMO_DEV).toString())
        assertTrue(logouts.isEmpty())
    }

    @Test
    fun anotherSessionOrAccountInTheSilentAuthorizeLogsOutEveryMintedToken() = runTest(dispatcher) {
        for (idToken in listOf(jwt("""{"isu":100001,"sid":"sid-other"}"""), jwt("""{"isu":100002,"sid":"sid-new"}"""))) {
            logouts.clear()
            devIdToken = idToken
            val attempt = signIn.begin(Samples.mcpRequest(), BOTH, withIdentity = false)
            val next = assertIs<ClientSignInResult.Next>(attempt.finish(callback(attempt), null))

            assertEquals(ClientSignInResult.SessionMismatch, attempt.finish(callback(next.url, dev.redirectUri), null))
            assertEquals(listOf<String?>(configuration.itmoId.clientId, dev.clientId), logouts)
        }
    }

    @Test
    fun loginRequiredInTheSilentAuthorizeLogsOutTheFirstToken() = runTest(dispatcher) {
        val attempt = signIn.begin(Samples.mcpRequest(), BOTH, withIdentity = false)
        val next = assertIs<ClientSignInResult.Next>(attempt.finish(callback(attempt), null))
        val state = Url(next.url).parameters["state"]

        val result = attempt.finish("${dev.redirectUri}?error=login_required&state=$state", null)

        assertEquals(ClientSignInResult.SessionMismatch, result)
        assertEquals(listOf<String?>(configuration.itmoId.clientId), logouts)
    }

    @Test
    fun anAbandonedAttemptLogsOutWhatItMinted() = runTest(dispatcher) {
        val attempt = signIn.begin(Samples.mcpRequest(), BOTH, withIdentity = false)
        assertIs<ClientSignInResult.Next>(attempt.finish(callback(attempt), null))

        attempt.abandon()
        attempt.abandon()

        assertEquals(listOf<String?>(configuration.itmoId.clientId), logouts)
    }

    @Test
    fun onlyTheDevClientSignsInOnItsOwnPage() = runTest(dispatcher) {
        val attempt = signIn.begin(Samples.mcpRequest(), listOf(ItmoGrants.MY_ITMO_DEV), withIdentity = false)
        assertEquals(dev.clientId, Url(attempt.url).parameters["client_id"])
        assertTrue(attempt.lastStep)

        val session = assertIs<ClientSignInResult.Success>(attempt.finish(callback(attempt.url, dev.redirectUri), null)).session

        assertEquals(ItmoGrants.MY_ITMO_DEV, session.oidcClient)
        assertEquals("test-dev-refresh-token", session.refreshFor(ItmoGrants.MY_ITMO_DEV)?.value)
        assertNull(session.refreshFor(ItmoGrants.MY_ITMO))
    }

    private fun callback(attempt: ClientSignInAttempt): String = callback(attempt.url, configuration.itmoId.redirectUri)

    private fun callback(url: String, redirect: String): String {
        val state = Url(url).parameters["state"]
        return "$redirect?code=test-code&state=$state"
    }

    private companion object {
        val MY_ITMO = listOf(ItmoGrants.MY_ITMO)
        val BOTH = listOf(ItmoGrants.MY_ITMO, ItmoGrants.MY_ITMO_DEV)
        const val EXP = 1_800_000_000L
        val BASE64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

        fun jwt(payload: String) = "eyJhbGciOiJSUzI1NiJ9.${BASE64.encode(payload.encodeToByteArray())}.c2ln"

        fun cookie(sid: String) = jwt("""{"sid":"$sid","exp":$EXP,"typ":"Serialized-ID"}""")

        fun tokens(refresh: String, idToken: String) = """{"access_token":"test-access","expires_in":300,
            "refresh_token":"$refresh","refresh_expires_in":2592000,"id_token":"$idToken"}"""

        val TOKENS = tokens("test-refresh-token", jwt("""{"isu":100001,"sid":"sid-new"}"""))
    }
}
