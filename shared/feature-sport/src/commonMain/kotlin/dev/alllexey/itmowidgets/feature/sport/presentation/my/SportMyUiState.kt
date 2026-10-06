package dev.alllexey.itmowidgets.feature.sport.presentation.my

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAttempts
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportScore

sealed class SportMyUiState {

    object Loading : SportMyUiState()

    data class Content(
        val attempts: SportAttempts,
        val score: SportScore,
        val bookings: List<SportBooking>,
        val hasPartialError: Boolean = false,
        /** A refresh is running behind content that stays on screen. */
        val refreshing: Boolean = false
    ) : SportMyUiState()

    data class Error(val error: AppError) : SportMyUiState()
}

sealed interface SportMyEvent {
    data class ShowError(val error: AppError) : SportMyEvent
}
