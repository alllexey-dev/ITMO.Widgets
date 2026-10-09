package dev.alllexey.itmowidgets.feature.recordbook.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.demo.DemoStudy
import dev.alllexey.itmowidgets.core.di.iosCoreModule
import dev.alllexey.itmowidgets.core.notification.AppNotification
import dev.alllexey.itmowidgets.core.notification.AppNotifier
import dev.alllexey.itmowidgets.core.notification.IosAppNotifier
import dev.alllexey.itmowidgets.core.notification.LocalNotificationCenter
import dev.alllexey.itmowidgets.core.notification.LocalNotificationRequest
import dev.alllexey.itmowidgets.core.platform.BundleIdentifiers
import dev.alllexey.itmowidgets.core.navigation.RecordbookSubjectArgs
import dev.alllexey.itmowidgets.core.navigation.SheetScoresArgs
import dev.alllexey.itmowidgets.core.platform.IosCoreHost
import dev.alllexey.itmowidgets.core.resources.SubjectLinksRepository
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.reviews.TeacherLevelsRepository
import dev.alllexey.itmowidgets.core.schedule.ScheduleRefreshGateway
import dev.alllexey.itmowidgets.core.schedule.SubjectLesson
import dev.alllexey.itmowidgets.core.schedule.SubjectLessonsGateway
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.core.sport.SportScorePeriod
import dev.alllexey.itmowidgets.core.sport.SportScoreRepository
import dev.alllexey.itmowidgets.core.sport.SportScoreSummary
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.storage.AppGroupDirectory
import dev.alllexey.itmowidgets.core.storage.SecureStore
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.FakeSubjectLinksRepository
import dev.alllexey.itmowidgets.core.testing.FakeSessionRepository
import dev.alllexey.itmowidgets.core.testing.InMemorySecureStore
import dev.alllexey.itmowidgets.core.testing.RecordingAppLog
import dev.alllexey.itmowidgets.core.work.AppRefreshScheduler
import dev.alllexey.itmowidgets.core.work.CheckOutcome
import dev.alllexey.itmowidgets.core.work.RefreshStep
import dev.alllexey.itmowidgets.core.work.RefreshStepKeys
import dev.alllexey.itmowidgets.core.work.RefreshStepLog
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsClient
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsSilentLogin
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsTokenStore
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsWebHost
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsWebLoad
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsWebNavigation
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.ItmoIdCookieExport
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.ItmoIdCookieExportOnSignIn
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.ItmoIdCookies
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.KeychainItmoIdCookies
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.OwnerBoundBarsStorage
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.WebKitCookie
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.WebViewBarsSilentLogin
import dev.alllexey.itmowidgets.feature.recordbook.data.marks.IosMarksNotifier
import dev.alllexey.itmowidgets.feature.recordbook.data.marks.MarksCheck
import dev.alllexey.itmowidgets.feature.recordbook.data.marks.MorningMarksNotifier
import dev.alllexey.itmowidgets.feature.recordbook.data.marks.RefreshTaskMarksScheduler
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarksNotifier
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarksScheduler
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookProgram
import dev.alllexey.itmowidgets.feature.recordbook.presentation.BarsLoginViewModel
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSubjectViewModel
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookViewModel
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.SheetScoresViewModel
import dev.alllexey.itmowidgets.feature.recordbook.ui.handleEntries
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import kotlinx.datetime.LocalDate
import org.koin.core.Koin
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.module.Module
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import platform.UIKit.UIViewController

/**
 * The recordbook graph on its iOS ports, as `IosKoinModules` loads it, with the session's types and the subject page's
 * ports from other features (the schedule's lessons, the sport score, the teacher tones) faked.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecordbookIosModuleTest {

    private val root: Path =
        FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "itmo-recordbook-test-${Random.nextLong().toULong()}"
    private val host = FakeWebHost()
    private val secrets = InMemorySecureStore()
    private val requests = mutableListOf<String>()

    @AfterTest
    fun deleteRoot() = FileSystem.SYSTEM.deleteRecursively(root)

    @Test
    fun theGraphHoldsOneBarsClientOneSessionStoreAndTheIosPorts() {
        val koin = graph()

        assertEquals(1, koin.getAll<BarsClient>().size)
        assertSame(koin.get<BarsClient>(), koin.get<BarsClient>())
        assertEquals(1, koin.getAll<OwnerBoundBarsStorage>().size)
        assertEquals(1, koin.getAll<BarsTokenStore>().size)
        assertIs<WebViewBarsSilentLogin>(koin.get<BarsSilentLogin>())
        assertSame(koin.get<KeychainItmoIdCookies>(), koin.get<ItmoIdCookies>())
        // BARS has its own engine, never MyITMO's.
        assertNotSame(koin.get<HttpClientEngine>(), koin.get<HttpClientEngine>(barsEngineQualifier))
        koin.close()
    }

    @Test
    fun everySessionCleanerOfTheGraphResolvesAndSignsOutWithoutARequest() = runTest {
        val koin = graph()

        val cleaners = koin.getAll<SessionDataCleaner>()
        // The core's three (Keychain, App Group, WebKit) and the recordbook's six.
        assertEquals(9, cleaners.size)
        withContext(Dispatchers.Default) {
            koin.get<BarsTokenStore>().install(ISU, "Bearer synthetic-bars-header")
            cleaners.filter { it.javaClassName() != KEYCHAIN_CLEANER }.forEach { it.clearSessionData() }
        }
        assertTrue("bars_tokens.enc" !in secrets.values)
        assertEquals(emptyList(), requests)
        koin.close()
    }

    @Test
    fun theBarsLoginViewModelTakesAFreshSavedStateHandleAsItsParameter() {
        val koin = graph()

        val first = koin.get<BarsLoginViewModel> { parametersOf(*BarsLoginParameters.fresh().toTypedArray()) }
        val second = koin.get<BarsLoginViewModel> { parametersOf(*BarsLoginParameters.fresh().toTypedArray()) }

        assertTrue(first.loginUrl.startsWith("https://id.itmo.ru/"))
        assertEquals(first.loginUrl, first.loginUrl)
        assertNotEquals(first.loginUrl, second.loginUrl)
        koin.close()
    }

    @Test
    fun theCookiesAreCopiedAfterAnInteractiveSignInOnly() = runTest(UnconfinedTestDispatcher()) {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val dispatchers = AppDispatchers(dispatcher, dispatcher, dispatcher)
        val cookies = KeychainItmoIdCookies(secrets, Clock.System, dispatchers)
        val session = FakeSessionRepository(SessionState.Initializing)
        host.cookies = listOf(WebKitCookie("KEYCLOAK_IDENTITY", "identity", "id.itmo.ru", "/", true, null))
        ItmoIdCookieExportOnSignIn(session, ItmoIdCookieExport(host, cookies, dispatchers, RecordingAppLog()))
            .launchIn(backgroundScope)

        // A launch that reads the stored session copies nothing; neither does the demo.
        session.mutableState.value = SessionState.SignedIn(USER)
        session.mutableState.value = SessionState.SignedOut
        session.mutableState.value = SessionState.SignedIn(USER, demo = true)
        session.mutableState.value = SessionState.SignedOut
        assertEquals(0, host.cookieReads)

        session.mutableState.value = SessionState.SignedIn(USER)
        assertEquals(1, host.cookieReads)
        assertTrue(KeychainItmoIdCookies.ITEM in secrets.values)
        session.mutableState.value = SessionState.ReauthenticationRequired
        session.mutableState.value = SessionState.SignedIn(USER)
        assertEquals(2, host.cookieReads)
    }

    /**
     * Koin's `verify()` is JVM-only; on Kotlin/Native every definition is resolved instead, each with the arguments the
     * Swift hosts give the subject page and `Мои баллы` (unused by the others), on the demo session, the background
     * mark check ([MarksCheck]) and its step included.
     */
    @OptIn(KoinInternalApi::class)
    @Test
    fun everyDefinitionOfTheRecordbookScreensResolvesWithoutARequest() {
        val koin = graph(demo = true)
        val definitions = koin.instanceRegistry.instances.values.map { it.beanDefinition }.distinct()

        definitions.forEach { definition ->
            koin.get<Any>(definition.primaryType, definition.qualifier) { parametersOf(screenArguments()) }
        }

        koin.get<RecordbookViewModel> { parametersOf(SavedStateHandle()) }
        koin.get<RecordbookSubjectViewModel> { parametersOf(screenArguments()) }
        koin.get<SheetScoresViewModel> { parametersOf(screenArguments()) }
        assertEquals(emptyList(), requests)
        koin.close()
    }

    @Test
    fun theMarkCheckIsAStepOfTheAppRefreshTaskWithAndroidsThreeHours() = runTest {
        val koin = graph()

        val step = koin.get<RefreshStep>(named(RefreshStepKeys.MARKS))

        assertEquals(RefreshStepKeys.MARKS, step.key)
        assertEquals(3.hours, step.period)
        assertIs<RefreshTaskMarksScheduler>(koin.get<MarksScheduler>())
        assertIs<IosMarksNotifier>(koin.get<MarksNotifier>())
        assertSame(koin.get<MarksNotifier>(), koin.get<MorningMarksNotifier>())
        // The test session holds no ITMO.ID token: the check skips without a request.
        assertEquals(CheckOutcome.SKIPPED, withContext(Dispatchers.Default) { step.action() })
        assertEquals(emptyList(), requests)
        koin.close()
    }

    @Test
    fun theDemoAnswersTheRecordbookWithoutARequest() = runTest {
        val koin = graph(demo = true)

        val programs = koin.get<RecordbookRepository>().getPrograms()

        val program = assertIs<AppResult.Success<List<RecordbookProgram>>>(programs).value.single()
        assertEquals(DemoStudy.PROGRAM_ID, program.id)
        assertTrue(program.periods.isNotEmpty())
        assertEquals(emptyList(), requests)
        koin.close()
    }

    /** The subject page's and the sheet's arguments in one handle: the keys of the two never collide. */
    private fun screenArguments(): SavedStateHandle =
        SavedStateHandle(mapOf(*SUBJECT.handleEntries(), *SCORES.handleEntries()))

    private fun graph(demo: Boolean = false): Koin = koinApplication {
        allowOverride(true)
        modules(
            iosCoreModule(host, ORIGIN),
            recordbookModule,
            recordbookIosModule(host),
            testDevice(demo),
            subjectPagePorts(),
        )
    }.koin

    /**
     * What the subject page reads from other features' graphs in the app: the own lessons and their refresh
     * (`scheduleDataModule`), the sport score (`sportModule`), the teacher tones (`reviewsModule`) and the subject
     * links (`resourcesModule`).
     */
    private fun subjectPagePorts(): Module = module {
        single<SubjectLessonsGateway> {
            object : SubjectLessonsGateway {
                override fun observeOwnLessons(start: LocalDate, end: LocalDate): Flow<List<SubjectLesson>> =
                    flowOf(emptyList())
            }
        }
        single<ScheduleRefreshGateway> {
            object : ScheduleRefreshGateway {
                override suspend fun refreshOwnSchedule(startDate: LocalDate, endDate: LocalDate) =
                    AppResult.Success(Unit)
            }
        }
        single<SportScoreRepository> {
            object : SportScoreRepository {
                override suspend fun getScorePeriods(): AppResult<List<SportScorePeriod>> =
                    AppResult.Success(emptyList())

                override suspend fun getScoreSummary(semesterId: Long): AppResult<SportScoreSummary> =
                    AppResult.Failure(AppError.NotFound)
            }
        }
        single<TeacherLevelsRepository> {
            object : TeacherLevelsRepository {
                override suspend fun levels(isus: Set<Int>): Map<Int, TeacherLevel> = emptyMap()
            }
        }
        single<SubjectLinksRepository> { FakeSubjectLinksRepository() }
    }

    /** The session's types (the account module's), a counting engine, the test's own directories and no Keychain. */
    private fun testDevice(demo: Boolean = false): Module = module {
        val engine = MockEngine { request ->
            requests += request.url.toString()
            respondError(HttpStatusCode.ServiceUnavailable)
        }
        single<HttpClientEngine> { engine }
        single<HttpClientEngine>(barsEngineQualifier) {
            MockEngine { request ->
                requests += request.url.toString()
                respondError(HttpStatusCode.ServiceUnavailable)
            }
        }
        single<SecureStore> { secrets }
        // The app graph's notifier, refresh task and step log are `iosBackgroundModule`'s (IO-14); this graph posts
        // and schedules nothing.
        single<AppNotifier> { SilentNotifier }
        single { IosAppNotifier(SilentCenter, get(), get()) }
        single { AppRefreshScheduler({ _, _ -> }, Clock.System, get()) }
        single<RefreshStepLog> { NoStepLog }
        single<AppDirectories> {
            object : AppDirectories {
                override val files = root / "files"
                override val cache = root / "caches"
                override val noBackup = root / "no-backup"
            }
        }
        single {
            AppGroupDirectory.resolve(
                identifiers = BundleIdentifiers(appGroupId = "group.test.itmo", keychainGroup = null),
                appDirectories = get(),
                log = get(),
                containerOf = { root / "group" },
            )
        }
        single<AppDispatchers> { AppDispatchers(Dispatchers.Default, Dispatchers.Default, Dispatchers.Default) }
        single<DemoMode> { FakeDemoMode(active = demo) }
        single<SessionRepository> { FakeSessionRepository(SessionState.SignedOut) }
        single<CurrentUserProvider> {
            object : CurrentUserProvider {
                override suspend fun getCurrentUser(): CurrentUser = USER
            }
        }
    }

    private fun SessionDataCleaner.javaClassName(): String = this::class.simpleName.orEmpty()

    private class FakeWebHost : BarsWebHost, IosCoreHost {
        var cookies: List<WebKitCookie> = emptyList()
        var cookieReads = 0

        override fun webKitCookies(completion: (List<WebKitCookie>) -> Unit) {
            cookieReads += 1
            completion(cookies)
        }

        override fun loadHiddenBarsPage(
            url: String,
            navigation: BarsWebNavigation,
            completion: (String?) -> Unit,
        ): BarsWebLoad {
            completion(null)
            return object : BarsWebLoad {
                override fun cancel() = Unit
            }
        }

        override fun topViewController(): UIViewController? = null

        override fun clearWebsiteData(completion: () -> Unit) = completion()

        override fun reload(kind: String) = Unit
    }

    private companion object {
        const val ORIGIN = "https://dev.widgets.alllexey.dev"
        const val ISU = 123456
        const val KEYCHAIN_CLEANER = "KeychainSessionDataCleaner"
        val USER = CurrentUser(isu = ISU, name = null, pictureUrl = null)
        val SUBJECT = RecordbookSubjectArgs(
            entryId = 11,
            programId = DemoStudy.PROGRAM_ID,
            semester = 3,
            studyYear = "2026/2027",
        )
        val SCORES = SheetScoresArgs(
            subjectId = 5,
            subjectName = "Subject",
            periodKey = "2026/2027:1",
            url = "https://docs.google.com/spreadsheets/d/x",
            step = SheetScoresArgs.Step.TOTAL,
        )
    }
}

private object SilentCenter : LocalNotificationCenter {
    override fun add(request: LocalNotificationRequest) = Unit

    override fun remove(identifier: String) = Unit

    override fun removeAll() = Unit
}

private object NoStepLog : RefreshStepLog {
    override fun dueAt(key: String): Instant? = null

    override fun retries(key: String): Int = 0

    override fun record(key: String, dueAt: Instant, retries: Int) = Unit

    override fun forget(key: String) = Unit

    override fun forgetAll() = Unit
}

private object SilentNotifier : AppNotifier {
    override fun show(notification: AppNotification) = Unit

    override fun cancel(channel: String, id: Int) = Unit

    override fun clear() = Unit
}
