package dev.alllexey.itmowidgets.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.core.diagnostics.AppDiagnostics
import dev.alllexey.itmowidgets.core.diagnostics.DiagnosticEntry
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface DiagnosticsUiState {
    data object Loading : DiagnosticsUiState
    data class Content(val entries: List<DiagnosticEntry>) : DiagnosticsUiState
}

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val diagnostics: AppDiagnostics,
    timeProvider: AcademicTimeProvider
) : ViewModel() {

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(timeProvider.zoneId)

    val uiState: StateFlow<DiagnosticsUiState> = diagnostics.observe()
        .map<List<DiagnosticEntry>, DiagnosticsUiState> { DiagnosticsUiState.Content(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiagnosticsUiState.Loading)

    fun clear() {
        viewModelScope.launch { diagnostics.clear() }
    }

    fun formatTime(entry: DiagnosticEntry): String = formatter.format(entry.at)

    /** Plain text for the clipboard: newest first, one block per entry. */
    fun exportText(): String {
        val entries = (uiState.value as? DiagnosticsUiState.Content)?.entries.orEmpty()
        return entries.joinToString("\n\n") { entry -> entry.asText(formatTime(entry)) }
    }
}
