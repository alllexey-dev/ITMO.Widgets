package dev.alllexey.itmowidgets.app.shell.entries

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import dev.alllexey.itmowidgets.app.shell.EntryRegistry
import dev.alllexey.itmowidgets.app.shell.Nav3AppNavigator
import dev.alllexey.itmowidgets.app.shell.ShellContent
import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.home.HomeCardKind
import dev.alllexey.itmowidgets.core.home.HomeCardRenderer
import dev.alllexey.itmowidgets.core.home.HomeCardSource
import dev.alllexey.itmowidgets.core.home.HomeCardTestTags
import dev.alllexey.itmowidgets.core.home.HomeHint
import dev.alllexey.itmowidgets.core.home.HomeLessonState
import dev.alllexey.itmowidgets.core.home.HomeScheduleRow
import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.ShellSurface
import dev.alllexey.itmowidgets.core.navigation.toDetailsArgs
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.LessonSlot
import dev.alllexey.itmowidgets.core.schedule.ScheduleChange
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeField
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeKind
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.core.sport.SportScoreSummary
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.di.bridge.StopKoinRule
import dev.alllexey.itmowidgets.feature.debug.ui.PreviewHostApplication
import dev.alllexey.itmowidgets.feature.home.di.hintCardsQualifier
import dev.alllexey.itmowidgets.feature.home.domain.HomeCardPreferences
import dev.alllexey.itmowidgets.feature.home.domain.HomeHintStore
import dev.alllexey.itmowidgets.feature.home.presentation.HomeViewModel
import dev.alllexey.itmowidgets.feature.home.ui.HintHomeCardRenderer
import dev.alllexey.itmowidgets.feature.home.ui.HomeTestTags
import dev.alllexey.itmowidgets.core.settings.QrAnimationType
import dev.alllexey.itmowidgets.feature.qr.domain.QrAppearancePreferences
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeRepository
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeSnapshot
import dev.alllexey.itmowidgets.feature.qr.presentation.QrCodeViewModel
import dev.alllexey.itmowidgets.feature.qr.ui.QrPassTestTags
import dev.alllexey.itmowidgets.feature.recordbook.ui.home.MarksHomeCardRenderer
import dev.alllexey.itmowidgets.feature.schedule.ui.home.ScheduleHomeCardRenderer
import dev.alllexey.itmowidgets.feature.social.ui.home.FriendRequestsHomeCardRenderer
import dev.alllexey.itmowidgets.feature.sport.ui.home.SportHomeCardRenderer
import dev.alllexey.itmowidgets.testkit.FakeClock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.compose.KoinContext
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The home tab root and the QR pass in the Compose shell with the real [shellEntries] and Koin ViewModels on
 * synthetic data: each key renders its screen, and every home card opens its route-map route (SH-0).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = PreviewHostApplication::class)
class HomeEntriesTest {

    @get:Rule(order = 0)
    val stopKoin = StopKoinRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val navigator = Nav3AppNavigator()

    /** The sport rows go through the sport redirect, whose holder finds no booking here. */
    private val sportScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Before
    fun startGraph() {
        val clock = FakeClock(Instant.fromEpochMilliseconds(0))
        val koin = startKoin {
            modules(
                module {
                    viewModel {
                        HomeViewModel(listOf(FakeHomeSource), NothingHidden, NoHintDismissed, clock)
                    }
                    viewModel { QrCodeViewModel(FakeQrRepository, PlainQrAppearance, clock) }
                    single<HomeCardRenderer>(named("schedule")) { ScheduleHomeCardRenderer(ZONE) }
                    single<HomeCardRenderer>(named("recordbook")) { MarksHomeCardRenderer }
                    single<HomeCardRenderer>(named("sport")) { SportHomeCardRenderer(ZONE) }
                    single<HomeCardRenderer>(named("social")) { FriendRequestsHomeCardRenderer }
                    single<HomeCardRenderer>(hintCardsQualifier) { HintHomeCardRenderer }
                },
                socialTestModule(),
                scheduleTestModule(),
                SportTestGraph(sportScope).module(),
            )
        }.koin
        compose.setContent {
            // Koin Compose caches the first graph it reads for the JVM; this test's graph replaces the stopped one.
            KoinContext(koin) {
                ItmoTheme { ShellContent(navigator, shellEntries(), ShellSurface.Tabs(demoBanner = false), onDemoSignIn = {}) }
            }
        }
        compose.waitForIdle()
    }

    @After
    fun stopSportScope() = sportScope.cancel()

    @Test
    fun homeTabRootRendersTheFeed() {
        compose.onNodeWithTag(HomeTestTags.FEED).assertIsDisplayed()
        compose.onNodeWithTag(EntryRegistry.placeholderTag(AppRoutes.TabRoot(AppTab.HOME))).assertDoesNotExist()
    }

    @Test
    fun qrPassRendersItsScreenAndBackClosesIt() {
        act { open(AppRoutes.QrPass) }

        compose.onNodeWithTag(QrPassTestTags.AREA).assertIsDisplayed()
        compose.onNodeWithContentDescription(BACK).performClick()
        compose.waitForIdle()
        assertTrue(navigator.state.overlays.isEmpty())
        compose.onNodeWithTag(HomeTestTags.FEED).assertIsDisplayed()
    }

    @Test
    fun scheduleRowsOpenTheirSheets() {
        compose.onAllNodesWithTag(HomeCardTestTags.SCHEDULE_ROW).onFirst().performClick()
        compose.waitForIdle()
        assertEquals(listOf(AppRoutes.LessonDetails(LESSON)), navigator.state.floating)

        act { select(AppTab.HOME) }
        compose.onAllNodesWithTag(HomeCardTestTags.SCHEDULE_ROW)[1].performClick()
        compose.waitForIdle()
        assertEquals(listOf(AppRoutes.PendingSportDetails(PENDING)), navigator.state.floating)
    }

    @Test
    fun cardsOpenTheirScreens() {
        assertOpens(listOf(AppRoutes.ScheduleChanges)) { card(HomeCardKind.SCHEDULE_CHANGES).performClick() }
        assertOpens(listOf(AppRoutes.UserProfile(FRIEND_ISU))) {
            scrollTo(HomeCardTestTags.FRIEND_ROW)
            compose.onAllNodesWithTag(HomeCardTestTags.FRIEND_ROW).onFirst().performClick()
        }
        assertOpens(listOf(AppRoutes.Friends)) {
            scrollTo(HomeCardTestTags.FRIENDS_ALL)
            compose.onNodeWithTag(HomeCardTestTags.FRIENDS_ALL).performClick()
        }
        assertOpens(listOf(AppRoutes.Settings(page = "SERVICES"))) {
            scrollTo(HomeCardTestTags.HINT_ACTION)
            compose.onNodeWithTag(HomeCardTestTags.HINT_ACTION).performClick()
        }
        assertOpens(listOf(AppRoutes.MyItmoWeb)) { compose.onNodeWithTag(HomeTestTags.WEB_FAB).performClick() }
        assertOpens(listOf(AppRoutes.QrPass)) { compose.onNodeWithTag(HomeTestTags.QR_FAB).performClick() }
    }

    @Test
    fun cardsSelectTheirTabs() {
        card(HomeCardKind.MARKS).performClick()
        compose.waitForIdle()
        assertEquals(AppTab.RECORDBOOK, navigator.tab)

        act { select(AppTab.HOME) }
        card(HomeCardKind.SPORT).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals(AppTab.SPORT, navigator.tab)
        assertTrue(navigator.state.overlays.isEmpty())
    }

    private fun act(block: Nav3AppNavigator.() -> Unit) {
        compose.runOnIdle { navigator.block() }
        compose.waitForIdle()
    }

    /** [click] on the home feed opens exactly [overlays]; the home tab is selected again afterwards. */
    private fun assertOpens(overlays: List<AppRoute>, click: () -> Unit) {
        click()
        compose.waitForIdle()
        assertEquals(AppTab.HOME, navigator.tab)
        assertEquals(overlays, navigator.state.overlays)
        act { select(AppTab.HOME) }
    }

    private fun card(kind: HomeCardKind): SemanticsNodeInteraction {
        scrollTo(HomeTestTags.card(kind))
        return compose.onNodeWithTag(HomeTestTags.card(kind))
    }

    private fun scrollTo(tag: String) {
        compose.onNodeWithTag(HomeTestTags.FEED).performScrollToNode(hasTestTag(tag))
        compose.waitForIdle()
    }

    private object FakeHomeSource : HomeCardSource {
        override fun observe(): Flow<List<HomeCard>> = flowOf(CARDS)
        override suspend fun refresh(): AppResult<Unit> = AppResult.Success(Unit)
    }

    private object NothingHidden : HomeCardPreferences {
        override fun observeHidden(): Flow<Set<HomeCardKind>> = flowOf(emptySet())
    }

    private object NoHintDismissed : HomeHintStore {
        override fun observeDismissed(): Flow<Set<HomeHint>> = flowOf(emptySet())
        override suspend fun dismiss(hint: HomeHint) = Unit
    }

    private object FakeQrRepository : QrCodeRepository {
        private val code = QrCodeSnapshot("0123456789ABCDEF", expiresAtMillis = 600_000)
        override suspend fun currentQr() = code
        override fun observeQrHex() = flowOf(code.hex)
        override suspend fun currentQrHex(allowExpired: Boolean) = code.hex
        override suspend fun refreshQrHex(force: Boolean): AppResult<Unit> = AppResult.Success(Unit)
        override fun clearCache() = Unit
    }

    private object PlainQrAppearance : QrAppearancePreferences {
        override suspend fun useDynamicColors() = false
        override suspend fun isSpoilerEnabled() = false
        override suspend fun spoilerAnimationType() = QrAnimationType.entries.first()
    }

    private companion object {
        const val BACK = "Назад"
        const val FRIEND_ISU = 300001
        val DATE = LocalDate(2026, 9, 7)
        val ZONE: TimeZone = TimeZone.of("Europe/Moscow")

        val LESSON = LessonDetailsArgs(
            pairId = 1, date = DATE.toString(), subjectName = "Математический анализ", typeId = 1, format = "Очный",
            start = "09:30", end = "11:00", teacherFio = "Преподаватель Тестовый", teacherIsu = 300002, room = "1506",
            building = "Кронверкский проспект, 49", buildingId = 13, mainBuildingId = 13, note = null,
            zoomUrl = null, zoomPassword = null, zoomInfo = null,
        )
        val BOOKING = PendingSportBooking(
            queueId = 1, queueKind = PendingSportBooking.QueueKind.AUTO, lessonId = 101, sectionName = "Плавание",
            start = at(16, 0), end = at(17, 30), teacherFio = "Тренер Тестовый", roomName = "Бассейн", isPrediction = false,
        )
        val PENDING = BOOKING.toDetailsArgs(ZONE)

        val CARDS = listOf(
            HomeCard.Schedule(
                date = DATE,
                tomorrow = false,
                rows = listOf(
                    HomeScheduleRow.Lesson(LESSON, HomeLessonState.NEXT),
                    HomeScheduleRow.PendingSport(PENDING, predicted = false),
                ),
                completed = 0,
            ),
            HomeCard.ScheduleChanges(unread = 1, latest = change()),
            HomeCard.Marks(listOf("Базы данных")),
            HomeCard.Sport(SportScoreSummary(attendances = 50, bonus = 22), listOf(BOOKING)),
            HomeCard.FriendRequests(
                listOf(
                    UserSummary(
                        isu = FRIEND_ISU, name = "Иван Петров", pictureUrl = null,
                        groups = listOf(UserGroup("M3100", 1, "ФИТиП")),
                        sharing = UserSharing(sport = true, schedule = true),
                    ),
                ),
            ),
            HomeCard.Hint(HomeHint.SERVICES),
        )

        fun at(hour: Int, minute: Int): Instant = LocalDateTime(DATE, LocalTime(hour, minute)).toInstant(ZONE)

        fun change() = ScheduleChange(
            id = "test-1",
            detectedAt = at(9, 0),
            kind = ScheduleChangeKind.UPDATED,
            fields = setOf(ScheduleChangeField.TIME),
            subjectName = "Математический анализ",
            typeId = 1,
            flowName = null,
            before = slot(LocalDate(2026, 9, 8)),
            after = slot(LocalDate(2026, 9, 9)),
            read = false,
            notified = true,
        )

        fun slot(day: LocalDate) = LessonSlot(
            pairId = 1, date = day, start = LocalTime(10, 0), end = LocalTime(11, 30), room = "1506",
            building = "Кронверкский проспект, 49", formatId = 1, format = "Очный", teacherIsu = 300002,
            teacherName = "Преподаватель Тестовый",
        )
    }
}
