package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.core.work.BackgroundCheck
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.OwnScheduleSource
import dev.alllexey.itmowidgets.client.schedule.ScheduleApi
import dev.alllexey.itmowidgets.core.schedule.CalendarSync
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeTracking
import dev.alllexey.itmowidgets.core.schedule.ScheduleRefreshGateway
import dev.alllexey.itmowidgets.core.schedule.SubjectLessonsGateway
import dev.alllexey.itmowidgets.core.schedule.TeacherLessonsGateway
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
 * The schedule data on the real graph (KM-11a2, IO-15a): Koin constructs it and the calendar sync once, with the
 * Android notifier, the phone's calendars, the WorkManager schedulers and Core 2.0's schedule area of the app modules.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ItmoWidgetsApplication::class)
@LazyApplication(LazyLoad.ON)
class ScheduleGraphTest {

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
    fun `the calendar sync is a background check and the export reads the own schedule`() {
        bootApplication()
        val koin = GlobalContext.get()
        val sync = koin.get<DefaultCalendarSync>()

        assertEquals(1, koin.getAll<BackgroundCheck>().count { it === sync })
        assertTrue(koin.get<OwnScheduleSource>() is MyItmoOwnScheduleSource)
    }

    @Test
    fun `the change tracking is a background check and the widget store one single`() {
        bootApplication()
        val koin = GlobalContext.get()
        val tracking = koin.get<DefaultScheduleChangeTracking>()

        assertEquals(1, koin.getAll<BackgroundCheck>().count { it === tracking })
        assertSame(koin.get<ScheduleWidgetSnapshotStoreImpl>(), koin.get<ScheduleWidgetSnapshotStore>())
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
    fun `the app modules give the Android notifier, the calendars, the schedulers and the schedule area`() {
        bootApplication()
        val koin = GlobalContext.get()

        assertNotNull(koin.get<ScheduleApi>())
        assertTrue(koin.get<ScheduleChangeNotifier>() is AndroidScheduleChangeNotifier)
        assertNotNull(koin.get<ScheduleChangesScheduler>())
        assertTrue(koin.get<PhoneCalendars>() is AndroidPhoneCalendars)
        assertNotNull(koin.get<CalendarSyncScheduler>())
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
