package dev.alllexey.itmowidgets.feature.sport.presentation.my

import dev.alllexey.itmowidgets.core.lesson.LessonPillKind
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.feature.sport.cards.SportCardFixtures
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAttempts
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntryStatus
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportScore
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportActionRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportBookingRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportDataRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportScheduleRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportSignPreferences
import dev.alllexey.itmowidgets.feature.sport.presentation.applicationScope
import dev.alllexey.itmowidgets.feature.sport.presentation.bookingDelegate
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingAction
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingsHolder
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.feature.sport.SportStrings
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class SportMyViewModelTest {

    private val main = TestMainDispatcher()

    @BeforeTest
    fun setUpMain() = main.install()

    @AfterTest
    fun tearDownMain() = main.reset()

    private val bookings = FakeSportBookingRepository()
    private val data = FakeSportDataRepository()
    private val actions = FakeSportActionRepository()
    private val preferences = FakeSportSignPreferences()

    /** Monday, the day before the fixture lesson, at noon. */
    private val time = FixedAcademicTime(LocalDateTime(2026, 9, 7, 12, 0))

    private fun TestScope.viewModel() = SportMyViewModel(
        bookings,
        data,
        preferences,
        SportBookingsHolder(
            bookings, data,
            bookingDelegate(bookings, FakeSportScheduleRepository(), data, backgroundScope, actions),
            time,
            applicationScope(),
        ),
        time,
    )

    private fun SportMyViewModel.content() = uiState.value.body as SportMyBody.Content

    private suspend fun emitSnapshot() {
        data.attempts.emit(LoadState.Content(SportAttempts(total = 3, used = 1, free = 2, canSignIn = true)))
        data.score.emit(LoadState.Content(SportScore(attendances = 40, other = 10, attendancesData = emptyList())))
        bookings.merged.emit(LoadState.Content(listOf(SportCardFixtures.booking(7))))
    }

    @Test
    fun theScoreIsARingWithItsFormulaAndWhatIsLeft() = runTest(main.dispatcher) {
        data.score.emit(LoadState.Content(SportScore(attendances = 40, other = 52, attendancesData = emptyList())))
        bookings.merged.emit(LoadState.Content(emptyList()))
        val viewModel = viewModel()
        advanceUntilIdle()

        val score = viewModel.uiState.value.score!!
        assertEquals(80, score.total, "the bonus counts up to 40")
        assertEquals(UiText.Res(SportStrings.sport_score_formula, listOf(40, 40)), score.formula)
        assertEquals(UiText.Res(SportStrings.sport_score_remaining, listOf(20)), score.status)
        assertEquals(0.4f, score.visitsShare)
        assertEquals(0.4f, score.bonusShare)
        assertEquals(emptyList(), viewModel.content().days, "no bookings: the empty state")
    }

    @Test
    fun bookingsAndQueuesAreLessonCardsByDayAndPastOnesAreGone() = runTest(main.dispatcher) {
        val later = SportCardFixtures.start + 2.days
        val queued = SportCardFixtures.booking(8).copy(signed = false, signEntry = SportCardFixtures.entry(), start = later, end = later + 1.days / 16)
        val past = SportCardFixtures.booking(9).copy(start = SportCardFixtures.start - 3.days, end = SportCardFixtures.start - 3.days)
        val expired = SportCardFixtures.booking(10).copy(signed = false, signEntry = SportCardFixtures.entry(SportQueueEntryStatus.EXPIRED))
        data.score.emit(LoadState.Content(SportScore(attendances = 40, other = 10, attendancesData = emptyList())))
        bookings.merged.emit(LoadState.Content(listOf(queued, past, SportCardFixtures.booking(7), expired)))
        val viewModel = viewModel()
        advanceUntilIdle()

        val days = viewModel.content().days
        assertEquals(
            listOf(UiText.Res(SportStrings.sport_day_tomorrow, listOf("8 сент.")), UiText.Dynamic("Чт, 10 сент.")),
            days.map { it.title },
        )
        val booked = days[0].bookings.single()
        assertEquals(7L, booked.lessonId)
        assertEquals(LessonPillKind.BOOKED, booked.lesson.pills.single().kind)
        assertEquals(UiText.Res(SportStrings.sport_queue_position, listOf(3, 12)), days[1].bookings.single().lesson.pills.single().text)
    }

    @Test
    fun theSectionsEntryNamesThePinnedSections() = runTest(main.dispatcher) {
        emitSnapshot()
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(UiText.Res(SportStrings.sport_sections_entry_empty), viewModel.uiState.value.sections)

        preferences.pinned.value = setOf(SectionName("Плавание"), SectionName("Волейбол"))
        advanceUntilIdle()
        assertEquals(UiText.Dynamic("Волейбол, Плавание"), viewModel.uiState.value.sections)
    }

    @Test
    fun aRefreshKeepsTheContentOnScreenAndOnlyMarksItRefreshing() = runTest(main.dispatcher) {
        emitSnapshot()
        val viewModel = viewModel()
        advanceUntilIdle()
        val before = viewModel.content()
        assertFalse(before.refreshing)

        bookings.gate = CompletableDeferred()
        viewModel.refresh(RefreshMode.Pull)
        runCurrent()
        assertTrue(viewModel.content().refreshing)
        assertEquals(before.days, viewModel.content().days)

        bookings.gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.content().refreshing)
    }

    @Test
    fun withoutASnapshotTheScreenStaysLoadingThroughTheRefresh() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        bookings.gate = CompletableDeferred()
        viewModel.ensureDataLoaded()
        runCurrent()
        assertEquals(SportMyBody.Loading, viewModel.uiState.value.body)
        bookings.gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(SportMyBody.Loading, viewModel.uiState.value.body)
    }

    @Test
    fun aFailedSourceAfterContentKeepsTheContentWithAPartialError() = runTest(main.dispatcher) {
        emitSnapshot()
        val viewModel = viewModel()
        advanceUntilIdle()
        val content = viewModel.content()

        bookings.merged.emit(LoadState.Error(AppError.Network))
        advanceUntilIdle()

        assertEquals(content.days, viewModel.content().days)
        assertTrue(viewModel.content().partialError)
    }

    @Test
    fun aClearedSessionDropsTheContentOfThePreviousOne() = runTest(main.dispatcher) {
        emitSnapshot()
        val viewModel = viewModel()
        advanceUntilIdle()

        data.score.emit(LoadState.Loading)
        bookings.merged.emit(LoadState.Content(emptyList()))
        advanceUntilIdle()

        assertEquals(SportMyBody.Loading, viewModel.uiState.value.body)
        assertNull(viewModel.uiState.value.score)
    }

    @Test
    fun aFailedSourceBeforeAnyContentIsAnError() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        data.score.emit(LoadState.Error(AppError.Network))
        bookings.merged.emit(LoadState.Error(AppError.Network))
        advanceUntilIdle()
        assertEquals(SportMyBody.Failed(AppError.Network), viewModel.uiState.value.body)
    }

    @Test
    fun aSecondEntryWithContentLoadedStartsNoRequest() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        bookings.gate = CompletableDeferred()
        viewModel.ensureDataLoaded()
        viewModel.ensureDataLoaded()
        runCurrent()
        assertEquals(1, bookings.refreshCount, "an entry while the first load runs joins it")

        emitSnapshot()
        bookings.gate.complete(Unit)
        advanceUntilIdle()
        viewModel.ensureDataLoaded()
        runCurrent()
        assertEquals(1, bookings.refreshCount)
    }

    @Test
    fun aCancellationTheSheetAskedForIsConfirmedFirstAndOnlyWhileOffered() = runTest(main.dispatcher) {
        emitSnapshot()
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onSheetAction(7, SportBookingAction.SIGN)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.cancel, "a booking offers no sign-in")

        viewModel.onSheetAction(7, SportBookingAction.CANCEL)
        advanceUntilIdle()
        assertEquals(UiText.Res(CoreStrings.sport_cancel_booking_question), viewModel.uiState.value.cancel?.message)

        val refreshes = bookings.refreshCount
        viewModel.confirmCancel()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.cancel)
        assertTrue(bookings.refreshCount > refreshes, "the cancellation refreshed the bookings")
    }

    @Test
    fun aFailedCancellationWaitsForTheView() = runTest(main.dispatcher) {
        emitSnapshot()
        val viewModel = viewModel()
        advanceUntilIdle()
        actions.result = AppResult.Failure(AppError.Network)

        viewModel.onSheetAction(7, SportBookingAction.CANCEL)
        viewModel.confirmCancel()
        advanceUntilIdle()

        assertEquals(AppError.Network.toUiText(), viewModel.events.first())
    }

    @Test
    fun theSheetOfABookingIsItsSnapshot() = runTest(main.dispatcher) {
        emitSnapshot()
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(7L, viewModel.details(7)?.lessonId)
        assertNull(viewModel.details(99))
    }
}
