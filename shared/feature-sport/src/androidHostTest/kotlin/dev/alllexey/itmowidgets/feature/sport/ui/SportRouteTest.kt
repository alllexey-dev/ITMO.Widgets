package dev.alllexey.itmowidgets.feature.sport.ui

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.sport.cards.SportCardFixtures
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAttempts
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportActionRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportBookingRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportDataRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportScheduleRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportSignPreferences
import dev.alllexey.itmowidgets.feature.sport.presentation.bookingDelegate
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingsHolder
import dev.alllexey.itmowidgets.feature.sport.presentation.emptyCatalog
import dev.alllexey.itmowidgets.feature.sport.presentation.my.SportMyViewModel
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportAutoSignFlow
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSharedLessonResolver
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignFilterController
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignStateFactory
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignUiState
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignViewModel
import dev.alllexey.itmowidgets.feature.sport.ui.my.SportBookingSamples
import dev.alllexey.itmowidgets.feature.sport.ui.sign.SportSheetAction
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `SportRoute` on real ViewModels over fake repositories, obtained through Koin from a `ViewModelStoreOwner` the test
 * keeps, as a Fragment's store or a Nav3 entry's: shared links, page lifecycles, the details sheet's results and a
 * bottom-tab round trip.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class SportRouteTest {

    private val main = TestMainDispatcher(UnconfinedTestDispatcher())

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            main.install()
            startKoin { modules(viewModels) }
        }

        override fun after() {
            stopKoin()
            scope.cancel()
            main.reset()
        }
    }).around(compose)

    private val time = SportBookingSamples.time
    private val scope = CoroutineScope(SupervisorJob() + main.dispatcher)
    private val bookings = FakeSportBookingRepository()
    private val data = FakeSportDataRepository()
    private val schedule = FakeSportScheduleRepository()

    private val myCreated = mutableListOf<SportMyViewModel>()
    private val signCreated = mutableListOf<SportSignViewModel>()
    private val viewModels = module {
        viewModel {
            val delegate = bookingDelegate(bookings, schedule, data, scope, FakeSportActionRepository())
            val holder = SportBookingsHolder(bookings, data, delegate, time, scope)
            SportMyViewModel(bookings, data, holder).also(myCreated::add)
        }
        viewModel {
            val delegate = bookingDelegate(bookings, schedule, data, scope, FakeSportActionRepository())
            SportSignViewModel(
                schedule, data, SportSignFilterController(time), SportSignStateFactory(time), delegate,
                SportAutoSignFlow(delegate, time), SportSharedLessonResolver(schedule, time), FakeSportSignPreferences,
                time,
            ).also(signCreated::add)
        }
    }

    /** The store a Fragment or a Nav3 entry keeps on the saved back stack while its tab is away. */
    private val store = object : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }

    private val pager = PagerState { SportPage.entries.size }
    private val sharedLessons = Channel<SportSharedLesson>(Channel.UNLIMITED)
    private val mySheet = Channel<SportSheetAction>(Channel.UNLIMITED)
    private val signSheet = Channel<SportSheetAction>(Channel.UNLIMITED)
    private val opened = mutableListOf<Long>()
    private var shown by mutableStateOf(true)

    private val host = SportHostActions(
        onOpenBooking = { opened += it.lessonId },
        onOpenLesson = { lesson, _ -> opened += lesson.lessonId },
    )

    private fun showRoute(vararg catalog: SportLesson) {
        runBlocking {
            data.attempts.emit(LoadState.Content(SportAttempts(total = 3, used = 1, free = 2, canSignIn = true)))
            data.score.emit(LoadState.Content(SportBookingSamples.score))
            bookings.merged.emit(LoadState.Content(listOf(SportBookingSamples.signed)))
            schedule.filters.emit(AppResult.Success(emptyCatalog()))
            schedule.timeSlots.emit(AppResult.Success(emptyList()))
            schedule.schedule.emit(LoadState.Content(catalog.toList()))
        }
        val shared = sharedLessons.receiveAsFlow()
        val my = mySheet.receiveAsFlow()
        val sign = signSheet.receiveAsFlow()
        compose.setContent {
            ItmoTheme {
                CompositionLocalProvider(LocalViewModelStoreOwner provides store) {
                    if (shown) {
                        SportRoute(
                            time = time,
                            host = host,
                            pagerState = pager,
                            sharedLessons = shared,
                            mySheetActions = my,
                            signSheetActions = sign,
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun aSharedLinkSelectsSignAndOpensTheLesson() {
        showRoute(lessonOn(5, day = 10))

        sharedLessons.trySend(SportSharedLesson(5))
        compose.waitForIdle()

        assertEquals(SportPage.SIGN.ordinal, pager.currentPage)
        assertEquals(listOf(5L), opened)
    }

    @Test
    fun signKeepsItsEventsWhileMySportIsInFront() {
        showRoute(lessonOn(5, day = 10))
        val sign = signViewModel()

        sign.openSharedLesson(5)
        compose.waitForIdle()
        assertTrue("opened behind `Мой спорт`: $opened", opened.isEmpty())

        compose.onNodeWithText("Запись").performClick()
        compose.waitForIdle()

        assertEquals(listOf(5L), opened)
    }

    @Test
    fun theSheetResultReachesThePageThatOpenedIt() {
        showRoute(lessonOn(5, day = 10))
        compose.onNodeWithText("Отменить запись на это занятие?").assertDoesNotExist()

        mySheet.trySend(SportSheetAction(SportBookingSamples.signed.lessonId, "CANCEL"))
        compose.waitForIdle()

        compose.onNodeWithText("Отменить запись на это занятие?").assertExists()
    }

    @Test
    fun aStaleSheetResultAsksNothing() {
        showRoute(lessonOn(5, day = 10))

        mySheet.trySend(SportSheetAction(SportBookingSamples.signed.lessonId, "SIGN"))
        compose.waitForIdle()

        compose.onNodeWithText("Отменить запись на это занятие?").assertDoesNotExist()
    }

    @Test
    fun theWeekAndTheFiltersSurviveABottomTabRoundTrip() {
        showRoute(lessonOn(5, day = 10))
        compose.onNodeWithText("Запись").performClick()
        val sign = signViewModel()
        sign.nextWeek()
        sign.showOnlyAvailable(false)
        compose.waitForIdle()
        val before = sign.uiState.value as SportSignUiState.Content
        val refreshes = bookings.refreshCount

        // Another tab in front: the route leaves the composition, the host's store stays.
        shown = false
        compose.waitForIdle()
        shown = true
        compose.waitForIdle()

        assertEquals(1, myCreated.size)
        assertEquals(listOf(sign), signCreated)
        val after = signViewModel().uiState.value as SportSignUiState.Content
        assertEquals(1, after.selectedWeekIndex)
        assertEquals(before.displayedWeek, after.displayedWeek)
        assertFalse(after.showOnlyAvailable)
        // `Мой спорт` came back with content: no request.
        assertEquals(refreshes, bookings.refreshCount)
    }

    private fun signViewModel(): SportSignViewModel = signCreated.single()

    private fun lessonOn(id: Long, day: Int): SportLesson = SportCardFixtures.lesson(id).let { lesson ->
        val start = Instant.parse("2026-09-${day.toString().padStart(2, '0')}T18:30:00+03:00")
        lesson.copy(start = start, end = start + 90.minutes)
    }
}
