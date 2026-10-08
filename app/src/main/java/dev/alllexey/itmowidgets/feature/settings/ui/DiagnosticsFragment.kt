package dev.alllexey.itmowidgets.feature.settings.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.ui.copyToClipboard
import dev.alllexey.itmowidgets.core.ui.navigation.closeScreen
import dev.alllexey.itmowidgets.designsystem.host.itmoComposeView
import dev.alllexey.itmowidgets.feature.settings.presentation.DiagnosticsViewModel
import dev.alllexey.itmowidgets.feature.settings.ui.diagnostics.DiagnosticsActions
import dev.alllexey.itmowidgets.feature.settings.ui.diagnostics.DiagnosticsScreen
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * «Журнал ошибок» (`@id/diagnostics`): `DiagnosticsScreen` of `:shared:feature-settings` over the Koin
 * `DiagnosticsViewModel`. The host leaves the screen and puts the journal on the clipboard; the screen asks before
 * clearing.
 */
@AndroidEntryPoint
class DiagnosticsFragment : Fragment() {

    private val viewModel: DiagnosticsViewModel by viewModel()

    private val snackbars = SnackbarHostState()

    private val actions = DiagnosticsActions(
        onBack = { closeScreen() },
        onCopy = { copyJournal() },
        onClear = { viewModel.clear() },
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        itmoComposeView {
            val state by viewModel.uiState.collectAsState()
            DiagnosticsScreen(state, actions, viewModel::formatTime, snackbarHostState = snackbars)
        }

    private fun copyJournal() {
        requireContext().copyDiagnosticsJournal(viewModel.exportText()) { message ->
            viewLifecycleOwner.lifecycleScope.launch { snackbars.showSnackbar(message) }
        }
    }
}

/**
 * Puts the [journal] on the clipboard. Android 13 and later confirm a copy themselves; before that [onCopied] gets
 * the screen's own message.
 */
internal fun Context.copyDiagnosticsJournal(journal: String, onCopied: (message: String) -> Unit) {
    copyToClipboard(getString(R.string.diagnostics_title), journal) { onCopied(getString(R.string.diagnostics_copied)) }
}
