package dev.alllexey.itmowidgets.feature.recordbook.presentation

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.feature.recordbook.FakeRecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.FakeSportScoreRepository
import dev.alllexey.itmowidgets.feature.recordbook.recordbookSubject
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
    private fun model() = RecordbookSubjectViewModel(repository, SavedStateHandle(mapOf(
        "entry_id" to 42L, "program_id" to 1L, "semester" to 2, "study_year" to "2025/2026"
    )), RecordbookSportResolver(FakeSportScoreRepository()))

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
