package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.core.platform.AndroidPlatformCapabilities
import dev.alllexey.itmowidgets.core.platform.PlatformCapabilities
import dev.alllexey.itmowidgets.core.storage.CrossProcessLock
import dev.alllexey.itmowidgets.core.storage.SecureStore
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
class PlatformPortsBridgeTest {

    @get:Rule
    val stopKoin = StopKoinRule()

    @Test
    fun `the platform ports resolve in Koin to the instances Hilt builds`() {
        val application = bootApplication()
        val hilt = CoreBridgeEntryPoint.from(application)
        val koin = GlobalContext.get()

        assertSame(hilt.secureStore(), koin.get<SecureStore>())
        assertSame(hilt.crossProcessLock(), koin.get<CrossProcessLock>())
        assertSame(hilt.secureStore(), hilt.secureStore())
        assertEquals(AndroidPlatformCapabilities, koin.get<PlatformCapabilities>())
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
