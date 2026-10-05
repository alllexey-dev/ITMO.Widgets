package dev.alllexey.itmowidgets.feature.qr.presentation

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeSnapshot

sealed interface QrCodeUiState {
    data object Loading : QrCodeUiState
    data object Empty : QrCodeUiState
    data class Error(val error: AppError) : QrCodeUiState

    /** [useDynamicColors] is the user's QR colour setting: theme colours instead of black on white. */
    data class Content(
        val code: QrCodeSnapshot,
        val refreshing: Boolean = false,
        val useDynamicColors: Boolean = false,
    ) : QrCodeUiState
}

sealed interface QrCodeEvent {

    /** A refresh failed while a still valid pass stays on the screen. */
    data class RefreshFailed(val error: AppError) : QrCodeEvent
}
