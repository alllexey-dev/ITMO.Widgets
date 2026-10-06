package dev.alllexey.itmowidgets.feature.me.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.feature.me.presentation.MeViewModel
import dev.alllexey.itmowidgets.shared.core.Res
import dev.alllexey.itmowidgets.shared.core.link_open_failed
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel

/**
 * The Me tab with its Koin ViewModel. Every start refreshes the social summary silently, since a contextual screen
 * may have changed friends or requests; sign-out goes to the ViewModel; a project link no app could open shows
 * `link_open_failed`. [actions] carries the host's navigation, sharing and links. Android hosts it in `MeFragment`,
 * iOS in a `ComposeUIViewController`.
 */
@Composable
fun MeRoute(actions: MeActions, showDebugTools: Boolean, viewModel: MeViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LifecycleStartEffect(viewModel) {
        viewModel.refresh(RefreshMode.Silent)
        onStopOrDispose { }
    }

    val wired = remember(actions, viewModel) {
        actions.copy(
            onSignOut = viewModel::signOut,
            onOpenProjectLink = { link ->
                actions.onOpenProjectLink(link).also { opened ->
                    if (!opened) {
                        scope.launch {
                            snackbars.showSnackbar(getString(Res.string.link_open_failed), duration = SnackbarDuration.Long)
                        }
                    }
                }
            },
        )
    }
    MeScreen(state, showDebugTools, wired, snackbarHostState = snackbars)
}
