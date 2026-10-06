package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.EntryPointAccessors
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.client.schedule.ScheduleApi
import dev.alllexey.itmowidgets.core.schedule.CalendarSync
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
import dev.alllexey.itmowidgets.feature.schedule.data.calendar.AndroidPhoneCalendars
import dev.alllexey.itmowidgets.feature.schedule.data.calendar.CalendarSyncRepositoryImpl
import dev.alllexey.itmowidgets.feature.schedule.data.calendar.DefaultCalendarSync
import dev.alllexey.itmowidgets.feature.schedule.data.calendar.MyItmoOwnScheduleSource
import dev.alllexey.itmowidgets.feature.schedule.data.changes.DefaultScheduleChangeTracking
import dev.alllexey.itmowidgets.feature.schedule.data.changes.ScheduleChangesCheck
import dev.alllexey.itmowidgets.feature.schedule.data.changes.ScheduleChangesRepositoryImpl
import dev.alllexey.itmowidgets.feature.schedule.data.repository.ScheduleRepositoryImpl
import dev.alllexey.itmowidgets.feature.schedule.data.widget.ScheduleWidgetSnapshotStoreImpl
import dev.alllexey.itmowidgets.feature.schedule.di.scheduleDataModule
import dev.alllexey.itmowidgets.feature.schedule.di.scheduleModule
import dev.alllexey.itmowidgets.feature.schedule.domain.LessonFriendsRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.ScheduleRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.CalendarSyncRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.CalendarSyncScheduler
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.PhoneCalendars
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangeNotifier
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangesRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangesScheduler
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshotStore
import dev.alllexey.itmowidgets.feature.schedule.work.AndroidScheduleChangeNotifier
import dev.alllexey.itmowidgets.feature.schedule.work.ScheduleChangesTestEntryPoint
import dev.alllexey.itmowidgets.feature.settings.di.settingsDataModule
import dev.alllexey.itmowidgets.feature.social.di.socialModule
import dev.alllexey.itmowidgets.feature.sport.di.sportModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
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
 * The schedule data on the real graph (KM-11a2, IO-15a): Koin constructs it and the calendar sync once, Hilt's
 * remaining readers (the `.ics` export, the debug tools, the background check set, the session effects) get Koin's
 * instances through `ScheduleBridge`, and Koin gets the Android notifier, the phone's calendars, the WorkManager
 * schedulers and Core 2.0's schedule area from Hilt.
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
        assertSame(koin.get<CalendarSyncRepositoryImpl>(), koin.get<CalendarSyncRepository>())
        assertSame(koin.get<DefaultCalendarSync>(), koin.get<CalendarSync>())
    }

    @Test
    fun `Hilt's readers get Koin's calendar sync and own schedule source`() {
        val application = bootApplication()
        val koin = GlobalContext.get()
        val sync = koin.get<DefaultCalendarSync>()

        assertEquals(1, application.backgroundChecks.count { it === sync })
        assertTrue(ScheduleBridge.ownScheduleSource(application) is MyItmoOwnScheduleSource)
    }

    @Test
    fun `Hilt's readers get Koin's tracking and widget snapshot store`() {
        val application = bootApplication()
        val koin = GlobalContext.get()
        val tracking = koin.get<DefaultScheduleChangeTracking>()

        assertSame(
            tracking,
            EntryPointAccessors.fromApplication(application, ScheduleChangesTestEntryPoint::class.java).scheduleChangeTracking()
        )
        assertEquals(1, application.backgroundChecks.count { it === tracking })
        val store = koin.get<ScheduleWidgetSnapshotStoreImpl>()
        assertSame(store, koin.get<ScheduleWidgetSnapshotStore>())
        assertSame(store, ScheduleBridge.scheduleWidgetSnapshotStore(application))
    }

    @Test
    fun `the workers get the change check and the calendar sync from Koin`() {
        bootApplication()
        val koin = GlobalContext.get()

        // Stateless and unscoped: every run of the worker gets a new check.
        assertNotSame(koin.get<ScheduleChangesCheck>(), koin.get<ScheduleChangesCheck>())
        // Koin's one single, read by its contract and by its implementation type.
        assertSame(koin.get<CalendarSync>(), koin.get<DefaultCalendarSync>())
    }

    @Test
    fun `Koin gets the Android notifier, the calendars, the schedulers and the schedule area from Hilt`() {
        val application = bootApplication()
        val koin = GlobalContext.get()
        val hilt = ScheduleBridgeEntryPoint.from(application)

        assertSame(hilt.backendScheduleApi(), koin.get<ScheduleApi>())
        assertTrue(koin.get<ScheduleChangeNotifier>() is AndroidScheduleChangeNotifier)
        assertNotNull(koin.get<ScheduleChangesScheduler>())
        assertTrue(koin.get<PhoneCalendars>() is AndroidPhoneCalendars)
        assertNotNull(koin.get<CalendarSyncScheduler>())
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
