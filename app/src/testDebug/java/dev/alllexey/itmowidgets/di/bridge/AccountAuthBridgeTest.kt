package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.EntryPointAccessors
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.app.OnboardingTestEntryPoint
import dev.alllexey.itmowidgets.core.notification.NotificationDebugEntryPoint
import dev.alllexey.itmowidgets.core.onboarding.OnboardingRepository
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.feature.auth.data.SessionDataCleaners
import dev.alllexey.itmowidgets.feature.qr.data.repository.QrCodeRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.SportSessionBindingsEntryPoint
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

/**
 * The session Koin builds is the one Hilt-built code reads, and its cleaners are Hilt's one set: Koin's qualified
 * cleaners arrive there once through `SessionCleanersBridge`, never a second time through Koin's `getAll()`. Read
 * through the debug-only entry points of the real graph.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ItmoWidgetsApplication::class)
@LazyApplication(LazyLoad.ON)
class AccountAuthBridgeTest {

    @get:Rule
    val stopKoin = StopKoinRule()

    @Test
    fun `Hilt reads the session and the first-run flag Koin builds`() {
        val application = bootApplication()
        val koin = GlobalContext.get()

        assertSame(koin.get<SessionRepository>(), entryPoint<NotificationDebugEntryPoint>(application).session())
        assertSame(koin.get<OnboardingRepository>(), entryPoint<OnboardingTestEntryPoint>(application).onboarding())
    }

    @Test
    fun `the session's cleaners are Hilt's set with each Koin cleaner once`() {
        val application = bootApplication()
        val koin = GlobalContext.get()
        val hilt = entryPoint<SportSessionBindingsEntryPoint>(application).sessionDataCleaners()

        val cleaners = koin.get<SessionDataCleaners>().current()

        assertEquals(hilt, cleaners.toSet())
        assertEquals(cleaners.size, cleaners.toSet().size)
        assertEquals(1, cleaners.count { it === koin.get<QrCodeRepositoryImpl>() })
    }

    private inline fun <reified T : Any> entryPoint(context: Context): T =
        EntryPointAccessors.fromApplication(context, T::class.java)

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
