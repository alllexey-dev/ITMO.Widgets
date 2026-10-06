package dev.alllexey.itmowidgets.feature.home.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.alllexey.itmowidgets.core.home.HomeCardRenderer
import dev.alllexey.itmowidgets.core.home.HomeHint
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.feature.home.presentation.HomeEvent
import dev.alllexey.itmowidgets.feature.home.presentation.HomeViewModel
import dev.alllexey.itmowidgets.shared.core.Res
import dev.alllexey.itmowidgets.shared.core.common_partial_load_error
import dev.alllexey.itmowidgets.shared.core.common_retry
import org.jetbrains.compose.resources.getString
import org.koin.compose.getKoin
import org.koin.compose.viewmodel.koinViewModel

/**
 * The home feed with its Koin ViewModel. The first show refreshes once, every resume revalidates (and refreshes a
 * stale feed), and a partly failed refresh shows `Часть данных не загрузилась` with `Повторить` while the screen is
 * resumed. [actions] carries the host's navigation and platform actions; dismissing and refreshing are wired here.
 * The cards are drawn by every feature's Koin `HomeCardRenderer`. Android hosts it in `HomeFragment`.
 */
@Composable
fun HomeRoute(
    actions: HomeActions,
    viewModel: HomeViewModel = koinViewModel(),
    renderers: List<HomeCardRenderer> = koinHomeCardRenderers(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(viewModel) { viewModel.ensureDataLoaded() }
    LifecycleResumeEffect(viewModel) {
        viewModel.onScreenResumed()
        onPauseOrDispose { }
    }
    LaunchedEffect(viewModel, lifecycle) {
        // Resumed only: leaving the screen cancels the snackbar with its coroutine.
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.events.collect { event ->
                when (event) {
                    is HomeEvent.RefreshFailed -> {
                        val result = snackbars.showSnackbar(
                            message = getString(Res.string.common_partial_load_error),
                            actionLabel = getString(Res.string.common_retry),
                            duration = SnackbarDuration.Long,
                        )
                        if (result == SnackbarResult.ActionPerformed) viewModel.refresh(RefreshMode.Pull)
                    }
                }
            }
        }
    }

    val wired = remember(actions, viewModel) {
        actions.copy(
            onRefresh = { viewModel.refresh(RefreshMode.Pull) },
            onDismiss = { kind -> HomeHint.of(kind)?.let(viewModel::dismissHint) ?: viewModel.dismissCard(kind) },
        )
    }
    HomeScreen(state = state, actions = wired, renderers = renderers, snackbarHostState = snackbars)
}

/** Every feature's renderer of home cards in the Koin graph, read once per graph. */
@Composable
private fun koinHomeCardRenderers(): List<HomeCardRenderer> {
    val koin = getKoin()
    return remember(koin) { koin.getAll<HomeCardRenderer>() }
}
