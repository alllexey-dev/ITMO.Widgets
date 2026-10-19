package dev.alllexey.itmowidgets.feature.home.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.feature.home.presentation.TodayViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Сегодня with its Koin ViewModel: the first show refreshes once; the ViewModel itself refreshes stale data when the
 * app comes back to the foreground. [actions] carries the host's navigation; refreshing is wired here.
 */
@Composable
fun TodayRoute(actions: TodayActions, viewModel: TodayViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.ensureDataLoaded() }
    val wired = remember(actions, viewModel) {
        actions.copy(
            onRefresh = { viewModel.refresh(RefreshMode.Pull) },
            onRetry = { viewModel.refresh(RefreshMode.Force) },
        )
    }
    TodayScreen(state, wired)
}
