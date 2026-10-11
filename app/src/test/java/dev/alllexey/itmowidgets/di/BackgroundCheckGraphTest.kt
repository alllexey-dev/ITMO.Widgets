package dev.alllexey.itmowidgets.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.core.recordbook.MarkTracking
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeTracking
import dev.alllexey.itmowidgets.core.work.BackgroundCheck
import dev.alllexey.itmowidgets.di.bridge.StopKoinRule
import dev.alllexey.itmowidgets.feature.schedule.data.calendar.DefaultCalendarSync
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
 * The background checks the Application and the session effects iterate: Koin's open set, each check under its own
 * qualifier and read by its implementation key, so the set holds the instances every other reader gets.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ItmoWidgetsApplication::class)
@LazyApplication(LazyLoad.ON)
class BackgroundCheckGraphTest {

    @get:Rule
    val stopKoin = StopKoinRule()

    @Test
    fun `the set holds the three checks once each, as the instances of their own bindings`() {
        bootApplication()
        val koin = GlobalContext.get()
        val checks = koin.getAll<BackgroundCheck>()
        val expected = listOf(
            koin.get<ScheduleChangeTracking>(),
            koin.get<DefaultCalendarSync>(),
            koin.get<MarkTracking>()
        )

        assertEquals(3, checks.size)
        expected.forEach { binding ->
            assertEquals("$binding in $checks", 1, checks.count { it === binding })
        }
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
