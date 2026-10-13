package dev.alllexey.itmowidgets.app.shell.entries

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import dev.alllexey.itmowidgets.app.shell.EntryRegistry
import dev.alllexey.itmowidgets.app.shell.Nav3AppNavigator
import dev.alllexey.itmowidgets.app.shell.ShellContent
import dev.alllexey.itmowidgets.core.bell.BellItem
import dev.alllexey.itmowidgets.core.bell.BellKind
import dev.alllexey.itmowidgets.core.lesson.DayLesson
import dev.alllexey.itmowidgets.core.lesson.LessonDay
import dev.alllexey.itmowidgets.core.lesson.LessonTarget
import dev.alllexey.itmowidgets.core.lesson.SportMark
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.EntryRoute
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.ShellSurface
import dev.alllexey.itmowidgets.core.navigation.toDetailsArgs
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.DayLessonsGateway
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.core.settings.QrAnimationType
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.core.testing.FakeSessionRepository
import dev.alllexey.itmowidgets.core.testing.FakeSocialRepository
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.text.AppIcon
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.today.TodayTodo
import dev.alllexey.itmowidgets.core.today.TodayTodoSource
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.di.bridge.StopKoinRule
import dev.alllexey.itmowidgets.feature.debug.ui.PreviewHostApplication
import dev.alllexey.itmowidgets.feature.home.domain.BellRepository
import dev.alllexey.itmowidgets.feature.home.presentation.BellViewModel
import dev.alllexey.itmowidgets.feature.home.presentation.TodayHeaderViewModel
import dev.alllexey.itmowidgets.feature.home.presentation.TodayViewModel
import dev.alllexey.itmowidgets.feature.home.ui.BellTestTags
import dev.alllexey.itmowidgets.feature.home.ui.TodayTestTags
import dev.alllexey.itmowidgets.feature.qr.domain.QrAppearancePreferences
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeRepository
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeSnapshot
import dev.alllexey.itmowidgets.feature.qr.presentation.QrPassViewModel
import dev.alllexey.itmowidgets.feature.qr.ui.QrPassTestTags
import dev.alllexey.itmowidgets.testkit.FakeClock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
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
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Сегодня, the bell's sheet and the pass in the Compose shell with the real [shellEntries] and Koin ViewModels on
 * synthetic data: each key renders its screen, lessons open their sheets, the bell opens its sheet and a row its home.
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
        val time = FixedAcademicTime(LocalDateTime(DATE, LocalTime(9, 0)))
        val koin = startKoin {
            modules(
                module {
                    viewModel { TodayViewModel(FakeDays, listOf(TodayTodoSource { flowOf(listOf(TODO)) }), time) }
                    viewModel { TodayHeaderViewModel(time, FakeBell) }
                    viewModel { BellViewModel(FakeBell, time) }
                    viewModel {
                        QrPassViewModel(FakeQrRepository, PlainQrAppearance, signedIn, FakeSocialRepository(), clock)
                    }
                },
                socialTestModule(),
                scheduleTestModule(),
                SportTestGraph(sportScope).module(),
                settingsTestModule(),
                RecordbookTestGraph().module(),
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
    fun todayTabRootRendersTheHeroAndTheDay() {
        compose.onNodeWithTag(TodayTestTags.HERO).assertIsDisplayed()
        scrollTo(TodayTestTags.DAY)
        compose.onNodeWithTag(TodayTestTags.DAY).assertIsDisplayed()
        compose.onNodeWithTag(EntryRegistry.placeholderTag(AppRoutes.TabRoot(AppTab.HOME))).assertDoesNotExist()
    }

    @Test
    fun thePassButtonOpensThePassSheetAndItsCloseButtonClosesIt() {
        compose.onNodeWithTag(TabRootTags.PASS_FAB).performClick()
        compose.waitForIdle()
        assertEquals(listOf(AppRoutes.QrPass), navigator.state.floating)

        compose.onNodeWithTag(QrPassTestTags.AREA).assertIsDisplayed()
        compose.onNodeWithContentDescription(CLOSE).performClick()
        compose.waitForIdle()
        assertTrue(navigator.state.floating.isEmpty())
        compose.onNodeWithTag(TodayTestTags.LIST).assertIsDisplayed()
    }

    @Test
    fun theBellOpensItsSheetWhoseRowLeadsHomeAndOnlyTodayHasTheBellAndThePass() {
        compose.onNodeWithTag(TabRootTags.BELL).performClick()
        compose.waitForIdle()
        assertEquals(listOf(AppRoutes.Bell), navigator.state.floating)

        compose.onNodeWithTag(BellTestTags.row(EVENT.id)).performClick()
        compose.waitForIdle()
        assertTrue(navigator.state.floating.isEmpty())
        assertEquals(listOf(AppRoutes.Friends), navigator.state.overlays)

        act { select(AppTab.SCHEDULE) }
        compose.onAllNodesWithTag(TabRootTags.BELL).assertCountEquals(0)
        compose.onAllNodesWithTag(TabRootTags.PASS_FAB).assertCountEquals(0)
    }

    @Test
    fun theHeroAndTheTimelineOpenTheirLessonSheets() {
        compose.onNodeWithTag(TodayTestTags.HERO).performClick()
        compose.waitForIdle()
        assertEquals(listOf(AppRoutes.LessonDetails(LESSON)), navigator.state.floating)

        act { select(AppTab.HOME) }
        // The last item lifts the day's end above the pass button.
        scrollTo(TodayTestTags.todo(TODO.id))
        compose.onNodeWithText(BOOKING.sectionName).performClick()
        compose.waitForIdle()
        assertEquals(listOf(AppRoutes.PendingSportDetails(PENDING)), navigator.state.floating)
    }

    @Test
    fun aThingToDoOpensItsRoute() {
        scrollTo(TodayTestTags.todo(TODO.id))
        compose.onNodeWithText(ACTION).performClick()
        compose.waitForIdle()
        assertEquals(listOf(AppRoutes.Friends), navigator.state.overlays)
    }

    private fun scrollTo(tag: String) {
        compose.onNodeWithTag(TodayTestTags.LIST).performScrollToNode(hasTestTag(tag))
        compose.waitForIdle()
    }

    private fun act(block: Nav3AppNavigator.() -> Unit) {
        compose.runOnIdle { navigator.block() }
        compose.waitForIdle()
    }

    private object FakeDays : DayLessonsGateway {
        override fun observe(from: LocalDate, to: LocalDate): Flow<List<LessonDay>> =
            flowOf(listOf(LessonDay(DATE, listOf(LESSON_ROW, BOOKING_ROW), null)))

        override suspend fun refresh(from: LocalDate, to: LocalDate): AppResult<Unit> = AppResult.Success(Unit)
    }

    private object FakeBell : BellRepository {
        private val seen = MutableStateFlow(emptySet<String>())
        override fun observeItems(): Flow<List<BellItem>> = flowOf(listOf(EVENT))
        override fun observeSeen(): Flow<Set<String>> = seen
        override fun observeUnseenCount(): Flow<Int> =
            combine(observeItems(), seen) { items, ids -> items.count { it.id !in ids } }
        override suspend fun markSeen(ids: Set<String>) {
            seen.value += ids
        }
        override suspend fun readAll() = Unit
        override suspend fun refresh(): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun answer(isu: Int, accept: Boolean): AppResult<Unit> = AppResult.Success(Unit)
    }

    private object FakeQrRepository : QrCodeRepository {
        private val code = QrCodeSnapshot("0123456789ABCDEF", savedAtMillis = 0)
        override suspend fun currentQr() = code
        override suspend fun lastQr() = code
        override fun observeQrHex() = flowOf(code.hex)
        override suspend fun currentQrHex(allowExpired: Boolean) = code.hex
        override suspend fun refreshQrHex(force: Boolean): AppResult<Unit> = AppResult.Success(Unit)
        override fun clearCache() = Unit
    }

    private val signedIn = FakeSessionRepository(SessionState.SignedIn(user = null))

    private object PlainQrAppearance : QrAppearancePreferences {
        override suspend fun useDynamicColors() = false
        override suspend fun isSpoilerEnabled() = false
        override suspend fun spoilerAnimationType() = QrAnimationType.entries.first()
    }

    private companion object {
        const val CLOSE = "Закрыть"
        const val ACTION = "Открыть друзей"
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

        val LESSON_ROW = DayLesson(
            start = LocalTime(9, 30), end = LocalTime(11, 0), subject = LESSON.subjectName, typeId = 1,
            teacher = LESSON.teacherFio, room = LESSON.room, building = LESSON.building, online = false,
            target = LessonTarget.Lesson(LESSON),
        )
        val BOOKING_ROW = DayLesson(
            start = LocalTime(16, 0), end = LocalTime(17, 30), subject = BOOKING.sectionName, typeId = 11,
            teacher = BOOKING.teacherFio, room = BOOKING.roomName, building = null, online = false,
            target = LessonTarget.PendingSport(PENDING), sport = SportMark.QUEUED,
        )

        val TODO = TodayTodo(
            id = "test", icon = AppIcon.LOGIN, title = UiText.Dynamic("Дело"), reason = UiText.Dynamic("Причина"),
            action = UiText.Dynamic(ACTION), target = EntryRoute(AppTab.HOME, overlay = AppRoutes.Friends),
        )

        val EVENT = BellItem(
            id = "event", kind = BellKind.SPORT, at = at(8, 0), title = UiText.Dynamic("Волейбол: вы записаны"),
            target = EntryRoute(AppTab.HOME, overlay = AppRoutes.Friends),
        )

        fun at(hour: Int, minute: Int): Instant = LocalDateTime(DATE, LocalTime(hour, minute)).toInstant(ZONE)
    }
}
