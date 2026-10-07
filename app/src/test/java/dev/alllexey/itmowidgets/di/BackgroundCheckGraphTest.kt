package dev.alllexey.itmowidgets.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.EntryPointAccessors
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.di.bridge.StopKoinRule
import dev.alllexey.itmowidgets.feature.recordbook.work.MarksTestEntryPoint
import dev.alllexey.itmowidgets.feature.schedule.data.calendar.DefaultCalendarSync
import dev.alllexey.itmowidgets.feature.schedule.work.ScheduleChangesTestEntryPoint
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
 * The Hilt set of background checks the Application and the session effects iterate. A test `@EntryPoint` would only
 * join a `@HiltAndroidTest` component, so the set is read from the Application's injected field and the single
 * bindings from the app's own entry points; the calendar sync is Koin's one `DefaultCalendarSync`, which
 * `ScheduleBridge` hands to the Hilt set.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ItmoWidgetsApplication::class)
@LazyApplication(LazyLoad.ON)
class BackgroundCheckGraphTest {

    @get:Rule
    val stopKoin = StopKoinRule()

    @Test
    fun `the set holds the three checks once each, as the instances of their own bindings`() {
        val application = bootApplication()
        val checks = application.backgroundChecks.toList()
        val expected = listOf(
            entryPoint<ScheduleChangesTestEntryPoint>(application).scheduleChangeTracking(),
            GlobalContext.get().get<DefaultCalendarSync>(),
            entryPoint<MarksTestEntryPoint>(application).marksTracking()
        )

        assertEquals(3, checks.size)
        expected.forEach { binding ->
            assertEquals("$binding in $checks", 1, checks.count { it === binding })
        }
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
