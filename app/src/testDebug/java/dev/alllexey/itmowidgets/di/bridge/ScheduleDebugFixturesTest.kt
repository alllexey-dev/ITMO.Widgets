package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.TeacherLevelsRepository
import dev.alllexey.itmowidgets.core.schedule.CalendarSync
import dev.alllexey.itmowidgets.core.schedule.SchedulePreferencesRepository
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.sport.PendingSportBookingsRepository
import dev.alllexey.itmowidgets.core.testing.FakeCalendarSync
import dev.alllexey.itmowidgets.core.testing.FakeCustomServicesRepository
import dev.alllexey.itmowidgets.core.testing.FakePendingSportBookingsRepository
import dev.alllexey.itmowidgets.core.testing.FakeSchedulePreferencesRepository
import dev.alllexey.itmowidgets.core.testing.FakeTeacherLevelsRepository
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.reviews.data.TeacherLevelsRepositoryImpl
import dev.alllexey.itmowidgets.feature.schedule.FakeScheduleChangesRepository
import dev.alllexey.itmowidgets.feature.schedule.FakeScheduleRepository
import dev.alllexey.itmowidgets.feature.schedule.data.LessonFriendsRepositoryImpl
import dev.alllexey.itmowidgets.feature.schedule.data.changes.ScheduleChangesRepositoryImpl
import dev.alllexey.itmowidgets.feature.schedule.data.repository.ScheduleRepositoryImpl
import dev.alllexey.itmowidgets.feature.schedule.domain.LessonFriendsRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.ScheduleRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangesRepository
import dev.alllexey.itmowidgets.feature.settings.data.CustomServicesRepositoryImpl
import dev.alllexey.itmowidgets.feature.settings.data.SchedulePreferencesRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.repository.PendingSportBookingsRepositoryImpl
import kotlinx.datetime.LocalDate
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ItmoWidgetsApplication::class)
@LazyApplication(LazyLoad.ON)
class ScheduleDebugFixturesTest {

    @get:Rule
    val stopKoin = StopKoinRule()

    @Test
    fun `a fixture replaces what the schedule screens read until its host unloads it`() {
        val application = bootApplication()
        val koin = GlobalContext.get()
        val core = CoreBridgeEntryPoint.from(application)

        val fixture = ScheduleDebugFixtures.load(application, Fakes)
        assertSame(Fakes.time, koin.get<AcademicTimeProvider>())
        assertSame(Fakes.schedule, koin.get<ScheduleRepository>())
        assertSame(Fakes.changes, koin.get<ScheduleChangesRepository>())
        assertSame(Fakes.preferences, koin.get<SchedulePreferencesRepository>())
        assertSame(Fakes.pendingSport, koin.get<PendingSportBookingsRepository>())
        assertSame(Fakes.calendarSync, koin.get<CalendarSync>())
        assertSame(Fakes.lessonFriends, koin.get<LessonFriendsRepository>())
        assertSame(Fakes.customServices, koin.get<CustomServicesRepository>())
        assertSame(Fakes.teacherLevels, koin.get<TeacherLevelsRepository>())

        ScheduleDebugFixtures.unload(application, fixture)
        assertSame(core.academicTimeProvider(), koin.get<AcademicTimeProvider>())
        // The data module's own singles again, not second repositories beside the ones sign-out and the widget hold.
        assertSame(koin.get<ScheduleRepositoryImpl>(), koin.get<ScheduleRepository>())
        assertSame(koin.get<ScheduleChangesRepositoryImpl>(), koin.get<ScheduleChangesRepository>())
        assertSame(koin.get<SchedulePreferencesRepositoryImpl>(), koin.get<SchedulePreferencesRepository>())
        assertSame(koin.get<PendingSportBookingsRepositoryImpl>(), koin.get<PendingSportBookingsRepository>())
        assertSame(core.coreCalendarSync(), koin.get<CalendarSync>())
        assertSame(koin.get<LessonFriendsRepositoryImpl>(), koin.get<LessonFriendsRepository>())
        assertSame(koin.get<CustomServicesRepositoryImpl>(), koin.get<CustomServicesRepository>())
        assertSame(koin.get<TeacherLevelsRepositoryImpl>(), koin.get<TeacherLevelsRepository>())
    }

    @Test
    fun `the history fixture replaces only the changes and the clock`() {
        val application = bootApplication()
        val koin = GlobalContext.get()
        val release = koin.get<ScheduleRepository>()

        val fixture = ScheduleDebugFixtures.loadChanges(application, Fakes.changes, Fakes.time)
        assertSame(Fakes.changes, koin.get<ScheduleChangesRepository>())
        assertSame(Fakes.time, koin.get<AcademicTimeProvider>())
        assertSame(release, koin.get<ScheduleRepository>())

        ScheduleDebugFixtures.unload(application, fixture)
        assertSame(koin.get<ScheduleChangesRepositoryImpl>(), koin.get<ScheduleChangesRepository>())
    }

    @Test
    fun `a replaced fixture is left to the host that replaced it`() {
        val application = bootApplication()
        val koin = GlobalContext.get()

        val first = ScheduleDebugFixtures.load(application, Fakes)
        val second = ScheduleDebugFixtures.load(application, Fakes)
        ScheduleDebugFixtures.unload(application, first)
        assertSame(Fakes.schedule, koin.get<ScheduleRepository>())

        ScheduleDebugFixtures.unload(application, second)
        assertNotSame(Fakes.schedule, koin.get<ScheduleRepository>())
    }

    private object Fakes : ScheduleDebugFixtures.Fakes {
        val time = FixedAcademicTime(LocalDate(2026, 9, 7))
        val schedule = FakeScheduleRepository()
        val changes = FakeScheduleChangesRepository()
        val preferences = FakeSchedulePreferencesRepository()
        val pendingSport = FakePendingSportBookingsRepository()
        val calendarSync = FakeCalendarSync()
        val lessonFriends = object : LessonFriendsRepository {
            override suspend fun friendsOnLesson(pairId: Long, date: LocalDate): AppResult<List<UserSummary>> =
                AppResult.Success(emptyList())
        }
        val customServices = FakeCustomServicesRepository()
        val teacherLevels = FakeTeacherLevelsRepository()

        override fun time(): AcademicTimeProvider = time
        override fun schedule(): ScheduleRepository = schedule
        override fun changes(): ScheduleChangesRepository = changes
        override fun preferences(): SchedulePreferencesRepository = preferences
        override fun pendingSport(): PendingSportBookingsRepository = pendingSport
        override fun calendarSync(): CalendarSync = calendarSync
        override fun lessonFriends(): LessonFriendsRepository = lessonFriends
        override fun customServices(): CustomServicesRepository = customServices
        override fun teacherLevels(): TeacherLevelsRepository = teacherLevels
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
