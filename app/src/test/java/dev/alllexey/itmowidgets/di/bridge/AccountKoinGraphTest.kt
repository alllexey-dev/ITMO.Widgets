package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.client.device.DevicePlatform
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.onboarding.OnboardingRepository
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.weblogin.WebLoginRepository
import dev.alllexey.itmowidgets.feature.auth.data.DataStoreDemoMode
import dev.alllexey.itmowidgets.feature.auth.data.SessionRepositoryImpl
import dev.alllexey.itmowidgets.feature.onboarding.data.OnboardingRepositoryImpl
import dev.alllexey.itmowidgets.feature.update.data.AppUpdateRepositoryImpl
import dev.alllexey.itmowidgets.feature.update.domain.AppUpdateRepository
import dev.alllexey.itmowidgets.feature.update.domain.AppVersionName
import dev.alllexey.itmowidgets.feature.weblogin.data.WebLoginRepositoryImpl
import dev.alllexey.itmowidgets.feature.update.ui.InstallStateWatcher
import dev.alllexey.itmowidgets.feature.update.ui.UpdateAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.experimental.LazyApplication
import org.robolectric.annotation.experimental.LazyApplication.LazyLoad

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ItmoWidgetsApplication::class)
@LazyApplication(LazyLoad.ON)
class AccountKoinGraphTest {

    @get:Rule
    val stopKoin = StopKoinRule()

    @Test
    fun `Koin builds one session, one demo switch and one first-run flag`() {
        bootApplication()
        val koin = GlobalContext.get()

        assertSame(koin.get<SessionRepositoryImpl>(), koin.get<SessionRepository>())
        assertSame(koin.get<DataStoreDemoMode>(), koin.get<DemoMode>())
        assertSame(koin.get<OnboardingRepositoryImpl>(), koin.get<OnboardingRepository>())
    }

    @Test
    fun `Koin builds one update and one web sign-in repository`() {
        bootApplication()
        val koin = GlobalContext.get()

        assertSame(koin.get<AppUpdateRepositoryImpl>(), koin.get<AppUpdateRepository>())
        assertSame(koin.get<WebLoginRepositoryImpl>(), koin.get<WebLoginRepository>())
    }

    @Test
    fun `the update check reads the installed version and asks for Android`() {
        val application = bootApplication()
        val koin = GlobalContext.get()

        assertEquals(AppVersionName(application.getString(R.string.app_version)), koin.get<AppVersionName>())
        assertEquals(DevicePlatform.ANDROID, koin.get<DevicePlatform>())
    }

    /** Each distribution flavor's unit tests run this against its own `updateActionModule`. */
    @Test
    fun `the flavor's update action and install watcher resolve`() {
        bootApplication()
        val koin = GlobalContext.get()

        assertNotNull(koin.get<UpdateAction>())
        assertNotNull(koin.get<InstallStateWatcher>())
    }

    /** As in `KoinStartTest`: Robolectric's `onCreate()` stops at `FcmWork.syncToken` after Koin is up. */
    private fun bootApplication(): ItmoWidgetsApplication {
        val failure = runCatching { ApplicationProvider.getApplicationContext<Context>() }.exceptionOrNull()
        if (failure != null) {
            val causes = generateSequence(failure) { it.cause }
            assertTrue(failure.stackTraceToString(), causes.any { "WorkManager" in it.message.orEmpty() })
        }
        return GlobalContext.get().get<Context>() as ItmoWidgetsApplication
    }
}
