package dev.alllexey.itmowidgets.feature.sport.presentation.sign

import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.sport.cards.SportCardFixtures
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAutoSignLimits
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.domain.model.UnavailableReason
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportActionRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportBookingRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportDataRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportScheduleRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportSignPreferences
import dev.alllexey.itmowidgets.feature.sport.presentation.bookingDelegate
import dev.alllexey.itmowidgets.feature.sport.presentation.emptyCatalog
import dev.alllexey.itmowidgets.shared.feature.sport.Res
import dev.alllexey.itmowidgets.shared.feature.sport.sport_auto_sign_limit_reached
import dev.alllexey.itmowidgets.shared.feature.sport.sport_sign_success
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class SportSignViewModelTest {

    private val main = TestMainDispatcher()

    @BeforeTest
    fun setUpMain() = main.install()

    @AfterTest
    fun tearDownMain() = main.reset()

    private val schedule = FakeSportScheduleRepository()
    private val data = FakeSportDataRepository()
    private val actions = FakeSportActionRepository()
    private val time = FixedAcademicTime(LocalDateTime(2026, 9, 8, 12, 0))

    private fun TestScope.viewModel(): SportSignViewModel {
        val delegate = bookingDelegate(FakeSportBookingRepository(), schedule, data, this, actions)
        return SportSignViewModel(
            schedule, data, SportSignFilterController(time), SportSignStateFactory(time), delegate,
            SportAutoSignFlow(delegate, time), SportSharedLessonResolver(schedule, time), FakeSportSignPreferences, time
        )
    }

    private fun SportSignViewModel.selectedDate(): LocalDate =
        (uiState.value as SportSignUiState.Content).displayedWeek.single { it.isSelected }.date

    private suspend fun emitSnapshot() {
        schedule.filters.emit(AppResult.Success(emptyCatalog()))
        schedule.timeSlots.emit(AppResult.Success(emptyList()))
        schedule.schedule.emit(LoadState.Content(listOf(SportCardFixtures.lesson(1))))
    }

    @Test
    fun aRefreshKeepsTheCatalogueOnScreenAndOnlyMarksItRefreshing() = runTest(main.dispatcher) {
        emitSnapshot()
        val viewModel = viewModel()
        advanceUntilIdle()
        val before = viewModel.uiState.value as SportSignUiState.Content
        assertFalse(before.refreshing)

        schedule.gate = CompletableDeferred()
        viewModel.refresh(RefreshMode.Pull)
        runCurrent()
        val during = viewModel.uiState.value as SportSignUiState.Content
        assertTrue(during.refreshing)
        assertEquals(before.displayedWeek, during.displayedWeek)

        schedule.gate.complete(Unit)
        advanceUntilIdle()
        assertFalse((viewModel.uiState.value as SportSignUiState.Content).refreshing)
    }

    @Test
    fun beforeTheCatalogueAnswersTheCalendarShowsOverAPlaceholderWithoutAnIndicator() = runTest(main.dispatcher) {
        schedule.gate = CompletableDeferred()
        val viewModel = viewModel()
        runCurrent()
        val initial = viewModel.uiState.value as SportSignUiState.Content
        assertTrue(initial.initialLoading)
        assertFalse(initial.refreshing)
        assertEquals(7, initial.displayedWeek.size)
        assertTrue(initial.displayedLessons.isEmpty())
        schedule.gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(SportSignUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun theEntryRefreshIsSilentAndAPullShowsTheIndicator() = runTest(main.dispatcher) {
        emitSnapshot()
        val viewModel = viewModel()
        runCurrent()
        assertFalse((viewModel.uiState.value as SportSignUiState.Content).refreshing)
        advanceUntilIdle()
        schedule.gate = CompletableDeferred()
        viewModel.refresh(RefreshMode.Pull)
        runCurrent()
        assertTrue((viewModel.uiState.value as SportSignUiState.Content).refreshing)
        schedule.gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun aFailedSourceAfterContentKeepsTheContentWithAPartialError() = runTest(main.dispatcher) {
        emitSnapshot()
        val viewModel = viewModel()
        advanceUntilIdle()

        schedule.schedule.emit(LoadState.Error(AppError.Network))
        advanceUntilIdle()

        val after = viewModel.uiState.value as SportSignUiState.Content
        assertTrue(after.hasPartialError)
        assertFalse(after.refreshing)
    }

    @Test
    fun aFailedSourceBeforeAnyContentIsAnError() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        schedule.filters.emit(AppResult.Failure(AppError.Network))
        schedule.timeSlots.emit(AppResult.Failure(AppError.Network))
        schedule.schedule.emit(LoadState.Error(AppError.Network))
        advanceUntilIdle()
        assertEquals(SportSignUiState.Error(AppError.Network), viewModel.uiState.value)
    }

    @Test
    fun aForcedRetryReplacesTheSilentEntryRefreshAndShowsTheIndicator() = runTest(main.dispatcher) {
        emitSnapshot()
        schedule.gate = CompletableDeferred()
        val viewModel = viewModel()
        runCurrent()
        assertEquals(1, schedule.scheduleRefreshCount)
        assertFalse((viewModel.uiState.value as SportSignUiState.Content).refreshing)

        viewModel.refresh(RefreshMode.Pull)
        runCurrent()
        assertEquals(1, schedule.scheduleRefreshCount, "a pull joins the refresh in flight")
        assertTrue((viewModel.uiState.value as SportSignUiState.Content).refreshing)

        viewModel.refresh(RefreshMode.Force)
        runCurrent()
        assertEquals(2, schedule.scheduleRefreshCount, "a retry starts a fresh request")
        assertTrue((viewModel.uiState.value as SportSignUiState.Content).refreshing)

        schedule.gate.complete(Unit)
        advanceUntilIdle()
        assertFalse((viewModel.uiState.value as SportSignUiState.Content).refreshing)
    }

    @Test
    fun aSecondTapOnALessonInFlightSendsNothingAndTheLessonIsBusyUntilTheAnswer() =
        runTest(main.dispatcher) {
            emitSnapshot()
            val viewModel = viewModel()
            advanceUntilIdle()
            actions.gate = CompletableDeferred()

            viewModel.signUpForLesson(SportCardFixtures.lesson(1))
            viewModel.signUpForLesson(SportCardFixtures.lesson(1))
            viewModel.signUpForLesson(SportCardFixtures.lesson(2))
            runCurrent()
            assertEquals(listOf(1L, 2L), actions.signedInLessons)
            assertEquals(setOf(1L, 2L), (viewModel.uiState.value as SportSignUiState.Content).busyLessonIds)

            actions.gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(emptySet<Long>(), (viewModel.uiState.value as SportSignUiState.Content).busyLessonIds)
            val toast = SportSignEvent.ShowToast(UiText.Res(Res.string.sport_sign_success))
            assertEquals(toast, viewModel.events.first())
            assertEquals(toast, viewModel.events.first())
        }

    @Test
    fun aFailedSignInWaitsForTheViewAndFreesTheLesson() = runTest(main.dispatcher) {
        emitSnapshot()
        val viewModel = viewModel()
        advanceUntilIdle()
        actions.result = AppResult.Failure(AppError.Network)

        viewModel.signUpForLesson(SportCardFixtures.lesson(1))
        advanceUntilIdle()

        assertEquals(emptySet<Long>(), (viewModel.uiState.value as SportSignUiState.Content).busyLessonIds)
        assertEquals(SportSignEvent.ShowError(AppError.Network), viewModel.events.first())
        viewModel.signUpForLesson(SportCardFixtures.lesson(1))
        runCurrent()
        assertEquals(listOf(1L, 1L), actions.signedInLessons)
    }

    @Test
    fun aConfirmedFreeSignReachesBackendOnceWithTheForceSignSwitch() = runTest(main.dispatcher) {
        emitSnapshot()
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.executeAutoSignCommand(SportSignCommand.CreateFreeSign(1), forceSign = true)
        viewModel.executeAutoSignCommand(SportSignCommand.CreateFreeSign(1), forceSign = true)
        advanceUntilIdle()

        assertEquals(listOf(1L to true), actions.freeSignRequests)
    }

    private suspend fun emitCatalog(vararg lessons: SportLesson) {
        schedule.filters.emit(AppResult.Success(emptyCatalog()))
        schedule.timeSlots.emit(AppResult.Success(emptyList()))
        schedule.schedule.emit(LoadState.Content(lessons.toList()))
    }

    private fun lessonOn(id: Long, day: Int, hour: Int = 18) = SportCardFixtures.lesson(id).let { lesson ->
        val start = Instant.parse("2026-09-${day.twoDigits()}T${hour.twoDigits()}:30:00+03:00")
        lesson.copy(start = start, end = start + 90.minutes)
    }

    private fun SportLesson.predicted() = copy(isLessonReal = false, start = start + 14.days, end = end + 14.days)

    @Test
    fun aSharedLessonHiddenByTheFiltersSelectsItsDayAndOpensItsCard() = runTest(main.dispatcher) {
        val hidden = lessonOn(5, day = 10).copy(
            available = 0, canSignIn = false, unavailableReasons = listOf(UnavailableReason.DailyLimitReached)
        )
        emitCatalog(SportCardFixtures.lesson(1), hidden)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.openSharedLesson(5)
        advanceUntilIdle()

        assertEquals(SportSignEvent.OpenLessonDetails(hidden), viewModel.events.first())
        assertEquals(LocalDate(2026, 9, 10), viewModel.selectedDate())
        assertTrue((viewModel.uiState.value as SportSignUiState.Content).displayedLessons.isEmpty())
        assertEquals(hidden, viewModel.linkedLesson(5))
        assertNull(viewModel.linkedLesson(1))
    }

    @Test
    fun anEndedOrMissingSharedLessonIsUnavailable() = runTest(main.dispatcher) {
        emitCatalog(lessonOn(3, day = 8, hour = 9))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.openSharedLesson(3)
        advanceUntilIdle()
        assertEquals(SportSignEvent.ShowLinkUnavailable, viewModel.events.first())

        viewModel.openSharedLesson(404)
        advanceUntilIdle()
        assertEquals(SportSignEvent.ShowLinkUnavailable, viewModel.events.first())
        assertEquals(LocalDate(2026, 9, 8), viewModel.selectedDate())
    }

    @Test
    fun aFailedCatalogReportsTheError() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        schedule.schedule.emit(LoadState.Error(AppError.Network))
        advanceUntilIdle()

        viewModel.openSharedLesson(1)
        advanceUntilIdle()

        assertEquals(SportSignEvent.ShowError(AppError.Network), viewModel.events.first())
    }

    @Test
    fun aRequestBeforeTheCatalogAnswersRunsOnceItDoes() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        viewModel.openSharedLesson(1)
        advanceUntilIdle()

        emitCatalog(SportCardFixtures.lesson(1))
        advanceUntilIdle()

        assertEquals(SportSignEvent.OpenLessonDetails(SportCardFixtures.lesson(1)), viewModel.events.first())
    }

    @Test
    fun aPredictedLinkOpensThePredictionAndARealLinkWithTheSameIdOpensThePrototype() =
        runTest(main.dispatcher) {
            val prototype = lessonOn(7, day = 9)
            emitCatalog(prototype, prototype.predicted())
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.openSharedLesson(7, predicted = true)
            advanceUntilIdle()
            assertEquals(SportSignEvent.OpenLessonDetails(prototype.predicted()), viewModel.events.first())
            assertEquals(LocalDate(2026, 9, 23), viewModel.selectedDate())
            assertEquals(prototype.predicted(), viewModel.linkedLesson(7))

            viewModel.openSharedLesson(7)
            advanceUntilIdle()
            assertEquals(SportSignEvent.OpenLessonDetails(prototype), viewModel.events.first())
        }

    @Test
    fun aPredictedLinkOpensTheRealRepeatOnceItIsInTheCatalog() = runTest(main.dispatcher) {
        val prototype = lessonOn(7, day = 9)
        val repeat = lessonOn(8, day = 23)
        val otherTeacher = lessonOn(9, day = 23).copy(teacherIsu = 200)
        emitCatalog(prototype, otherTeacher, repeat)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.openSharedLesson(7, predicted = true)
        advanceUntilIdle()

        assertEquals(SportSignEvent.OpenLessonDetails(repeat), viewModel.events.first())
    }

    @Test
    fun aPredictedLinkWhosePrototypeLeftTheCatalogIsUnavailable() = runTest(main.dispatcher) {
        emitCatalog(lessonOn(8, day = 23))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.openSharedLesson(7, predicted = true)
        advanceUntilIdle()

        assertEquals(SportSignEvent.ShowLinkUnavailable, viewModel.events.first())
    }

    @Test
    fun theAutoSignLimitDateReadsAsARussianDeviceShowedIt() = runTest(main.dispatcher) {
        // What java.time wrote on a "ru" device before the port (`SportAutoSignLegacyDateTest` pins it on the JVM).
        val legacy = "12 сент. 2026 г., 09:00:00"
        data.limits = SportAutoSignLimits(limit = 2, available = 0, nextAvailableAt = Instant.parse("2026-09-12T09:00:00+03:00"))
        emitCatalog()
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.handleAutoSignClick(lessonOn(7, day = 9).predicted())
        advanceUntilIdle()

        val expected = UiText.Res(Res.string.sport_auto_sign_limit_reached, listOf(legacy))
        assertEquals(SportSignEvent.ShowInfoDialog(message = expected), viewModel.events.first())
    }

    private fun Int.twoDigits(): String = toString().padStart(2, '0')
}
