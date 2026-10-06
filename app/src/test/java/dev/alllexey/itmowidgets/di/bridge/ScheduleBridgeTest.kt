package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.EntryPointAccessors
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.client.schedule.ScheduleApi
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeTracking
import dev.alllexey.itmowidgets.core.schedule.ScheduleRefreshGateway
import dev.alllexey.itmowidgets.core.schedule.SubjectLessonsGateway
import dev.alllexey.itmowidgets.core.schedule.TeacherLessonsGateway
import dev.alllexey.itmowidgets.feature.auth.di.authDataModule
import dev.alllexey.itmowidgets.feature.friendselector.di.friendSelectorModule
import dev.alllexey.itmowidgets.feature.reviews.di.reviewsModule
import dev.alllexey.itmowidgets.feature.schedule.data.LessonFriendsRepositoryImpl
import dev.alllexey.itmowidgets.feature.schedule.data.SubjectLessonsGatewayImpl
import dev.alllexey.itmowidgets.feature.schedule.data.TeacherLessonsGatewayImpl
import dev.alllexey.itmowidgets.feature.schedule.data.changes.DefaultScheduleChangeTracking
import dev.alllexey.itmowidgets.feature.schedule.data.changes.ScheduleChangesRepositoryImpl
import dev.alllexey.itmowidgets.feature.schedule.data.repository.ScheduleRepositoryImpl
import dev.alllexey.itmowidgets.feature.schedule.di.scheduleDataModule
import dev.alllexey.itmowidgets.feature.schedule.di.scheduleModule
import dev.alllexey.itmowidgets.feature.schedule.domain.LessonFriendsRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.ScheduleRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangeNotifier
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangesRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangesScheduler
import dev.alllexey.itmowidgets.feature.schedule.work.AndroidScheduleChangeNotifier
import dev.alllexey.itmowidgets.feature.schedule.work.ScheduleChangesEntryPoint
import dev.alllexey.itmowidgets.feature.schedule.work.ScheduleChangesTestEntryPoint
import dev.alllexey.itmowidgets.feature.schedule.work.ScheduleWidgetEntryPoint
import dev.alllexey.itmowidgets.feature.settings.di.settingsDataModule
import dev.alllexey.itmowidgets.feature.social.di.socialModule
import dev.alllexey.itmowidgets.feature.sport.di.sportModule
import org.junit.Assert.assertEquals
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

/**
 * The schedule data on the real graph (KM-11a2): Koin constructs it once, Hilt's remaining readers (the workers, the
 * widget, the debug tools, the background check set) get Koin's instances through `ScheduleBridge`, and Koin gets
 * the Android notifier, the WorkManager scheduler and Core 2.0's schedule area from Hilt.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ItmoWidgetsApplication::class)
@LazyApplication(LazyLoad.ON)
class ScheduleBridgeTest {

    @get:Rule
    val stopKoin = StopKoinRule()

    @Test
    fun `each schedule contract is one Koin single over its implementation`() {
        bootApplication()
        val koin = GlobalContext.get()

        assertSame(koin.get<ScheduleRepositoryImpl>(), koin.get<ScheduleRepository>())
        assertSame(koin.get<ScheduleRepositoryImpl>(), koin.get<ScheduleRefreshGateway>())
        assertSame(koin.get<ScheduleChangesRepositoryImpl>(), koin.get<ScheduleChangesRepository>())
        assertSame(koin.get<LessonFriendsRepositoryImpl>(), koin.get<LessonFriendsRepository>())
        assertSame(koin.get<SubjectLessonsGatewayImpl>(), koin.get<SubjectLessonsGateway>())
        assertSame(koin.get<TeacherLessonsGatewayImpl>(), koin.get<TeacherLessonsGateway>())
        assertSame(koin.get<DefaultScheduleChangeTracking>(), koin.get<ScheduleChangeTracking>())
    }

    @Test
    fun `Hilt's readers get Koin's tracking, check and widget data`() {
        val application = bootApplication()
        val koin = GlobalContext.get()
        val tracking = koin.get<DefaultScheduleChangeTracking>()

        assertSame(
            tracking,
            EntryPointAccessors.fromApplication(application, ScheduleChangesTestEntryPoint::class.java).scheduleChangeTracking()
        )
        assertEquals(1, application.backgroundChecks.count { it === tracking })
        assertNotNull(EntryPointAccessors.fromApplication(application, ScheduleChangesEntryPoint::class.java).check())
        assertNotNull(ScheduleWidgetEntryPoint.from(application).scheduleWidgetDataProvider())
    }

    @Test
    fun `Koin gets the Android notifier, the scheduler and the schedule area from Hilt`() {
        val application = bootApplication()
        val koin = GlobalContext.get()
        val hilt = ScheduleBridgeEntryPoint.from(application)

        assertSame(hilt.backendScheduleApi(), koin.get<ScheduleApi>())
        assertTrue(koin.get<ScheduleChangeNotifier>() is AndroidScheduleChangeNotifier)
        assertNotNull(koin.get<ScheduleChangesScheduler>())
    }

    /**
     * The screens read the opt-in and the schedule preferences, which `settingsDataModule` constructs since KM-11e, and
     * the pending sport rows, which `sportModule` constructs since KM-11c (with the friend list it reads).
     */
    @Test
    fun `the schedule modules pass the graph check against the release bridges`() {
        KoinGraphCheck.assertValid(
            KoinModules.bridges,
            listOf(
                authDataModule,
                settingsDataModule,
                reviewsModule,
                socialModule,
                friendSelectorModule,
                sportModule,
                scheduleModule,
                scheduleDataModule,
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
