package dev.alllexey.itmowidgets.feature.recordbook.data.bars

import dev.alllexey.itmoapi.bars.auth.BarsLogin
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.testing.InMemorySecureStore
import dev.alllexey.itmowidgets.core.testing.RecordingAppLog
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/** The hidden-WebView renewal over a scripted [HiddenBarsWebView]: Kotlin keeps the callback, state and time limit. */
@OptIn(ExperimentalCoroutinesApi::class)
class WebViewBarsSilentLoginTest {

    private val login = BarsLogin(MockEngine { respond("") })
    private val web = ScriptedWebView()
    private val secrets = InMemorySecureStore()

    @Test
    fun theExactCallbackWithTheSameStateYieldsTheCode() = runScenario { silentLogin ->
        web.answer = { url -> "${login.configuration.redirectUri}?state=${state(url)}&code=synthetic-code" }

        assertEquals("synthetic-code", silentLogin.authorizationCode("synthetic-state"))
        assertEquals(listOf(login.loginUrl("synthetic-state")), web.loads)
    }

    @Test
    fun aCallbackWithAnotherStateIsRejected() = runScenario { silentLogin ->
        web.answer = { "${login.configuration.redirectUri}?state=another-state&code=synthetic-code" }

        assertNull(silentLogin.authorizationCode("synthetic-state"))
    }

    @Test
    fun aSignInFormOrAStoppedFlowYieldsNoCode() = runScenario { silentLogin ->
        web.answer = { null }

        assertNull(silentLogin.authorizationCode("synthetic-state"))
    }

    @Test
    fun aFlowThatDoesNotFinishInTimeIsCancelled() = runScenario { silentLogin ->
        web.answer = ScriptedWebView.NEVER

        assertNull(silentLogin.authorizationCode("synthetic-state"))
        assertEquals(1, web.cancels)
    }

    @Test
    fun everyRunCopiesTheCookiesItmoIdMayHaveReissued() = runScenario { silentLogin ->
        web.answer = { null }
        web.cookies = listOf(WebKitCookie("KEYCLOAK_IDENTITY", "identity", "id.itmo.ru", "/", true, null))

        silentLogin.authorizationCode("synthetic-state")

        assertTrue(KeychainItmoIdCookies.ITEM in secrets.values)
    }

    @Test
    fun theNavigationFollowsTheBarsLoginChecks() {
        val navigation = BarsWebNavigation(login)

        assertEquals(BarsWebStep.ALLOW, navigation.step(login.loginUrl("synthetic-state")))
        assertEquals(BarsWebStep.CALLBACK, navigation.step("${login.configuration.redirectUri}?state=x&code=y"))
        assertEquals(BarsWebStep.STOP, navigation.step("https://example.com/"))
        assertEquals(BarsWebStep.STOP, navigation.step("http://id.itmo.ru/auth/realms/itmo/"))
        assertEquals(BarsWebStep.STOP, navigation.step("https://bars.itmo.ru/other"))
    }

    private fun runScenario(block: suspend TestScope.(WebViewBarsSilentLogin) -> Unit) = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = AppDispatchers(dispatcher, dispatcher, dispatcher)
        val export = ItmoIdCookieExport(
            web,
            KeychainItmoIdCookies(secrets, Clock.System, dispatchers),
            dispatchers,
            RecordingAppLog(),
        )
        block(WebViewBarsSilentLogin(web, login, export, dispatchers, 20.seconds))
    }

    private fun state(url: String): String = url.substringAfter("state=").substringBefore('&')

    /** Answers each load at once with [answer]'s callback URL, or never for [NEVER]. */
    private class ScriptedWebView : HiddenBarsWebView, WebKitCookieSource {
        var answer: ((String) -> String?)? = null
        var cookies: List<WebKitCookie> = emptyList()
        val loads = mutableListOf<String>()
        var cancels = 0

        override fun loadHiddenBarsPage(
            url: String,
            navigation: BarsWebNavigation,
            completion: (String?) -> Unit,
        ): BarsWebLoad {
            loads += url
            answer.takeIf { it !== NEVER }?.let { completion(it(url)) }
            return object : BarsWebLoad {
                override fun cancel() {
                    cancels += 1
                }
            }
        }

        override fun webKitCookies(completion: (List<WebKitCookie>) -> Unit) = completion(cookies)

        companion object {
            val NEVER: (String) -> String? = { null }
        }
    }
}
