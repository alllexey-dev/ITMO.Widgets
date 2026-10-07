package dev.alllexey.itmowidgets.feature.update.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.alllexey.itmowidgets.feature.update.presentation.AppUpdateEvent
import dev.alllexey.itmowidgets.feature.update.presentation.AppUpdateViewModel
import dev.alllexey.itmowidgets.shared.feature.account.Res
import dev.alllexey.itmowidgets.shared.feature.account.app_update_open_failed
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel

/**
 * The update offer with its Koin ViewModel. [onUpdate] is the host's distribution-specific update (`UpdateAction` on
 * Android): it gets whether the build is unsupported and a callback for when nothing could be opened, which shows
 * `app_update_open_failed`. The reminder and the close button both call [onClose]; a skipped release closes the
 * screen once the choice is stored. Android hosts it in `AppUpdateFragment`.
 */
@Composable
fun AppUpdateRoute(
    onUpdate: (unsupported: Boolean, onFailed: () -> Unit) -> Unit,
    onClose: () -> Unit,
    viewModel: AppUpdateViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val close by rememberUpdatedState(onClose)

    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    AppUpdateEvent.Skipped -> close()
                }
            }
        }
    }

    AppUpdateScreen(
        state = state,
        onUpdate = {
            onUpdate(state.unsupported) {
                scope.launch {
                    snackbars.showSnackbar(getString(Res.string.app_update_open_failed), duration = SnackbarDuration.Long)
                }
            }
        },
        onLater = onClose,
        onClose = onClose,
        onSkip = viewModel::skipVersion,
        snackbarHostState = snackbars,
    )
}
