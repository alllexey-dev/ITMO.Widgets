package dev.alllexey.itmowidgets.feature.home.presentation

import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.result.AppError

sealed interface HomeUiState {
    data object Loading : HomeUiState

    /**
     * An empty [cards] list is content: the feed has nothing to say, not nothing loaded. [cards] are in feed order
     * and drawn by their features' `HomeCardRenderer`s. [refreshing] is true only for a refresh the user asked for.
     */
    data class Content(val cards: List<HomeCard>, val refreshing: Boolean) : HomeUiState
}

sealed interface HomeEvent {
    data class RefreshFailed(val error: AppError) : HomeEvent
}
