package dev.alllexey.itmowidgets.feature.sport.ui.sign

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.sport.cards.SportCardFixtures
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportActionRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportBookingRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportDataRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportScheduleRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportSignPreferences
import dev.alllexey.itmowidgets.feature.sport.presentation.bookingDelegate
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingAction
import dev.alllexey.itmowidgets.feature.sport.presentation.emptyCatalog
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportAutoSignFlow
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSharedLessonResolver
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignFilterController
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignStateFactory
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignUiState
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignViewModel
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import dev.alllexey.itmowidgets.testkit.awaitText
import kotlin.time.Duration.Companion.days
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
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The `Запись` screen and its route on a real `SportSignViewModel` over fake repositories: shared links, the details
 * sheet's actions with their stale-offer guard, debug template lessons, and the states the screen keeps its header in.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class SportSignScreenTest {

    private val main = TestMainDispatcher(UnconfinedTestDispatcher())

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() = main.install()
        override fun after() {
            followUps.cancel()
            main.reset()
        }
    }).around(compose)

    private val schedule = FakeSportScheduleRepository()
    private val actions = FakeSportActionRepository()
    private val time = FixedAcademicTime(LocalDateTime(2026, 9, 8, 12, 0))
    private val followUps = CoroutineScope(SupervisorJob() + main.dispatcher)

    private val opened = mutableListOf<Pair<SportLesson, Boolean>>()
    private var templateMessages = 0
    private val sheet = Channel<SportSheetAction>(Channel.UNLIMITED)

    private fun viewModel(): SportSignViewModel {
        val delegate = bookingDelegate(FakeSportBookingRepository(), schedule, FakeSportDataRepository(), followUps, actions)
        return SportSignViewModel(
            schedule, FakeSportDataRepository(), SportSignFilterController(time), SportSignStateFactory(time), delegate,
            SportAutoSignFlow(delegate, time), SportSharedLessonResolver(schedule, time), FakeSportSignPreferences, time
        )
    }

    private fun showRoute(vararg catalog: SportLesson): SportSignViewModel {
        runBlocking {
            schedule.filters.emit(AppResult.Success(emptyCatalog()))
            schedule.timeSlots.emit(AppResult.Success(emptyList()))
            schedule.schedule.emit(LoadState.Content(catalog.toList()))
        }
        val viewModel = viewModel()
        compose.setContent {
            ItmoTheme {
                SportSignRoute(
                    time = time,
                    onOpenLesson = { lesson, busy -> opened += lesson to busy },
                    onTemplateLesson = { templateMessages++ },
                    viewModel = viewModel,
                    sheetActions = sheet.receiveAsFlow(),
                )
            }
        }
        compose.waitForIdle()
        return viewModel
    }

    @Test
    fun aRealSharedLinkOpensItsLessonEvenWhenTheFiltersHideIt() {
        val hidden = lessonOn(5, day = 10).copy(available = 0, canSignIn = false)
        val viewModel = showRoute(SportCardFixtures.lesson(1), hidden)

        viewModel.openSharedLesson(5)
        compose.waitForIdle()

        assertEquals(listOf(hidden to false), opened)
    }

    @Test
    fun aPredictedSharedLinkOpensThePrediction() {
        val prototype = lessonOn(7, day = 9)
        val prediction = prototype.copy(isLessonReal = false, start = prototype.start + 14.days, end = prototype.end + 14.days)
        val viewModel = showRoute(prototype, prediction)

        viewModel.openSharedLesson(7, predicted = true)
        compose.waitForIdle()

        assertEquals(listOf(prediction to false), opened)
    }

    @Test
    fun anEndedSharedLessonSaysItIsUnavailableAndOpensNothing() {
        val viewModel = showRoute(lessonOn(3, day = 8, hour = 9))

        viewModel.openSharedLesson(3)
        compose.waitForIdle()

        compose.onNodeWithText(LINK_UNAVAILABLE).assertExists()
        assertTrue(opened.isEmpty())
    }

    @Test
    fun aSheetActionTheLessonNoLongerOffersSaysUnavailableAndSendsNothing() {
        showRoute(SportCardFixtures.lesson(1))

        sheet.trySend(SportSheetAction(1, SportBookingAction.CANCEL.name))
        // The snackbar merges its text into its live region.
        compose.awaitText(UNAVAILABLE, useUnmergedTree = true).assertExists()
        assertTrue(actions.signedInLessons.isEmpty())

        sheet.trySend(SportSheetAction(1, SportBookingAction.SIGN.name))
        compose.waitForIdle()
        assertEquals(listOf(1L), actions.signedInLessons)
    }

    @Test
    fun aTemplateLessonShowsTheMessageAndNeverReachesTheViewModel() {
        showRoute(SportCardFixtures.lesson(-3))

        compose.onNodeWithTag(SportLessonCardTestTags.ACTION).performClick()
        sheet.trySend(SportSheetAction(-3, SportBookingAction.SIGN.name))
        compose.waitForIdle()

        assertEquals(2, templateMessages)
        assertTrue(actions.signedInLessons.isEmpty())
    }

    @Test
    fun theSheetOutcomeChecksTheLessonItsBusyStateAndTheCurrentOffer() {
        val shown = SportCardFixtures.lesson(1)
        val linked = SportCardFixtures.lesson(2)
        val state = SportSignUiState.Content(displayedLessons = listOf(shown))
        val now = time.now()
        val noLink: (Long) -> SportLesson? = { null }

        assertEquals(SportSheetOutcome.Run(shown, SportBookingAction.SIGN), SportSheetAction(1, "SIGN").outcome(state, noLink, now))
        assertEquals(SportSheetOutcome.Stale, SportSheetAction(1, "CANCEL").outcome(state, noLink, now))
        assertEquals(SportSheetOutcome.Stale, SportSheetAction(1, "SIGN").outcome(state, noLink, shown.end))
        assertEquals(SportSheetOutcome.Ignored, SportSheetAction(1, "SIGN").outcome(state.copy(busyLessonIds = setOf(1)), noLink, now))
        assertEquals(SportSheetOutcome.Ignored, SportSheetAction(2, "SIGN").outcome(state, noLink, now))
        assertEquals(
            SportSheetOutcome.Run(linked, SportBookingAction.SIGN),
            SportSheetAction(2, "SIGN").outcome(state, { id -> linked.takeIf { id == 2L } }, now),
        )
        assertEquals(SportSheetOutcome.Ignored, SportSheetAction(1, "SIGN").outcome(SportSignUiState.Loading, noLink, now))
    }

    @Test
    fun theHeaderStaysBehindAnErrorAndTheFirstLoadHasNone() {
        var state: SportSignUiState by mutableStateOf(SportSignUiState.Loading)
        compose.setContent { ItmoTheme { SportSignScreen(state, time, SportSignActions()) } }

        compose.onAllNodesWithTag(SportSignFiltersTestTags.SPORT).assertCountEquals(0)
        compose.onNodeWithTag(SportLessonListTestTags.SKELETON).assertExists()
        compose.assertTouchTargets()

        state = SportSignFiltersSamples.hiddenSelectors().copy(initialLoading = true)
        compose.waitForIdle()
        compose.onNodeWithTag(SportSignFiltersTestTags.SPORT).assertExists()
        compose.onNodeWithTag(SportWeekStripTestTags.STRIP).assertExists()
        compose.onNodeWithTag(SportLessonListTestTags.SKELETON).assertExists()
        compose.assertTouchTargets()

        state = SportSignUiState.Error(AppError.Network)
        compose.waitForIdle()
        compose.onNodeWithTag(SportSignFiltersTestTags.SPORT).assertExists()
        compose.onNodeWithTag(SportLessonListTestTags.ERROR).assertExists()
        compose.assertTouchTargets()
    }

    @Test
    fun contentAndAFilteredEmptyDayKeepTheFiltersAndTheirTouchTargets() {
        var state: SportSignUiState by mutableStateOf(
            SportSignFiltersSamples.hiddenSelectors().copy(displayedLessons = listOf(SportLessonSamples.open)),
        )
        val retries = mutableListOf<Unit>()
        compose.setContent {
            ItmoTheme { SportSignScreen(state, SportLessonSamples.time, SportSignActions(onRetry = { retries += Unit })) }
        }

        compose.onAllNodesWithTag(SportLessonCardTestTags.CARD).assertCountEquals(1)
        compose.assertTouchTargets()

        state = SportSignFiltersSamples.hiddenSelectors().copy(showOnlyFriends = true, hasActiveFilters = true)
        compose.waitForIdle()
        compose.onNodeWithTag(SportLessonListTestTags.EMPTY).assertExists()
        compose.onNodeWithTag(SportSignFiltersTestTags.RESET).assertExists()
        compose.assertTouchTargets()
    }

    private fun lessonOn(id: Long, day: Int, hour: Int = 18): SportLesson = SportCardFixtures.lesson(id).let { lesson ->
        val start = Instant.parse("2026-09-${day.twoDigits()}T${hour.twoDigits()}:30:00+03:00")
        lesson.copy(start = start, end = start + 90.minutes)
    }

    private fun Int.twoDigits(): String = toString().padStart(2, '0')

    private companion object {
        const val LINK_UNAVAILABLE = "Занятие недоступно"
        const val UNAVAILABLE = "Недоступно"
    }
}
