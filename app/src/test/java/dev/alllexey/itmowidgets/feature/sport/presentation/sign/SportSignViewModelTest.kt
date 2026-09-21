package dev.alllexey.itmowidgets.feature.sport.presentation.sign

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.util.DataState
import dev.alllexey.itmowidgets.core.util.MergedDataState
import dev.alllexey.itmowidgets.feature.sport.cards.SportCardFixtures
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeAcademicTimeProvider
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportBookingRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportDataRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportScheduleRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportSignPreferences
import dev.alllexey.itmowidgets.feature.sport.presentation.bookingDelegate
import dev.alllexey.itmowidgets.feature.sport.presentation.emptyCatalog
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
class SportSignViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val schedule = FakeSportScheduleRepository()
    private val data = FakeSportDataRepository()
    private val time = FakeAcademicTimeProvider()

    private fun TestScope.viewModel() = SportSignViewModel(
        schedule, data, SportSignFilterController(time), SportSignStateFactory(time),
        bookingDelegate(FakeSportBookingRepository(), schedule, data, this), FakeSportSignPreferences
    )

    private suspend fun emitSnapshot() {
        schedule.filters.emit(DataState.Success(emptyCatalog()))
        schedule.timeSlots.emit(DataState.Success(emptyList()))
        schedule.schedule.emit(MergedDataState.Success(listOf(SportCardFixtures.lesson(1))))
    }

    @Test
    fun `a refresh keeps the catalogue on screen and only marks it refreshing`() = runTest(mainDispatcherRule.dispatcher) {
        emitSnapshot()
        val viewModel = viewModel()
        advanceUntilIdle()
        val before = viewModel.uiState.value as SportSignUiState.Content
        assertFalse(before.refreshing)

        schedule.gate = CompletableDeferred()
        viewModel.refreshAllData()
        runCurrent()
        val during = viewModel.uiState.value as SportSignUiState.Content
        assertTrue(during.refreshing)
        assertEquals(before.displayedWeek, during.displayedWeek)

        schedule.gate.complete(Unit)
        advanceUntilIdle()
        assertFalse((viewModel.uiState.value as SportSignUiState.Content).refreshing)
    }

    @Test
    fun `before the catalogue answers the calendar shows over a placeholder without an indicator`() = runTest(mainDispatcherRule.dispatcher) {
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
    fun `the entry refresh is silent and a pull shows the indicator`() = runTest(mainDispatcherRule.dispatcher) {
        emitSnapshot()
        val viewModel = viewModel()
        runCurrent()
        assertFalse((viewModel.uiState.value as SportSignUiState.Content).refreshing)
        advanceUntilIdle()
        schedule.gate = CompletableDeferred()
        viewModel.refreshAllData()
        runCurrent()
        assertTrue((viewModel.uiState.value as SportSignUiState.Content).refreshing)
        schedule.gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `a failed source after content keeps the content with a partial error`() = runTest(mainDispatcherRule.dispatcher) {
        emitSnapshot()
        val viewModel = viewModel()
        advanceUntilIdle()

        schedule.schedule.emit(MergedDataState.Error(AppError.Network))
        advanceUntilIdle()

        val after = viewModel.uiState.value as SportSignUiState.Content
        assertTrue(after.hasPartialError)
        assertFalse(after.refreshing)
    }

    @Test
    fun `a failed source before any content is an error`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = viewModel()
        schedule.filters.emit(DataState.Error(AppError.Network))
        schedule.timeSlots.emit(DataState.Error(AppError.Network))
        schedule.schedule.emit(MergedDataState.Error(AppError.Network))
        advanceUntilIdle()
        assertEquals(SportSignUiState.Error(AppError.Network), viewModel.uiState.value)
    }
}
