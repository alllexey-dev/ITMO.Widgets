package dev.alllexey.itmowidgets.feature.recordbook

import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.di.bridge.KoinStarter
import dev.alllexey.itmowidgets.di.bridge.StopKoinRule
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsSessionRepository
import dev.alllexey.itmowidgets.feature.recordbook.presentation.BarsLoginViewModel
import dev.alllexey.itmowidgets.feature.recordbook.ui.BarsLoginActivity
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.androidx.viewmodel.ext.android.getViewModel
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The BARS sign-in obtains its ViewModel from Koin (LR-2a) on the release graph with a fake session port. Hilt can no
 * longer build this ViewModel, so an Activity that resolves one proves the Koin path.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = HiltTestApplication::class)
class RecordbookKoinHostsTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val stopKoin = StopKoinRule()

    @Test
    fun `the BARS sign-in obtains its ViewModel from Koin`() {
        val koin = KoinStarter.ensureStarted(ApplicationProvider.getApplicationContext())
        koin.loadModules(listOf(module { single<BarsSessionRepository> { FakeSession } }), allowOverride = true)
        val controller = Robolectric.buildActivity(BarsLoginActivity::class.java).setup()
        try {
            val model = controller.get().getViewModel<BarsLoginViewModel>()
            assertTrue(model.loginUrl, model.loginUrl.startsWith("https://bars.example/login?state="))
            assertSame(model, controller.get().getViewModel<BarsLoginViewModel>())
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private object FakeSession : BarsSessionRepository {
        override fun loginUrl(state: String) = "https://bars.example/login?state=$state"
        override fun isCallback(url: String) = false
        override suspend fun completeLogin(callbackUrl: String, expectedState: String): AppResult<Unit> =
            AppResult.Success(Unit)
    }
}
