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
import dev.alllexey.itmowidgets.feature.connections.presentation.FakeItmoLogins
import dev.alllexey.itmowidgets.feature.connections.presentation.Samples
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
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
    private val configuration = MyItmoConfiguration.DEFAULT
    private val signIn = ClientSignInImpl(
        myItmo = MyItmoClient(
            configuration,
            storage = InMemoryTokenStorage(),
            engine = MockEngine { request ->
                val url = request.url.toString()
                requests += url
                when {
                    url.startsWith(configuration.itmoId.tokenEndpoint) -> respond(
                        TOKENS,
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                    else -> respond("", HttpStatusCode.NoContent)
                }
            },
            clock = Clock.System,
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
        val attempt = signIn.begin(Samples.request(), withIdentity = false)

        assertTrue("Chrome/" in attempt.userAgent && "(X11; Linux x86_64)" in attempt.userAgent, attempt.userAgent)
        assertFalse("bot" in attempt.userAgent)
        assertTrue(attempt.url.startsWith("https://id.itmo.ru/"))
    }

    @Test
    fun theSwitchOffSignsInWithTheHonestLabel() = runTest(dispatcher) {
        label.setBrowserLike(false)

        val attempt = signIn.begin(Samples.request(), withIdentity = false)

        assertTrue(attempt.userAgent.startsWith("ITMO Widgets agent nas bot/1.2"), attempt.userAgent)
    }

    @Test
    fun withoutIdentityTheCookieIsIgnored() = runTest(dispatcher) {
        val attempt = signIn.begin(Samples.request(), withIdentity = false)

        val session = assertIs<ClientSignInResult.Success>(attempt.finish(callback(attempt), cookie("sid-new"))).session

        assertEquals("sid-new", session.sid)
        assertNull(session.identity)
    }

    @Test
    fun theCookieOfTheSameSessionIsTakenWithItsExp() = runTest(dispatcher) {
        val attempt = signIn.begin(Samples.mcpRequest(), withIdentity = true)

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
            val attempt = signIn.begin(Samples.mcpRequest(), withIdentity = true)

            assertEquals(ClientSignInResult.IdentityMissing, attempt.finish(callback(attempt), value), value)
            assertTrue(requests.any { it.startsWith(configuration.itmoId.logoutEndpoint) }, value)
        }
    }

    private fun callback(attempt: ClientSignInAttempt): String {
        val state = Url(attempt.url).parameters["state"]
        return "${configuration.itmoId.redirectUri}?code=test-code&state=$state"
    }

    private companion object {
        const val EXP = 1_800_000_000L
        val BASE64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

        fun jwt(payload: String) = "eyJhbGciOiJSUzI1NiJ9.${BASE64.encode(payload.encodeToByteArray())}.c2ln"

        fun cookie(sid: String) = jwt("""{"sid":"$sid","exp":$EXP,"typ":"Serialized-ID"}""")

        val TOKENS = """{"access_token":"test-access","expires_in":300,"refresh_token":"test-refresh-token",
            "refresh_expires_in":2592000,"id_token":"${jwt("""{"isu":100001,"sid":"sid-new"}""")}"}"""
    }
}
