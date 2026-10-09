package dev.alllexey.itmowidgets.feature.schedule.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.di.iosCoreModule
import dev.alllexey.itmowidgets.core.diagnostics.AppLog
import dev.alllexey.itmowidgets.core.location.BuildingDirectory
import dev.alllexey.itmowidgets.core.notification.AppNotifier
import dev.alllexey.itmowidgets.core.platform.BundleIdentifiers
import dev.alllexey.itmowidgets.core.platform.IosCoreHost
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.TeacherLevelsRepository
import dev.alllexey.itmowidgets.core.schedule.CalendarSync
import dev.alllexey.itmowidgets.core.schedule.SchedulePreferencesRepository
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.core.sport.PendingSportBookingsRepository
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.storage.AppGroupDirectory
import dev.alllexey.itmowidgets.core.testing.FakeCustomServicesRepository
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.FakeSchedulePreferencesRepository
import dev.alllexey.itmowidgets.core.testing.FakeSessionRepository
import dev.alllexey.itmowidgets.core.testing.FakeSessionTokenStore
import dev.alllexey.itmowidgets.core.testing.FakeTeacherLevelsRepository
import dev.alllexey.itmowidgets.core.testing.RecordingAppNotifier
import dev.alllexey.itmowidgets.core.work.AppRefreshScheduler
import dev.alllexey.itmowidgets.core.work.RefreshStepLog
import dev.alllexey.itmowidgets.feature.schedule.di.calendar.calendarIosModule
import dev.alllexey.itmowidgets.feature.schedule.domain.LessonFriendsRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.PhoneCalendars
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangeDigest
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangeNotifier
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangesScheduler
import dev.alllexey.itmowidgets.feature.schedule.presentation.ScheduleViewModel
import dev.alllexey.itmowidgets.feature.schedule.presentation.changes.ScheduleChangesViewModel
import dev.alllexey.itmowidgets.feature.schedule.presentation.details.LessonDetailsViewModel
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import okio.FileSystem
import okio.Path
import org.koin.core.Koin
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.module.Module
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import platform.UIKit.UIViewController

/**
 * The schedule routes' graph as the iOS app starts it (`IosKoinModules`): the core module, [scheduleModule],
 * [scheduleDataModule], [scheduleIosModule] and `calendarIosModule` on the demo session, with the account types, the
 * settings data (`settingsDataModule` on iOS), the change notifier and scheduler (`scheduleChangesIosModule` over the
 * system's centres), the pending sport rows (`sportModule`), the teacher tones (`reviewsModule`), the app refresh task
 * and its step log (the background module), the session's tokens and the simulator test binary's device stood in. The
 * test binary has no calendar access, as before the system prompt.
 */
class ScheduleIosModuleTest {

    private val root: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "itmo-schedule-test-${Random.nextLong().toULong()}"
    private val requests = mutableListOf<String>()

    init {
        FileSystem.SYSTEM.createDirectories(root)
    }

    @AfterTest
    fun deleteDevice() = FileSystem.SYSTEM.deleteRecursively(root)

    /** Koin's `verify()` is JVM-only; on Kotlin/Native every definition is resolved instead. */
    @OptIn(KoinInternalApi::class)
    @Test
    fun everyDefinitionOfTheScheduleRoutesResolvesWithoutARequest() {
        val koin = graph { BUILDINGS }
        val viewModels = setOf(ScheduleViewModel::class, ScheduleChangesViewModel::class, LessonDetailsViewModel::class)
        val definitions = koin.instanceRegistry.instances.values.map { it.beanDefinition }.distinct()

        definitions.filter { it.primaryType !in viewModels }
            .forEach { definition -> koin.get<Any>(definition.primaryType, definition.qualifier) }

        koin.get<ScheduleViewModel> { parametersOf(SavedStateHandle()) }
        koin.get<ScheduleViewModel> { parametersOf(SavedStateHandle(mapOf(ScheduleViewModel.ARG_USER_ISU to USER))) }
        koin.get<ScheduleChangesViewModel> { parametersOf(SavedStateHandle()) }
        koin.get<LessonDetailsViewModel> { parametersOf(SavedStateHandle(lessonArguments())) }
        assertEquals(emptyList(), requests)
        koin.close()
    }

    @Test
    fun withoutCalendarAccessTheSyncNeitherTurnsOnNorAsksMyItmo() = runTest {
        val koin = graph { BUILDINGS }

        assertFalse(koin.get<PhoneCalendars>().hasAccess())
        koin.get<CalendarSync>().requestSync()
        assertEquals(emptyList(), requests)
        koin.close()
    }

    @Test
    fun theDemoFriendsOnALessonNeedNoRequest() = runTest {
        val koin = graph { BUILDINGS }

        val friends = koin.get<LessonFriendsRepository>().friendsOnLesson(PAIR_ID, LocalDate.parse(DATE))

        assertIs<AppResult.Success<*>>(friends)
        assertEquals(emptyList(), requests)
        koin.close()
    }

    @Test
    fun theBundledBuildingsGiveTheMapItsPin() {
        val directory = graph { BUILDINGS }.get<BuildingDirectory>()

        val building = directory.find(buildingId = 7, mainBuildingId = null, buildingName = null)

        assertEquals("Test building", building?.label)
        assertEquals(59.9, building?.toMapDestination()?.latitude)
    }

    @Test
    fun aMissingOrDamagedBuildingsFileLeavesTheRawAddress() {
        assertTrue(graph { null }.get<BuildingDirectory>().all.isEmpty())
        assertTrue(graph { "not json" }.get<BuildingDirectory>().all.isEmpty())
    }

    private fun graph(buildings: () -> String?): Koin = koinApplication {
        allowOverride(true)
        modules(
            iosCoreModule(FakeHost(), ORIGIN),
            scheduleModule,
            scheduleDataModule,
            scheduleIosModule(buildings),
            calendarIosModule,
            platformTypes(),
            testDevice(),
        )
    }.koin

    private fun lessonArguments(): Map<String, Any> = mapOf(
        LessonDetailsViewModel.ARG_PAIR_ID to PAIR_ID,
        LessonDetailsViewModel.ARG_DATE to DATE,
        LessonDetailsViewModel.ARG_TEACHER_ISU to USER,
    )

    /** What the account, settings and background modules give the app, on the demo session. */
    private fun platformTypes() = module {
        single<DemoMode> { FakeDemoMode(active = true) }
        single<SessionRepository> { FakeSessionRepository(SessionState.SignedIn(user = null, demo = true)) }
        // The Keychain is out of a test binary's reach.
        single<SessionTokenStore> { FakeSessionTokenStore() }
        single<CustomServicesRepository> { FakeCustomServicesRepository(enabled = true) }
        single<SchedulePreferencesRepository> { FakeSchedulePreferencesRepository() }
        single<AppNotifier> { RecordingAppNotifier() }
        single<ScheduleChangeNotifier> { SilentNotifier }
        single<ScheduleChangesScheduler> { IdleScheduler }
        // `sportModule` (IO-09c) and `reviewsModule` (IO-09f) in the app, features this module cannot read.
        single<PendingSportBookingsRepository> { NoPendingSport }
        single<TeacherLevelsRepository> { FakeTeacherLevelsRepository() }
        single { AppRefreshScheduler({ _, _ -> }, get(), get()) }
        single<RefreshStepLog> { NoStepLog }
    }

    /** A counting engine, directories and the App Group under the test's temporary directory, no main queue. */
    private fun testDevice() = module {
        single<HttpClientEngine> {
            MockEngine { request ->
                requests += request.url.toString()
                respondError(HttpStatusCode.ServiceUnavailable)
            }
        }
        single<AppDirectories> {
            object : AppDirectories {
                override val files = root / "files"
                override val cache = root / "caches"
                override val noBackup = root / "no-backup"
            }
        }
        single<AppGroupDirectory> {
            AppGroupDirectory.resolve(
                identifiers = BundleIdentifiers(appGroupId = "group.test.itmo", keychainGroup = null),
                appDirectories = get(),
                log = get<AppLog>(),
                containerOf = { root / "group" },
            )
        }
        single<AppDispatchers> { AppDispatchers(Dispatchers.Default, Dispatchers.Default, Dispatchers.Default) }
    }

    private object NoPendingSport : PendingSportBookingsRepository {
        override fun observePendingBookings(): Flow<AppResult<List<PendingSportBooking>>> =
            flowOf(AppResult.Success(emptyList()))

        override suspend fun refresh() = Unit
    }

    private object SilentNotifier : ScheduleChangeNotifier {
        override fun show(digest: ScheduleChangeDigest) = Unit
    }

    private object IdleScheduler : ScheduleChangesScheduler {
        override fun ensurePeriodic() = Unit

        override fun runOnce() = Unit

        override fun cancel() = Unit
    }

    private object NoStepLog : RefreshStepLog {
        override fun dueAt(key: String): Instant? = null

        override fun retries(key: String): Int = 0

        override fun record(key: String, dueAt: Instant, retries: Int) = Unit

        override fun forget(key: String) = Unit

        override fun forgetAll() = Unit
    }

    private class FakeHost : IosCoreHost {
        override fun topViewController(): UIViewController? = null

        override fun clearWebsiteData(completion: () -> Unit) = completion()

        override fun reload(kind: String) = Unit
    }

    private companion object {
        const val ORIGIN = "https://dev.widgets.alllexey.dev"
        const val USER = 100_001
        const val DATE = "2026-10-08"
        const val PAIR_ID = 20_369_084L

        /** One synthetic building in the shape of `itmo_buildings.json`. */
        const val BUILDINGS = """[{"id":"test","buildingIds":[7],"aliases":["тест"],"label":"Test building",
            "address":"Test street 1","latitude":59.9,"longitude":30.3}]"""
    }
}
