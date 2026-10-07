package dev.alllexey.itmowidgets.feature.recordbook.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker.Result
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.core.work.buildWorker
import dev.alllexey.itmowidgets.di.bridge.StopKoinRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.experimental.LazyApplication
import org.robolectric.annotation.experimental.LazyApplication.LazyLoad

/**
 * WorkManager's `InitializationProvider` can run a worker before `Application.onCreate()` has started Koin; the
 * worker then starts the release graph itself through `KoinStarter`. Proven on the real Application with Koin
 * stopped after the boot: a fresh device has no session, so the marks check skips.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ItmoWidgetsApplication::class)
@LazyApplication(LazyLoad.ON)
class MarksWorkerStarterTest {

    @get:Rule
    val stopKoin = StopKoinRule()

    @Test
    fun `a worker that runs before Koin starts it`() = runTest {
        val application = bootApplication()
        stopKoin()
        assertNull(GlobalContext.getOrNull())

        assertEquals(Result.success(), buildWorker<MarksWorker>(application).doWork())
        assertSame(application, GlobalContext.get().get<Context>())
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
