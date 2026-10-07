package dev.alllexey.itmowidgets.feature.settings.ui.diagnostics

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.settings.presentation.DiagnosticsUiState

/*
 * Baselines `DiagnosticsScreen_<state>`, the names LT-3a's XML references took: each state is a function called
 * `DiagnosticsScreen` in a holder class of its own.
 */

@Composable
private fun DiagnosticsPreview(state: DiagnosticsUiState) = ItmoPreview {
    DiagnosticsScreen(state, DiagnosticsActions(), DiagnosticsSamples::timeLabel)
}

internal class DiagnosticsScreenEmptyPreview {
    @Preview(name = "empty")
    @Composable
    fun DiagnosticsScreen() = DiagnosticsPreview(DiagnosticsUiState.Content(emptyList()))
}

internal class DiagnosticsScreenFilledPreview {
    @Preview(name = "filled")
    @Composable
    fun DiagnosticsScreen() = DiagnosticsPreview(DiagnosticsUiState.Content(DiagnosticsSamples.entries))
}

internal class DiagnosticsScreenLoadingPreview {
    @Preview(name = "loading")
    @Composable
    fun DiagnosticsScreen() = DiagnosticsPreview(DiagnosticsUiState.Loading)
}

/** Baseline `DiagnosticsDialog_clear`: the confirmation without its window, on the margin the window keeps. */
internal class DiagnosticsDialogClearPreview {
    @Preview(name = "clear")
    @Composable
    fun DiagnosticsDialog() = ItmoPreview {
        Box(Modifier.padding(ItmoTheme.spacing.section)) {
            DiagnosticsClearDialog(onConfirm = {}, onDismiss = {}, windowed = false)
        }
    }
}
