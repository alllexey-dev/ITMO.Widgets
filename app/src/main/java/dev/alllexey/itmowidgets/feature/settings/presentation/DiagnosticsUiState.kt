package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.diagnostics.DiagnosticEntry

sealed interface DiagnosticsUiState {
    data object Loading : DiagnosticsUiState
    data class Content(val entries: List<DiagnosticEntry>) : DiagnosticsUiState
}
