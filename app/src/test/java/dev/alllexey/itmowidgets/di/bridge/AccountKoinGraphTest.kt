package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.onboarding.OnboardingRepository
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.feature.auth.data.DataStoreDemoMode
import dev.alllexey.itmowidgets.feature.auth.data.SessionRepositoryImpl
import dev.alllexey.itmowidgets.feature.auth.di.authModule
import dev.alllexey.itmowidgets.feature.me.di.meModule
import dev.alllexey.itmowidgets.feature.onboarding.data.OnboardingRepositoryImpl
import dev.alllexey.itmowidgets.feature.onboarding.di.onboardingDataModule
import dev.alllexey.itmowidgets.feature.onboarding.di.onboardingModule
import dev.alllexey.itmowidgets.feature.social.di.socialModule
import dev.alllexey.itmowidgets.feature.update.di.updateModule
import dev.alllexey.itmowidgets.feature.update.domain.AppUpdateRepository
import dev.alllexey.itmowidgets.feature.weblogin.di.webLoginModule
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
    fun `the update repository resolves in Koin to the instance Hilt builds`() {
        val application = bootApplication()

        assertSame(
            AccountUpdateBridgeEntryPoint.from(application).appUpdateRepository(),
            GlobalContext.get().get<AppUpdateRepository>(),
        )
    }

    /**
     * Onboarding and Me read the services opt-in, which `settingsDataModule` constructs since KM-11e, and Me reads
     * `SocialRepository`, which `socialModule` constructs since KM-11d (its profile reads the teacher reviews of
     * `reviewsModule` since KM-11f, checked with `scheduleDataGraph`); the session, the demo switch and the first-run
     * flag come from `authDataModule` and `onboardingDataModule` since KM-11h1.
     */
    @Test
    fun `the account modules pass the graph check against the release bridges`() {
        KoinGraphCheck.assertValid(
            KoinModules.bridges,
            listOf(
                socialModule,
                authModule,
                onboardingDataModule,
                onboardingModule,
                meModule,
                webLoginModule,
                updateModule,
            ) + scheduleDataGraph,
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
