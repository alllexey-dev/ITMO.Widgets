package dev.alllexey.itmowidgets.feature.recordbook.ui.subject

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.text.resolve
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSubjectEvent
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSubjectViewModel

/**
 * The subject page with its ViewModel: [actions] carries the host's navigation, the rest is wired here; a vote that
 * did not reach the server says so in a snackbar.
 */
@Composable
fun RecordbookSubjectRoute(actions: RecordbookSubjectActions, viewModel: RecordbookSubjectViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    is RecordbookSubjectEvent.VoteFailed -> snackbars.showSnackbar(event.error.toUiText().resolve())
                }
            }
        }
    }
    val wired = remember(actions, viewModel) {
        actions.copy(
            onRefresh = { viewModel.refresh(RefreshMode.Pull) },
            onRetry = { viewModel.refresh(RefreshMode.Force) },
            onSelectTab = viewModel::selectTab,
            onShowAllLessons = viewModel::showAllLessons,
            onConfirmBinding = viewModel::confirmBinding,
            onRejectBinding = viewModel::rejectBinding,
            onRetryLessons = viewModel::retryLessons,
            onVote = viewModel::voteLink,
            onDisconnectSheet = viewModel::disconnectSheet,
        )
    }
    RecordbookSubjectScreen(state, wired, snackbars = snackbars)
}
