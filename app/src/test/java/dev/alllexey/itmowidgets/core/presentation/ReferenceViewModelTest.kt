package dev.alllexey.itmowidgets.core.presentation

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReferenceViewModelTest {

    @get:Rule val main = MainDispatcherRule()
    private val repository = FakeReferenceRepository()

    @Test
    fun `entry refreshes silently and a forced retry reaches the repository`() = runTest(main.dispatcher) {
        val viewModel = ReferenceViewModel(repository)
        runCurrent()
        assertEquals(listOf(false), repository.refreshes)
        assertEquals(ReferenceUiState.Loading, viewModel.uiState.value)

        repository.items.value = LoadState.Content(listOf(ReferenceItem(1, "Physics")))
        repository.finishRefresh()
        runCurrent()
        assertEquals(ReferenceUiState.Content(listOf(ReferenceRow(1, "Physics", busy = false)), refreshing = false), viewModel.uiState.value)

        viewModel.refresh(RefreshMode.Force)
        runCurrent()
        assertEquals(listOf(false, true), repository.refreshes)
        assertEquals(true, (viewModel.uiState.value as ReferenceUiState.Content).refreshing)
    }

    @Test
    fun `an error is not shown while a refresh may still replace it`() = runTest(main.dispatcher) {
        repository.items.value = LoadState.Error(AppError.Network)
        val viewModel = ReferenceViewModel(repository)
        runCurrent()
        repository.finishRefresh()
        runCurrent()
        assertEquals(ReferenceUiState.Error(AppError.Network), viewModel.uiState.value)

        viewModel.refresh(RefreshMode.Pull)
        runCurrent()
        assertEquals(ReferenceUiState.Loading, viewModel.uiState.value)

        // The refresh ends on the value the repository already held: the state still leaves Loading.
        repository.finishRefresh()
        runCurrent()
        assertEquals(ReferenceUiState.Error(AppError.Network), viewModel.uiState.value)
    }

    @Test
    fun `a failed row action frees the row and its event waits for the view`() = runTest(main.dispatcher) {
        repository.items.value = LoadState.Content(listOf(ReferenceItem(1, "Physics")))
        repository.archiveResult = CompletableDeferred()
        val viewModel = ReferenceViewModel(repository)
        runCurrent()

        viewModel.archive(1)
        viewModel.archive(1)
        runCurrent()
        assertEquals(listOf(ReferenceRow(1, "Physics", busy = true)), (viewModel.uiState.value as ReferenceUiState.Content).rows)

        repository.archiveResult.complete(AppResult.Failure(AppError.Forbidden))
        runCurrent()
        val events = mutableListOf<ReferenceEvent>()
        backgroundScope.launch { viewModel.events.collect { events += it } }
        runCurrent()

        assertEquals(listOf(1), repository.archived)
        assertEquals(listOf(ReferenceRow(1, "Physics", busy = false)), (viewModel.uiState.value as ReferenceUiState.Content).rows)
        assertEquals(listOf<ReferenceEvent>(ReferenceEvent.ActionFailed(AppError.Forbidden)), events)
    }

    private class FakeReferenceRepository : ReferenceRepository {
        val items = MutableStateFlow<LoadState<List<ReferenceItem>>>(LoadState.Loading)
        val refreshes = mutableListOf<Boolean>()
        val archived = mutableListOf<Int>()
        var archiveResult = CompletableDeferred<AppResult<Unit>>(AppResult.Success(Unit))
        private var refreshGate = CompletableDeferred<Unit>()

        fun finishRefresh() {
            refreshGate.complete(Unit)
            refreshGate = CompletableDeferred()
        }

        override fun observeItems() = items

        override suspend fun refresh(force: Boolean) {
            refreshes += force
            refreshGate.await()
        }

        override suspend fun archive(id: Int): AppResult<Unit> {
            archived += id
            return archiveResult.await()
        }
    }
}
