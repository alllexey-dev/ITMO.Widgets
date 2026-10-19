package dev.alllexey.itmowidgets.feature.me.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.alllexey.itmowidgets.feature.me.presentation.MeViewModel
import dev.alllexey.itmowidgets.shared.compose.strings.Res
import dev.alllexey.itmowidgets.shared.compose.strings.link_open_failed
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel

/**
 * The Me tab with its Koin ViewModel, which refreshes the social summary on its first show (a return from a pushed
 * screen does not: the screens there fold their changes into the repository); sign-out goes to the ViewModel; a
 * project link no app could open shows `link_open_failed`. [actions] carries the host's navigation, sharing and
 * links. Android hosts it in the Compose shell's Me tab.
 */
@Composable
fun MeRoute(actions: MeActions, showDebugTools: Boolean, viewModel: MeViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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
