package dev.alllexey.itmowidgets.feature.sport.presentation.points

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.presentation.RefreshTracker
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportDataRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * `Откуда баллы` on both platforms: the current semester's points from My ITMO with every award in its history. There
 * is no forecast: it cannot be computed honestly.
 */
class SportPointsViewModel(
    private val dataRepository: SportDataRepository,
    private val time: AcademicTimeProvider,
) : ViewModel() {

    private val refreshes = RefreshTracker(viewModelScope)
    private val filter = MutableStateFlow(SportPointsFilter.ALL)

    val uiState: StateFlow<SportPointsUiState> = combine(
        dataRepository.observeSportScore(),
        filter,
        refreshes.refreshing,
    ) { score, filter, refreshing ->
        when (score) {
            is LoadState.Content -> SportPointsPresenter.present(score.value, filter, time.timeZone).copy(refreshing = refreshing)
            is LoadState.Error -> SportPointsUiState.Failed(score.error)
            LoadState.Loading, LoadState.Disabled -> SportPointsUiState.Loading
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SportPointsUiState.Loading)

    init {
        refresh(RefreshMode.Silent)
    }

    fun refresh(mode: RefreshMode) {
        refreshes.launch(mode) { dataRepository.refreshSportScore() }
    }

    fun select(filter: SportPointsFilter) {
        this.filter.value = filter
    }
}
