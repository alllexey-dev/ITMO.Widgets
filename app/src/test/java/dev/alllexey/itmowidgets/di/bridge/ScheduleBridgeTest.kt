package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.feature.schedule.di.scheduleModule
import dev.alllexey.itmowidgets.feature.schedule.domain.LessonFriendsRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.ScheduleRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangesRepository
import dev.alllexey.itmowidgets.feature.schedule.work.ScheduleWidgetEntryPoint
import dev.alllexey.itmowidgets.feature.settings.di.settingsDataModule
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
class ScheduleBridgeTest {

    @get:Rule
    val stopKoin = StopKoinRule()

    @Test
    fun `the screens read the schedule data Hilt builds`() {
        val application = bootApplication()
        val hilt = ScheduleBridgeEntryPoint.from(application)
        val koin = GlobalContext.get()

        assertSame(hilt.scheduleRepository(), koin.get<ScheduleRepository>())
        assertSame(hilt.scheduleChangesRepository(), koin.get<ScheduleChangesRepository>())
        assertSame(hilt.lessonFriendsRepository(), koin.get<LessonFriendsRepository>())
    }

    @Test
    fun `the widget gets its selector from Koin through Hilt`() {
        val application = bootApplication()

        assertNotNull(ScheduleWidgetEntryPoint.from(application).scheduleWidgetDataProvider())
    }

    /** The screens read the opt-in and the schedule preferences, which `settingsDataModule` constructs since KM-11e. */
    @Test
    fun `the schedule module passes the graph check against the release bridges`() {
        KoinGraphCheck.assertValid(KoinModules.bridges, listOf(settingsDataModule, scheduleModule))
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
