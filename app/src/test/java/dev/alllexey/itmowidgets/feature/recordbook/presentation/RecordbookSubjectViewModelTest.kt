package dev.alllexey.itmowidgets.feature.recordbook.presentation

import dev.alllexey.itmowidgets.feature.recordbook.FakeBarsPreference
import dev.alllexey.itmowidgets.feature.recordbook.FakeBarsRepository
import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.feature.recordbook.FakeRecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.FakeSportScoreRepository
import dev.alllexey.itmowidgets.feature.recordbook.barsJournal
import dev.alllexey.itmowidgets.feature.recordbook.barsSubject
import dev.alllexey.itmowidgets.feature.recordbook.recordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsSubjectDetails
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportResolver
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecordbookSubjectViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()
    private val repository = FakeRecordbookRepository()
    private val bars = FakeBarsRepository()
    private fun model(withBars: Boolean = false) = RecordbookSubjectViewModel(repository, bars, SavedStateHandle(buildMap {
        put("entry_id", 42L); put("program_id", 1L); put("semester", 2); put("study_year", "2025/2026")
        if (withBars) { put("bars_plan", 8L); put("bars_type", "flow"); put("bars_identifier", "7") }
    }), RecordbookSportResolver(FakeSportScoreRepository()))

    @Test fun `BARS journal overlays the official subject and supplies its controls`() = runTest {
        val control = RecordbookControl(6, "Работа", 7.5, 0.0, 10.0, true, null, null)
        bars.details = AppResult.Success(BarsSubjectDetails(barsSubject(), listOf(control)))
        val vm = model(withBars = true); advanceUntilIdle()
        val state = vm.uiState.value as RecordbookSubjectUiState.Content
        assertEquals(listOf(barsJournal()), bars.journalRequests)
        assertEquals(91.5, state.subject.score!!, 0.0)
        assertEquals(42L, state.subject.entryId)
        assertEquals(listOf(control), state.controls)
        assertEquals(0, repository.controlRequests)
        assertNull(state.barsError)
    }
    @Test fun `failed BARS journal falls back to official values with a visible error`() = runTest {
        bars.details = AppResult.Failure(AppError.Network)
        val vm = model(withBars = true); advanceUntilIdle()
        val state = vm.uiState.value as RecordbookSubjectUiState.Content
        assertEquals(recordbookSubject(), state.subject)
        assertEquals(AppError.Network, state.barsError)
        assertEquals(1, repository.controlRequests)
    }
    @Test fun `subject without a BARS journal never asks BARS`() = runTest {
        model(); advanceUntilIdle()
        assertTrue(bars.journalRequests.isEmpty())
    }

    @Test fun `no tree does not make a detail API request`() = runTest {
        repository.subjects = AppResult.Success(listOf(recordbookSubject(details = false)))
        val vm = model(); advanceUntilIdle()
        assertEquals(0, repository.controlRequests)
        assertTrue((vm.uiState.value as RecordbookSubjectUiState.Content).controls.isEmpty())
    }

    @Test fun `failed controls leave the overview available`() = runTest {
        repository.controls = AppResult.Failure(AppError.Forbidden)
        val vm = model(); advanceUntilIdle()
        val state = vm.uiState.value as RecordbookSubjectUiState.Content
        assertEquals(recordbookSubject(), state.subject)
        assertEquals(AppError.Forbidden, state.controlsError)
    }

    @Test fun `refresh reloads official overview instead of showing stale navigation arguments`() = runTest {
        val vm = model(); advanceUntilIdle()
        val updated = recordbookSubject().copy(rate = "5/A", score = 96.0)
        repository.subjects = AppResult.Success(listOf(updated))
        vm.refresh(); advanceUntilIdle()
        assertEquals(updated, (vm.uiState.value as RecordbookSubjectUiState.Content).subject)
    }

    @Test fun `unknown entry yields not found without requesting controls`() = runTest {
        repository.subjects = AppResult.Success(emptyList())
        val vm = model(); advanceUntilIdle()
        assertEquals(RecordbookSubjectUiState.Error(AppError.NotFound), vm.uiState.value)
        assertEquals(0, repository.controlRequests)
    }

    @Test fun `failed refresh preserves content and stops spinner`() = runTest {
        val vm = model(); advanceUntilIdle()
        repository.subjects = AppResult.Failure(AppError.Network)
        vm.refresh(); vm.refresh(); advanceUntilIdle()
        val state = vm.uiState.value as RecordbookSubjectUiState.Content
        assertEquals(recordbookSubject(), state.subject)
        assertEquals(AppError.Network, state.refreshError)
        assertFalse(state.refreshing)
    }
}
