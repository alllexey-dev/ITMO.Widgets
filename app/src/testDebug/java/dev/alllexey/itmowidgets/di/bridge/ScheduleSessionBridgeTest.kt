package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.EntryPointAccessors
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.storage.DemoPreferences
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.schedule.data.TeacherLessonsGatewayImpl
import dev.alllexey.itmowidgets.feature.schedule.data.TeacherWeeksFileStore
import dev.alllexey.itmowidgets.feature.schedule.data.WeekLesson
import dev.alllexey.itmowidgets.feature.schedule.data.calendar.CalendarSyncRepositoryImpl
import dev.alllexey.itmowidgets.feature.schedule.data.changes.ScheduleChangesFileStore
import dev.alllexey.itmowidgets.feature.schedule.data.changes.ScheduleChangesRepositoryImpl
import dev.alllexey.itmowidgets.feature.schedule.data.changes.StoredScheduleChanges
import dev.alllexey.itmowidgets.feature.schedule.data.repository.ScheduleRepositoryImpl
import dev.alllexey.itmowidgets.feature.schedule.data.widget.ScheduleWidgetSnapshotStoreImpl
import dev.alllexey.itmowidgets.feature.sport.data.SportSessionBindingsEntryPoint
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
 * Sign-out on the real graph after KM-11a2: Hilt's `Set<SessionDataCleaner>` holds Koin's three schedule cleaners
 * (through `SessionCleanersBridge`) and Hilt's two once each, and running it forgets every schedule store.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ItmoWidgetsApplication::class)
@LazyApplication(LazyLoad.ON)
class ScheduleSessionBridgeTest {

    @get:Rule
    val stopKoin = StopKoinRule()

    @Test
    fun `the cleaner set holds each schedule cleaner once`() {
        val application = bootApplication()
        val koin = GlobalContext.get()
        val cleaners = cleaners(application)

        assertEquals(1, cleaners.count { it === koin.get<ScheduleRepositoryImpl>() })
        assertEquals(1, cleaners.count { it === koin.get<ScheduleChangesRepositoryImpl>() })
        assertEquals(1, cleaners.count { it === koin.get<TeacherLessonsGatewayImpl>() })
        assertEquals(1, cleaners.count { it is ScheduleWidgetSnapshotStoreImpl })
        assertEquals(1, cleaners.count { it is CalendarSyncRepositoryImpl })
        assertEquals(5, cleaners.count(::isScheduleCleaner))
    }

    @Test
    fun `sign-out clears the schedule cache, the change history and the teacher weeks`() = runBlocking {
        val application = bootApplication()
        val koin = GlobalContext.get()
        val schedule = koin.get<ScheduleRepositoryImpl>()
        val directories = koin.get<AppDirectories>()
        val today = koin.get<AcademicTimeProvider>().today()
        // The demo session fills the schedule cache without My ITMO.
        DemoPreferences(koin.get<DataStore<Preferences>>()).setDemoActive(true)
        assertEquals(AppResult.Success(Unit), schedule.refreshSchedule(null, today, today))
        ScheduleChangesFileStore(directories).write(StoredScheduleChanges())
        TeacherWeeksFileStore(directories).write(mapOf(LocalDate(2026, 9, 7) to listOf(WeekLesson(1, 2, "Физика"))))
        assertNotNull(schedule.peekScheduleForRange(null, today, today))

        cleaners(application).forEach { it.clearSessionData() }

        assertNull(schedule.peekScheduleForRange(null, today, today))
        assertNull(ScheduleChangesFileStore(directories).read())
        assertTrue(TeacherWeeksFileStore(directories).read().isEmpty())
    }

    private fun isScheduleCleaner(cleaner: SessionDataCleaner): Boolean =
        cleaner::class.java.name.startsWith("dev.alllexey.itmowidgets.feature.schedule.")

    private fun cleaners(context: Context): Set<SessionDataCleaner> =
        EntryPointAccessors.fromApplication(context, SportSessionBindingsEntryPoint::class.java).sessionDataCleaners()

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
