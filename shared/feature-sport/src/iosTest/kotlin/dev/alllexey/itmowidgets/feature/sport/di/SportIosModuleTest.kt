package dev.alllexey.itmowidgets.feature.sport.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.demo.DemoPeople
import dev.alllexey.itmowidgets.core.di.iosCoreModule
import dev.alllexey.itmowidgets.core.diagnostics.AppLog
import dev.alllexey.itmowidgets.core.friend.FriendRepository
import dev.alllexey.itmowidgets.core.location.MapDestination
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.navigation.AppLink
import dev.alllexey.itmowidgets.core.navigation.AppLinks
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.platform.BundleIdentifiers
import dev.alllexey.itmowidgets.core.platform.IosCoreHost
import dev.alllexey.itmowidgets.core.platform.PlatformActions
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.schedule.ScheduleRefreshGateway
import dev.alllexey.itmowidgets.core.schedule.ScheduleWidgetRefreshRequester
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.storage.AppGroupDirectory
import dev.alllexey.itmowidgets.core.testing.FakeCustomServicesRepository
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportActionRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.UserSportBookings
import dev.alllexey.itmowidgets.feature.sport.domain.repository.UserSportRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportShareTarget
import dev.alllexey.itmowidgets.feature.sport.presentation.my.SportMyViewModel
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignViewModel
import dev.alllexey.itmowidgets.feature.sport.presentation.user.UserSportViewModel
import dev.alllexey.itmowidgets.feature.sport.ui.SportShares
import dev.alllexey.itmowidgets.feature.sport.ui.details.SportDetailsSamples
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
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
 * The sport tab's graph as the iOS app starts it (`IosKoinModules`): the core module, [sportModule] and
 * [sportIosModule], with the account types, the services opt-in (`settingsDataModule` on iOS), the friend picker's
 * `FriendRepository` (`friendSelectorModule`, a feature this module cannot read), the schedule widget refresh, the
 * schedule refresh after a booking (`scheduleDataModule`) and the simulator test binary's device stood in, on the demo session: the screens resolve and answer from the demo set with
 * no request, and a shared lesson names the build's site.
 */
class SportIosModuleTest {

    private val root: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "itmo-sport-test-${Random.nextLong().toULong()}"
    private val requests = mutableListOf<String>()
    private val shared = mutableListOf<Pair<String, String>>()

    init {
        FileSystem.SYSTEM.createDirectories(root)
    }

    @AfterTest
    fun deleteDevice() = FileSystem.SYSTEM.deleteRecursively(root)

    /**
     * Koin's `verify()` is JVM-only; on Kotlin/Native every definition is resolved instead, each with the arguments
     * the Swift host gives another user's sport (unused by the others).
     */
    @OptIn(KoinInternalApi::class)
    @Test
    fun everyDefinitionOfTheSportRoutesResolvesWithoutARequest() {
        val koin = graph()
        val definitions = koin.instanceRegistry.instances.values.map { it.beanDefinition }.distinct()

        definitions.forEach { definition ->
            koin.get<Any>(definition.primaryType, definition.qualifier) { parametersOf(arguments()) }
        }

        koin.get<SportMyViewModel>()
        koin.get<SportSignViewModel>()
        koin.get<UserSportViewModel> { parametersOf(arguments()) }
        assertEquals(emptyList(), requests)
        koin.close()
    }

    @Test
    fun theDemoAnswersAnotherUsersSportAndRefusesABookingWithoutARequest() = runTest {
        val koin = graph()

        val ivan = koin.get<UserSportRepository>().getUserBookings(DemoPeople.IVAN.isu)
        val sign = koin.get<SportActionRepository>().signIn(SportDetailsSamples.open.lessonId)

        assertTrue(assertIs<AppResult.Success<UserSportBookings>>(ivan).value.confirmedLessonIds.isNotEmpty())
        assertEquals(AppResult.Failure(AppError.DemoUnavailable), sign)
        assertEquals(emptyList(), requests)
        koin.close()
    }

    @Test
    fun aSharedLessonOrPredictionNamesTheBuildsSiteAndParsesBack() = runTest {
        val koin = graph()
        val shares = koin.get<SportShares>()
        val item = SportDetailsSamples.open

        shares.lesson(item, SportShareTarget.Lesson(item.lessonId))
        shares.lesson(item, SportShareTarget.Prediction(PROTOTYPE))

        val (title, lesson) = shared[0]
        val prediction = shared[1].second
        assertEquals("Занятие по спорту в ITMO.Widgets", title)
        assertTrue(lesson.startsWith("${item.sectionName}, "), lesson)
        val lessonLink = lesson.substringAfterLast(' ')
        assertEquals("$ORIGIN/sport/${item.lessonId}", lessonLink)
        assertEquals(AppLink.SportLesson(item.lessonId), AppLinks.parse(lessonLink))
        assertEquals(AppLink.PredictedSportLesson(PROTOTYPE), AppLinks.parse(prediction.substringAfterLast(' ')))
        koin.close()
    }

    private fun arguments() = SavedStateHandle(
        mapOf(UserScreenArgs.ISU to DemoPeople.IVAN.isu, UserScreenArgs.NAME to DemoPeople.IVAN.name),
    )

    private fun graph(): Koin = koinApplication {
        allowOverride(true)
        modules(iosCoreModule(FakeHost(), ORIGIN), sportModule, sportIosModule, platformTypes(), testDevice())
    }.koin

    private fun platformTypes(): Module = module {
        single<DemoMode> { FakeDemoMode(active = true) }
        single<CustomServicesRepository> { FakeCustomServicesRepository(enabled = true) }
        single<FriendRepository> { NoFriends }
        single<ScheduleWidgetRefreshRequester> { ScheduleWidgetRefreshRequester {} }
        single<ScheduleRefreshGateway> { NoScheduleRefresh }
        single<PlatformActions> { RecordingShares(shared) }
    }

    /** A counting engine, directories and the App Group under the test's temporary directory, no main queue. */
    private fun testDevice(): Module = module {
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

    private object NoFriends : FriendRepository {
        override fun observeFriendList(): Flow<LoadState<List<UserSummary>>> = flowOf()
        override fun observeCurrentUser(): Flow<UserSummary?> = flowOf(null)
        override suspend fun refreshFriendList() = Unit
        override val currentFriends: List<UserSummary>? = null
    }

    /** The schedule refresh after a booking, which `scheduleDataModule` binds in the app: no schedule to refresh. */
    private object NoScheduleRefresh : ScheduleRefreshGateway {
        override suspend fun refreshOwnSchedule(startDate: LocalDate, endDate: LocalDate): AppResult<Unit> =
            AppResult.Success(Unit)
    }

    /** The share sheet's title and text; the other actions open nothing. */
    private class RecordingShares(private val shared: MutableList<Pair<String, String>>) : PlatformActions {
        override fun shareText(title: String, text: String): Boolean = shared.add(title to text)
        override fun openLink(url: String) = false
        override fun openMap(destination: MapDestination) = false
        override fun openAppSettings() = false
        override fun openNotificationSettings() = false
    }

    private class FakeHost : IosCoreHost {
        override fun topViewController(): UIViewController? = null

        override fun clearWebsiteData(completion: () -> Unit) = completion()

        override fun reload(kind: String) = Unit
    }

    private companion object {
        const val ORIGIN = "https://dev.widgets.alllexey.dev"
        const val PROTOTYPE = 4242L
    }
}
