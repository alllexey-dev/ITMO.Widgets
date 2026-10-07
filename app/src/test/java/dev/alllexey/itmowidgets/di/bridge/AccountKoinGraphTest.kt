package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.client.device.DevicePlatform
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.onboarding.OnboardingRepository
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.weblogin.WebLoginRepository
import dev.alllexey.itmowidgets.feature.auth.data.DataStoreDemoMode
import dev.alllexey.itmowidgets.feature.auth.data.SessionRepositoryImpl
import dev.alllexey.itmowidgets.feature.auth.di.authDataModule
import dev.alllexey.itmowidgets.feature.auth.di.authModule
import dev.alllexey.itmowidgets.feature.me.di.meModule
import dev.alllexey.itmowidgets.feature.onboarding.data.OnboardingRepositoryImpl
import dev.alllexey.itmowidgets.feature.onboarding.di.onboardingDataModule
import dev.alllexey.itmowidgets.feature.onboarding.di.onboardingModule
import dev.alllexey.itmowidgets.feature.settings.di.settingsDataModule
import dev.alllexey.itmowidgets.feature.social.di.socialModule
import dev.alllexey.itmowidgets.feature.update.data.AppUpdateRepositoryImpl
import dev.alllexey.itmowidgets.feature.update.di.updateModule
import dev.alllexey.itmowidgets.feature.update.domain.AppUpdateRepository
import dev.alllexey.itmowidgets.feature.update.domain.AppVersionName
import dev.alllexey.itmowidgets.feature.weblogin.data.WebLoginRepositoryImpl
import dev.alllexey.itmowidgets.feature.weblogin.di.webLoginDataModule
import dev.alllexey.itmowidgets.feature.weblogin.di.webLoginModule
import org.junit.Assert.assertEquals
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
    fun `the update check reads the installed version Hilt provides and asks for Android`() {
        val application = bootApplication()
        val koin = GlobalContext.get()

        assertEquals(AccountUpdateBridgeEntryPoint.from(application).installedVersion(), koin.get<AppVersionName>())
        assertEquals(DevicePlatform.ANDROID, koin.get<DevicePlatform>())
    }

    /**
     * Onboarding and Me read the services opt-in, which `settingsDataModule` constructs since KM-11e, and Me reads
     * `SocialRepository`, which `socialModule` constructs since KM-11d; the session, the demo switch and the first-run
     * flag come from `authDataModule` and `onboardingDataModule` since KM-11h1.
     */
    @Test
    fun `the account modules pass the graph check against the release bridges`() {
        KoinGraphCheck.assertValid(
            KoinModules.bridges,
            listOf(
                settingsDataModule,
                socialModule,
                authDataModule,
                authModule,
                onboardingDataModule,
                onboardingModule,
                meModule,
                webLoginDataModule,
                webLoginModule,
                updateModule,
            ),
        )
    }

    /** As in `KoinStartTest`: Robolectric's `onCreate()` stops at `FcmWork.syncToken` after Koin and Hilt are up. */
    private fun bootApplication(): ItmoWidgetsApplication {
        val failure = runCatching { ApplicationProvider.getApplicationContext<Context>() }.exceptionOrNull()
        if (failure != null) {
            val causes = generateSequence(failure) { it.cause }
            assertTrue(failure.stackTraceToString(), causes.any { "WorkManager" in it.message.orEmpty() })
        }
        return GlobalContext.get().get<Context>() as ItmoWidgetsApplication
    }
}
