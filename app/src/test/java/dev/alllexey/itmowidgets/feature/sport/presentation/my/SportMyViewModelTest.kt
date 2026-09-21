package dev.alllexey.itmowidgets.feature.sport.presentation.my

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.util.DataState
import dev.alllexey.itmowidgets.feature.sport.cards.SportCardFixtures
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAttempts
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportScore
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportBookingRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportDataRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportScheduleRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.bookingDelegate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SportMyViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val bookings = FakeSportBookingRepository()
    private val data = FakeSportDataRepository()

    private fun TestScope.viewModel() = SportMyViewModel(
        bookings, data, bookingDelegate(bookings, FakeSportScheduleRepository(), data, this)
    )

    private suspend fun emitSnapshot() {
        data.attempts.emit(DataState.Success(SportAttempts(total = 3, used = 1, free = 2, canSignIn = true)))
        data.score.emit(DataState.Success(SportScore(attendances = 40, other = 10, attendancesData = emptyList())))
        bookings.merged.emit(dev.alllexey.itmowidgets.core.util.MergedDataState.Success(listOf(SportCardFixtures.booking(7))))
    }

    @Test
    fun `a refresh keeps the content on screen and only marks it refreshing`() = runTest(mainDispatcherRule.dispatcher) {
        emitSnapshot()
        val viewModel = viewModel()
        advanceUntilIdle()
        val before = viewModel.uiState.value as SportMyUiState.Content
        assertFalse(before.refreshing)

        bookings.gate = CompletableDeferred()
        viewModel.refreshAllData()
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
    fun `without a snapshot the screen stays loading through the refresh`() = runTest(mainDispatcherRule.dispatcher) {
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
    fun `a failed source after content keeps the content with a partial error`() = runTest(mainDispatcherRule.dispatcher) {
        emitSnapshot()
        val viewModel = viewModel()
        advanceUntilIdle()
        val content = viewModel.uiState.value as SportMyUiState.Content

        data.score.emit(DataState.Error(AppError.Network))
        advanceUntilIdle()

        val after = viewModel.uiState.value as SportMyUiState.Content
        assertEquals(content.score, after.score)
        assertEquals(content.bookings, after.bookings)
        assertTrue(after.hasPartialError)
        assertFalse(after.refreshing)
    }

    @Test
    fun `a failed source before any content is an error`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = viewModel()
        data.score.emit(DataState.Error(AppError.Network))
        data.attempts.emit(DataState.Error(AppError.Network))
        bookings.merged.emit(dev.alllexey.itmowidgets.core.util.MergedDataState.Error(AppError.Network))
        advanceUntilIdle()
        assertEquals(SportMyUiState.Error(AppError.Network), viewModel.uiState.value)
    }
}
