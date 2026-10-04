package dev.alllexey.itmowidgets.core.presentation

import dev.alllexey.itmowidgets.core.result.AppError

/** State and events of [ReferenceViewModel]; per the contract they live beside the ViewModel, never in its file. */
sealed interface ReferenceUiState {
    data object Loading : ReferenceUiState
    data object Disabled : ReferenceUiState
    data class Error(val error: AppError) : ReferenceUiState
    data class Content(
        val rows: List<ReferenceRow>,
        val refreshing: Boolean,
        val refreshError: AppError? = null
    ) : ReferenceUiState
}

data class ReferenceRow(val id: Int, val title: String, val busy: Boolean)

sealed interface ReferenceEvent {
    data class ActionFailed(val error: AppError) : ReferenceEvent
}
