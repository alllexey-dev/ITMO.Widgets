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
import dev.alllexey.itmowidgets.feature.connections.presentation.FakeItmoLogins
import dev.alllexey.itmowidgets.feature.connections.presentation.Samples
import io.ktor.client.engine.mock.MockEngine
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

/** The label of a client's sign-in (plan 2.2): an ordinary browser unless the switch is turned off. */
class ClientSignInImplTest {

    private val dispatcher = StandardTestDispatcher()
    private val label = SignInLabelPreferences(InMemoryPreferencesDataStore())
    private val signIn = ClientSignInImpl(
        myItmo = MyItmoClient(
            MyItmoConfiguration.DEFAULT,
            storage = InMemoryTokenStorage(),
            engine = MockEngine { error("begin sends no request") },
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
        val attempt = signIn.begin(Samples.request())

        assertTrue("Chrome/" in attempt.userAgent && "(X11; Linux x86_64)" in attempt.userAgent, attempt.userAgent)
        assertFalse("bot" in attempt.userAgent)
        assertTrue(attempt.url.startsWith("https://id.itmo.ru/"))
    }

    @Test
    fun theSwitchOffSignsInWithTheHonestLabel() = runTest(dispatcher) {
        label.setBrowserLike(false)

        val attempt = signIn.begin(Samples.request())

        assertTrue(attempt.userAgent.startsWith("ITMO Widgets agent nas bot/1.2"), attempt.userAgent)
    }
}
