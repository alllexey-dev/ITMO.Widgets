package dev.alllexey.itmowidgets.feature.sport.presentation.my

import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.feature.sport.cards.SportCardFixtures
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAttempts
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportScore
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportActionRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportBookingRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportDataRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportScheduleRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.applicationScope
import dev.alllexey.itmowidgets.feature.sport.presentation.bookingDelegate
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingsHolder
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

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

    private fun TestScope.viewModel() = SportMyViewModel(
        bookings, data,
        SportBookingsHolder(
            bookings, data,
            bookingDelegate(bookings, FakeSportScheduleRepository(), data, backgroundScope, actions),
            FixedAcademicTime(),
            applicationScope()
        )
    )

    private suspend fun emitSnapshot() {
        data.attempts.emit(AppResult.Success(SportAttempts(total = 3, used = 1, free = 2, canSignIn = true)))
        data.score.emit(AppResult.Success(SportScore(attendances = 40, other = 10, attendancesData = emptyList())))
        bookings.merged.emit(LoadState.Content(listOf(SportCardFixtures.booking(7))))
    }

    @Test
    fun aRefreshKeepsTheContentOnScreenAndOnlyMarksItRefreshing() = runTest(main.dispatcher) {
        emitSnapshot()
        val viewModel = viewModel()
        advanceUntilIdle()
        val before = viewModel.uiState.value as SportMyUiState.Content
        assertFalse(before.refreshing)

        bookings.gate = CompletableDeferred()
        viewModel.refresh(RefreshMode.Pull)
        runCurrent()
        val during = viewModel.uiState.value as SportMyUiState.Content
        assertTrue(during.refreshing)
        assertEquals(before.bookings, during.bookings)
        assertEquals(before.score, during.score)

        bookings.gate.complete(Unit)
        advanceUntilIdle()
        assertFalse((viewModel.uiState.value as SportMyUiState.Content).refreshing)
    }

    @Test
    fun theFirstLoadIsSilentAndAPullShowsTheIndicator() = runTest(main.dispatcher) {
        emitSnapshot()
        val viewModel = viewModel()
        advanceUntilIdle()
        bookings.gate = CompletableDeferred()
        viewModel.refresh(RefreshMode.Silent)
        runCurrent()
        assertFalse((viewModel.uiState.value as SportMyUiState.Content).refreshing)
        bookings.gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun withoutASnapshotTheScreenStaysLoadingThroughTheRefresh() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        bookings.gate = CompletableDeferred()
        viewModel.ensureDataLoaded()
        runCurrent()
        assertEquals(SportMyUiState.Loading, viewModel.uiState.value)
        bookings.gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(SportMyUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun aFailedSourceAfterContentKeepsTheContentWithAPartialError() = runTest(main.dispatcher) {
        emitSnapshot()
        val viewModel = viewModel()
        advanceUntilIdle()
        val content = viewModel.uiState.value as SportMyUiState.Content

        data.score.emit(AppResult.Failure(AppError.Network))
        advanceUntilIdle()

        val after = viewModel.uiState.value as SportMyUiState.Content
        assertEquals(content.score, after.score)
        assertEquals(content.bookings, after.bookings)
        assertTrue(after.hasPartialError)
        assertFalse(after.refreshing)
    }

    @Test
    fun aFailedSourceBeforeAnyContentIsAnError() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        data.score.emit(AppResult.Failure(AppError.Network))
        data.attempts.emit(AppResult.Failure(AppError.Network))
        bookings.merged.emit(LoadState.Error(AppError.Network))
        advanceUntilIdle()
        assertEquals(SportMyUiState.Error(AppError.Network), viewModel.uiState.value)
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
        assertTrue(viewModel.uiState.value is SportMyUiState.Content)

        viewModel.ensureDataLoaded()
        runCurrent()
        assertEquals(1, bookings.refreshCount)
    }

    @Test
    fun aRetryOverAnErrorShowsProgressAndReachesTheRepositories() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        data.score.emit(AppResult.Failure(AppError.Network))
        data.attempts.emit(AppResult.Failure(AppError.Network))
        bookings.merged.emit(LoadState.Error(AppError.Network))
        advanceUntilIdle()
        assertEquals(SportMyUiState.Error(AppError.Network), viewModel.uiState.value)

        bookings.gate = CompletableDeferred()
        viewModel.refresh(RefreshMode.Force)
        runCurrent()
        assertEquals(SportMyUiState.Loading, viewModel.uiState.value)
        assertEquals(1, bookings.refreshCount)

        bookings.gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(SportMyUiState.Error(AppError.Network), viewModel.uiState.value)
    }

    @Test
    fun aFailedCancellationWaitsForTheView() = runTest(main.dispatcher) {
        emitSnapshot()
        val viewModel = viewModel()
        advanceUntilIdle()
        actions.result = AppResult.Failure(AppError.Network)

        viewModel.cancelBooking(SportCardFixtures.booking(7))
        advanceUntilIdle()

        assertEquals(SportMyEvent.ShowError(AppError.Network), viewModel.events.first())
    }
}
