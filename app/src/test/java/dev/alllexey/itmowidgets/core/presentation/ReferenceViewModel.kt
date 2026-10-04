package dev.alllexey.itmowidgets.core.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** A repository shaped like the ones the ViewModel contract expects: a cached flow, a refresh and row actions. */
interface ReferenceRepository {
    fun observeItems(): Flow<LoadState<List<ReferenceItem>>>
    suspend fun refresh(force: Boolean)
    suspend fun archive(id: Int): AppResult<Unit>
}

data class ReferenceItem(val id: Int, val title: String)

/**
 * The ViewModel contract in its smallest form (recipe `viewmodel-contract`): one [uiState], one [events] flow,
 * `refresh(RefreshMode)`, row actions through [BusyKeys], state derived from the repository flow and never stranded.
 */
class ReferenceViewModel(private val repository: ReferenceRepository) : ViewModel() {

    private val refreshes = RefreshTracker(viewModelScope)
    private val busyRows = BusyKeys<Int>(viewModelScope)
    private val eventQueue = EventQueue<ReferenceEvent>()

    val uiState: StateFlow<ReferenceUiState> = combine(
        repository.observeItems(),
        refreshes.refreshing,
        busyRows.busy,
        ::toUiState
    ).stateIn(viewModelScope, SharingStarted.Eagerly, ReferenceUiState.Loading)

    val events: Flow<ReferenceEvent> = eventQueue.events

    init {
        refresh(RefreshMode.Silent)
    }

    fun refresh(mode: RefreshMode) {
        refreshes.launch(mode) { repository.refresh(force = it == RefreshMode.Force) }
    }

    fun archive(id: Int) {
        busyRows.launch(id) {
            val result = repository.archive(id)
            if (result is AppResult.Failure) eventQueue.send(ReferenceEvent.ActionFailed(result.error))
        }
    }

    private fun toUiState(items: LoadState<List<ReferenceItem>>, refreshing: Boolean, busy: Set<Int>) = when (items) {
        // Keep progress while a refresh may still replace the error.
        is LoadState.Error -> if (refreshing) ReferenceUiState.Loading else ReferenceUiState.Error(items.error)
        LoadState.Loading -> ReferenceUiState.Loading
        LoadState.Disabled -> ReferenceUiState.Disabled
        is LoadState.Content -> ReferenceUiState.Content(
            rows = items.value.map { ReferenceRow(it.id, it.title, busy = it.id in busy) },
            refreshing = refreshing,
            refreshError = items.error
        )
    }
}
