package dev.alllexey.itmowidgets.app.shell.entries

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.alllexey.itmowidgets.app.shell.EntryRegistry
import dev.alllexey.itmowidgets.app.shell.Nav3AppNavigator
import dev.alllexey.itmowidgets.app.shell.ShellContent
import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.EntryRoute
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.PendingSportDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.ShellBackStack
import dev.alllexey.itmowidgets.core.navigation.ShellSurface
import dev.alllexey.itmowidgets.core.navigation.TabRequest
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.di.bridge.StopKoinRule
import dev.alllexey.itmowidgets.feature.debug.ui.PreviewHostApplication
import dev.alllexey.itmowidgets.feature.sport.cards.SportCardFixtures
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.navigation.SportDetailsOpener
import dev.alllexey.itmowidgets.feature.sport.navigation.SportRoutes
import dev.alllexey.itmowidgets.feature.sport.ui.SportPage
import dev.alllexey.itmowidgets.feature.sport.ui.SportScreenTestTags
import dev.alllexey.itmowidgets.feature.sport.ui.details.SportDetailsSheetTestTags
import dev.alllexey.itmowidgets.feature.sport.ui.user.UserSportScreenTestTags
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.compose.KoinContext
import org.koin.core.context.startKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The sport keys in the Compose shell with the real [shellEntries] and Koin ViewModels on synthetic data: the tab
 * root and its shared-lesson request, the details sheet replying to its opener, the shell's cancel question, another
 * user's sport; and [SportDetailsRedirect] on virtual time: which sheet a feed or schedule row opens, the holder's
 * eight-second wait, and the guard against a stale action.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = PreviewHostApplication::class)
class SportEntriesTest {

    @get:Rule(order = 0)
    val stopKoin = StopKoinRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val sport = SportTestGraph(scope)
    private val navigator = Nav3AppNavigator(ShellBackStack(AppTab.SPORT))

    @Before
    fun startGraph() {
        val koin = startKoin { modules(sport.module(), socialTestModule()) }.koin
        compose.setContent {
            // Koin Compose caches the first graph it reads for the JVM; this test's graph replaces the stopped one.
            KoinContext(koin) {
                ItmoTheme { ShellContent(navigator, shellEntries(), ShellSurface.Tabs(demoBanner = false), onDemoSignIn = {}) }
            }
        }
        compose.waitForIdle()
    }

    @After
    fun stopScope() = scope.cancel()

    @Test
    fun sportTabRootRendersTheSportScreen() {
        compose.onNodeWithTag(SportScreenTestTags.PAGER).assertIsDisplayed()
        compose.onNodeWithTag(SportScreenTestTags.tab(SportPage.MY)).assertIsSelected()
        compose.onNodeWithTag(EntryRegistry.placeholderTag(AppRoutes.TabRoot(AppTab.SPORT))).assertDoesNotExist()
    }

    @Test
    fun aSharedLessonRequestOpensTheSignPageOnce() {
        act { apply(EntryRoute(AppTab.SPORT, request = TabRequest.SportLesson(LESSON_ID))) }

        compose.onNodeWithTag(SportScreenTestTags.tab(SportPage.SIGN)).assertIsSelected()
        assertNull(navigator.pendingRequest(AppTab.SPORT))
    }

    @Test
    fun aMySportSheetActionAsksOnMySport() {
        sport.bookings.value = LoadState.Content(listOf(UPCOMING))
        compose.waitForIdle()
        act { open(SportRoutes.details(UPCOMING, SportDetailsOpener.MY)) }

        compose.onNodeWithTag(SportDetailsSheetTestTags.ACTION).performClick()
        compose.waitForIdle()

        assertEquals(emptyList<AppRoute>(), navigator.state.floating)
        compose.onNodeWithText(CANCEL_QUESTION).assertIsDisplayed()
    }

    @Test
    fun aSignSheetActionDoesNotReachMySport() {
        sport.bookings.value = LoadState.Content(listOf(UPCOMING))
        compose.waitForIdle()
        act { open(SportRoutes.details(UPCOMING, SportDetailsOpener.SIGN)) }

        compose.onNodeWithTag(SportDetailsSheetTestTags.ACTION).performClick()
        compose.waitForIdle()

        assertEquals(emptyList<AppRoute>(), navigator.state.floating)
        compose.onNodeWithText(CANCEL_QUESTION).assertDoesNotExist()
    }

    @Test
    fun aShellSheetActionAsksAndTheAnswerCancels() {
        sport.bookings.value = LoadState.Content(listOf(UPCOMING))
        act { open(SportRoutes.details(UPCOMING, SportDetailsOpener.SHELL)) }

        compose.onNodeWithTag(SportDetailsSheetTestTags.ACTION).performClick()
        compose.waitForIdle()
        assertEquals(listOf<AppRoute>(AppRoutes.CancelBookingConfirm(LESSON_ID)), navigator.state.floating)

        compose.onNodeWithText(CANCEL_ACTION).performClick()
        compose.waitForIdle()
        assertEquals(emptyList<AppRoute>(), navigator.state.floating)
        assertEquals(listOf(LESSON_ID), sport.cancelled)
    }

    @Test
    fun theCancelQuestionBackCancelsNothing() {
        sport.bookings.value = LoadState.Content(listOf(UPCOMING))
        act { open(AppRoutes.CancelBookingConfirm(LESSON_ID)) }

        compose.onNodeWithText(BACK).performClick()
        compose.waitForIdle()

        assertEquals(emptyList<AppRoute>(), navigator.state.floating)
        assertEquals(emptyList<Long>(), sport.cancelled)
    }

    @Test
    fun aStaleShellSheetActionAsksNothing() {
        sport.bookings.value = LoadState.Content(listOf(UPCOMING))
        act { open(SportRoutes.details(UPCOMING, SportDetailsOpener.SHELL)) }
        // The booking was cancelled elsewhere while the sheet still offers it.
        sport.bookings.value = LoadState.Content(listOf(UPCOMING.copy(signed = false)))

        compose.onNodeWithTag(SportDetailsSheetTestTags.ACTION).performClick()
        compose.waitForIdle()

        assertEquals(emptyList<AppRoute>(), navigator.state.floating)
        assertEquals(emptyList<Long>(), sport.cancelled)
    }

    @Test
    fun anotherUsersSportRendersAndBackClosesIt() {
        act { open(AppRoutes.UserSport(SocialSamples.FRIEND_ISU, SocialSamples.FRIEND_NAME)) }

        compose.onNodeWithTag(UserSportScreenTestTags.STATE).assertIsDisplayed()
        compose.onNodeWithContentDescription(BACK).performClick()
        compose.waitForIdle()
        assertEquals(emptyList<AppRoute>(), navigator.state.overlays)
    }

    @Test
    fun aSportLessonOpensTheSportSheetOfTheBestBookingInItsSlot() = redirect { graph, redirect, home ->
        graph.bookings.value = LoadState.Content(
            listOf(
                booking(1, signed = false, section = SECTION),
                booking(2, signed = true, section = "Другая секция"),
                booking(3, signed = true, section = SECTION),
                booking(4, signed = false, section = "Другая секция"),
            ),
        )

        redirect.openLesson(home, SPORT_LESSON)
        runCurrent()

        val sheet = home.state.floating.single() as SportRoutes.SportCommonDetails
        assertEquals(3L, sheet.item.lessonId)
        assertEquals(SportDetailsOpener.SHELL, sheet.replyTo)
    }

    @Test
    fun aSportLessonWithoutItsBookingOpensTheScheduleSheet() = redirect { graph, redirect, home ->
        graph.bookings.value = LoadState.Content(listOf(booking(1, signed = true, section = SECTION, hourLater = true)))

        redirect.openLesson(home, SPORT_LESSON)
        runCurrent()

        assertEquals(listOf<AppRoute>(AppRoutes.LessonDetails(SPORT_LESSON)), home.state.floating)
    }

    @Test
    fun anyOtherLessonOpensItsSheetAtOnce() = redirect { graph, redirect, home ->
        graph.bookings.value = LoadState.Loading
        val lecture = SPORT_LESSON.copy(typeId = 1)

        redirect.openLesson(home, lecture)

        assertEquals(listOf<AppRoute>(AppRoutes.LessonDetails(lecture)), home.state.floating)
    }

    @Test
    fun aPendingSportRowOpensTheSportSheetWhenTheBookingIsKnown() = redirect { graph, redirect, home ->
        graph.bookings.value = LoadState.Content(listOf(booking(LESSON_ID, signed = false, section = SECTION)))

        redirect.openPendingSport(home, PENDING)
        runCurrent()

        assertEquals(LESSON_ID, (home.state.floating.single() as SportRoutes.SportCommonDetails).item.lessonId)
    }

    @Test
    fun aPendingSportRowWithoutItsBookingOpensTheScheduleSheet() = redirect { _, redirect, home ->
        redirect.openPendingSport(home, PENDING)
        runCurrent()

        assertEquals(listOf<AppRoute>(AppRoutes.PendingSportDetails(PENDING)), home.state.floating)
    }

    @Test
    fun theLookupWaitsEightSecondsThenOpensTheScheduleSheet() = redirect { graph, redirect, home ->
        graph.bookings.value = LoadState.Loading

        redirect.openLesson(home, SPORT_LESSON)
        advanceTimeBy(8.seconds - 1.milliseconds)
        runCurrent()
        assertEquals(emptyList<AppRoute>(), home.state.floating)

        advanceTimeBy(2.milliseconds)
        runCurrent()
        assertEquals(listOf<AppRoute>(AppRoutes.LessonDetails(SPORT_LESSON)), home.state.floating)
    }

    @Test
    fun aBookingArrivingWithinTheWaitStillOpensTheSportSheet() = redirect { graph, redirect, home ->
        graph.bookings.value = LoadState.Loading

        redirect.openPendingSport(home, PENDING)
        advanceTimeBy(5.seconds)
        graph.bookings.value = LoadState.Content(listOf(booking(LESSON_ID, signed = true, section = SECTION)))
        runCurrent()

        assertEquals(LESSON_ID, (home.state.floating.single() as SportRoutes.SportCommonDetails).item.lessonId)
    }

    @Test
    fun anActionTheBookingNoLongerOffersAsksNothing() = redirect { graph, redirect, home ->
        // A queued booking offers `CANCEL_AUTO`, a signed one `CANCEL`.
        graph.bookings.value = LoadState.Content(listOf(booking(LESSON_ID, signed = false, section = SECTION)))
        redirect.onAction(home, LESSON_ID, "CANCEL")
        runCurrent()
        assertEquals(emptyList<AppRoute>(), home.state.floating)

        graph.bookings.value = LoadState.Content(listOf(booking(LESSON_ID, signed = true, section = SECTION)))
        redirect.onAction(home, LESSON_ID, "CANCEL_AUTO")
        runCurrent()
        assertEquals(emptyList<AppRoute>(), home.state.floating)

        redirect.onAction(home, LESSON_ID, "CANCEL")
        runCurrent()
        assertEquals(listOf<AppRoute>(AppRoutes.CancelBookingConfirm(LESSON_ID)), home.state.floating)
    }

    @Test
    fun theAnswerCancelsOnlyWhileTheBookingStillOffersIt() = redirect { graph, redirect, _ ->
        val gone = booking(LESSON_ID, signed = false, section = SECTION).copy(signEntry = null)
        graph.bookings.value = LoadState.Content(listOf(gone))
        redirect.confirmCancel(LESSON_ID)
        runCurrent()
        assertEquals(emptyList<Long>(), graph.cancelled)

        graph.bookings.value = LoadState.Content(listOf(booking(LESSON_ID, signed = true, section = SECTION)))
        redirect.confirmCancel(LESSON_ID)
        runCurrent()
        assertEquals(listOf(LESSON_ID), graph.cancelled)
    }

    private fun act(block: Nav3AppNavigator.() -> Unit) {
        compose.runOnIdle { navigator.block() }
        compose.waitForIdle()
    }

    /** Runs [body] on virtual time with a graph of its own, a redirect in the test's scope and a navigator on home. */
    private fun redirect(
        body: suspend TestScope.(SportTestGraph, SportDetailsRedirect, home: Nav3AppNavigator) -> Unit,
    ) = runTest {
        val graph = SportTestGraph(backgroundScope)
        body(graph, SportDetailsRedirect(graph.holder, graph.time, this), Nav3AppNavigator())
    }

    private companion object {
        const val LESSON_ID = 7L
        const val SECTION = "Плавание"
        const val CANCEL_QUESTION = "Отменить запись на это занятие?"
        const val CANCEL_ACTION = "Отменить запись"
        const val BACK = "Назад"

        /** A signed booking tomorrow, so it offers `Отменить` at the graph's fixed time. */
        val UPCOMING: SportBooking = SportCardFixtures.booking(LESSON_ID).let {
            val start = it.start + 30.days
            it.copy(start = start, end = start + 90.minutes)
        }

        /** The fixture slot: 8 September 2026, 18:30 in Moscow. */
        val SPORT_LESSON = LessonDetailsArgs(
            pairId = 1, date = "2026-09-08", subjectName = SECTION, typeId = 11, format = "Очный",
            start = "18:30", end = "20:00", teacherFio = null, teacherIsu = null, room = null, building = null,
            buildingId = null, mainBuildingId = null, note = null, zoomUrl = null, zoomPassword = null, zoomInfo = null,
        )

        val PENDING = PendingSportDetailsArgs(
            lessonId = LESSON_ID, sectionName = SECTION, autoSign = false, isPrediction = false,
            start = "2026-09-08T18:30+03:00", end = "2026-09-08T20:00+03:00", teacherFio = "Тренер", roomName = "Зал",
        )

        fun booking(id: Long, signed: Boolean, section: String, hourLater: Boolean = false): SportBooking {
            val fixture = SportCardFixtures.booking(id)
            val start = if (hourLater) fixture.start + 60.minutes else fixture.start
            return fixture.copy(
                start = start,
                end = start + 90.minutes,
                signed = signed,
                sectionName = SectionName(section),
                signEntry = if (signed) null else SportCardFixtures.entry(),
            )
        }
    }
}
