package dev.alllexey.itmowidgets.feature.debug.presentation

import dev.alllexey.itmowidgets.core.debug.SportScoreOverride
import dev.alllexey.itmowidgets.core.result.AppError
import kotlinx.datetime.LocalDate

sealed interface DebugToolsUiState {
    data class Content(
        val effectiveDate: LocalDate,
        val dateOverride: LocalDate?,
        val scoreOverride: SportScoreOverride?,
        val lessonTemplatesEnabled: Boolean,
        val refreshTokenConfigured: Boolean,
        val refreshTokenUpdateInProgress: Boolean,
        val customServicesEnabled: Boolean
    ) : DebugToolsUiState
}

sealed interface DebugToolsEvent {
    data object RecreateActivity : DebugToolsEvent
    data object RefreshTokenUpdated : DebugToolsEvent
    data class RefreshTokenUpdateFailed(val error: AppError) : DebugToolsEvent
}
