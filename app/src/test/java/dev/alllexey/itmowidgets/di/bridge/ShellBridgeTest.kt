package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.feature.update.domain.PendingAppUpdate
import dev.alllexey.itmowidgets.feature.update.ui.InstallStateWatcher
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ItmoWidgetsApplication::class)
@LazyApplication(LazyLoad.ON)
class ShellBridgeTest {

    @get:Rule
    val stopKoin = StopKoinRule()

    @Test
    fun `the shell host's update types resolve in Koin to what Hilt builds`() {
        val application = bootApplication()
        val hilt = ShellBridgeEntryPoint.from(application)
        val koin = GlobalContext.get()

        assertEquals(hilt.pendingAppUpdate()::class, koin.get<PendingAppUpdate>()::class)
        assertEquals(hilt.installStateWatcher()::class, koin.get<InstallStateWatcher>()::class)
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
