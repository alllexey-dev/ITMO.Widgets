package dev.alllexey.itmowidgets.feature.recordbook.presentation

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.feature.recordbook.FakeRecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.FakeSportScoreRepository
import dev.alllexey.itmowidgets.feature.recordbook.FixedAcademicTime
import dev.alllexey.itmowidgets.feature.recordbook.recordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportResolver
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecordbookViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()
    private val repository = FakeRecordbookRepository()
    private val sport = FakeSportScoreRepository()

    private fun model(state: SavedStateHandle = SavedStateHandle(), date: String = "2026-09-07") =
        RecordbookViewModel(repository, state, RecordbookSportResolver(sport), FixedAcademicTime(date))

    @Test fun `loads current academic period without asking sport for regular subjects`() = runTest {
        val vm = model(); vm.ensureDataLoaded(); advanceUntilIdle()
        assertEquals(3, (vm.uiState.value as RecordbookUiState.Content).selection.period.semester)
        assertEquals(0, sport.periodRequests)
    }

    @Test fun `academic override selects spring even when server actual is autumn`() = runTest {
        val vm = model(date = "2026-06-01"); vm.ensureDataLoaded(); advanceUntilIdle()
        assertEquals(2, (vm.uiState.value as RecordbookUiState.Content).selection.period.semester)
    }

    @Test fun `restores explicitly selected historical period`() = runTest {
        val vm = model(SavedStateHandle(mapOf<String, Any>("recordbook_program_id" to 1L, "recordbook_semester" to 1)))
        vm.ensureDataLoaded(); advanceUntilIdle()
        assertEquals(1, (vm.uiState.value as RecordbookUiState.Content).selection.period.semester)
    }

    @Test fun `renders real empty catalog instead of not found error`() = runTest {
        repository.programs = AppResult.Success(emptyList())
        val vm = model(); vm.ensureDataLoaded(); advanceUntilIdle()
        assertEquals(RecordbookUiState.Empty, vm.uiState.value)
    }

    @Test fun `catalog failure is retryable`() = runTest {
        repository.programs = AppResult.Failure(AppError.Network)
        val vm = model(); vm.ensureDataLoaded(); advanceUntilIdle()
        assertEquals(RecordbookUiState.Error(AppError.Network), vm.uiState.value)
    }

    @Test fun `refresh keeps content and ends spinner even when identical result arrives`() = runTest {
        val vm = model(); vm.ensureDataLoaded(); advanceUntilIdle()
        val before = vm.uiState.value
        vm.refresh()
        assertTrue((vm.uiState.value as RecordbookUiState.Content).refreshing)
        advanceUntilIdle()
        assertEquals(before, vm.uiState.value)
        assertEquals(2, repository.programRequests)
    }

    @Test fun `repeated failed refresh keeps previous values and clears spinner`() = runTest {
        val vm = model(); vm.ensureDataLoaded(); advanceUntilIdle()
        repository.subjects = AppResult.Failure(AppError.Network)
        vm.refresh(); vm.refresh(); advanceUntilIdle()
        val state = vm.uiState.value as RecordbookUiState.Content
        assertFalse(state.refreshing)
        assertEquals(AppError.Network, state.refreshError)
        assertEquals(listOf(recordbookSubject()), state.subjects)
    }

    @Test fun `rapid period selection cancels old result and cannot mix periods`() = runTest {
        val vm = model(); vm.ensureDataLoaded(); advanceUntilIdle()
        val oldRequest = CompletableDeferred<AppResult<List<dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject>>>()
        repository.subjectLoader = { semester -> if (semester == 1) oldRequest.await() else AppResult.Success(listOf(recordbookSubject(semester.toLong()))) }
        vm.selectPeriod(1, 1); runCurrent()
        vm.selectPeriod(1, 2); advanceUntilIdle()
        oldRequest.complete(AppResult.Success(listOf(recordbookSubject(99))))
        advanceUntilIdle()
        val state = vm.uiState.value as RecordbookUiState.Content
        assertEquals(2, state.selection.period.semester)
        assertEquals(2L, state.subjects.single().entryId)
    }

    @Test fun `sport failure does not hide official grades or mark PE passed`() = runTest {
        repository.subjects = AppResult.Success(listOf(recordbookSubject(name = "Физическая культура и спорт (элективная)").copy(rate = null)))
        sport.periods = AppResult.Failure(AppError.Network)
        val vm = model(); vm.ensureDataLoaded(); advanceUntilIdle()
        val state = vm.uiState.value as RecordbookUiState.Content
        assertEquals(RecordbookSportState.Error, state.sport)
        assertNull(state.subjects.single().rate)
    }

    @Test fun `same period does not trigger another request`() = runTest {
        val vm = model(); vm.ensureDataLoaded(); advanceUntilIdle()
        vm.selectPeriod(1, 3); advanceUntilIdle()
        assertEquals(listOf(3), repository.subjectRequests)
    }
}
