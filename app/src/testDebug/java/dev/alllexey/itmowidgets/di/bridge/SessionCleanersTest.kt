package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.feature.auth.data.SessionDataCleaners
import dev.alllexey.itmowidgets.feature.qr.data.repository.QrCodeRepositoryImpl
import org.junit.Assert.assertEquals
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
 * The session's cleaner list on the real graph: each Koin cleaner reaches it once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ItmoWidgetsApplication::class)
@LazyApplication(LazyLoad.ON)
class SessionCleanersTest {

    @get:Rule
    val stopKoin = StopKoinRule()

    @Test
    fun `the session's cleaners hold each Koin cleaner once`() {
        bootApplication()
        val koin = GlobalContext.get()

        val cleaners = koin.get<SessionDataCleaners>().current()

        assertEquals(koin.getAll<SessionDataCleaner>().toSet(), cleaners.toSet())
        assertEquals(cleaners.size, cleaners.toSet().size)
        assertEquals(1, cleaners.count { it === koin.get<QrCodeRepositoryImpl>() })
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
