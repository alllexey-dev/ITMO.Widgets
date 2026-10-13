package dev.alllexey.itmowidgets.feature.recordbook.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookViewModel

/**
 * Зачётка with the tab's ViewModel: the first show loads once, coming back keeps the list. [actions] carries the host's
 * navigation and the BARS sign-in; the rest is wired here.
 */
@Composable
fun RecordbookRoute(title: String, actions: RecordbookActions, viewModel: RecordbookViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.refresh(RefreshMode.Silent) }
    val wired = remember(actions, viewModel) {
        actions.copy(
            onRefresh = { viewModel.refresh(RefreshMode.Pull) },
            onRetry = { viewModel.refresh(RefreshMode.Force) },
            onSelectPeriod = viewModel::selectPeriod,
            onSelectView = viewModel::selectView,
            onHideOffer = viewModel::hideBarsOffer,
            onOpenSources = viewModel::openSources,
            onCloseSources = viewModel::closeSources,
        )
    }
    RecordbookScreen(title, state, wired)
}
