package dev.alllexey.itmowidgets.feature.sport.presentation.user

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking

sealed interface UserSportUiState {
    data object Loading : UserSportUiState
    data class Error(val error: AppError) : UserSportUiState
    data class Content(val bookings: List<SportBooking>, val refreshing: Boolean) : UserSportUiState
}
