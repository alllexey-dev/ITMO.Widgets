package dev.alllexey.itmowidgets.feature.social.ui.friends

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.text.textResource
import dev.alllexey.itmowidgets.feature.social.presentation.FriendsEvent
import dev.alllexey.itmowidgets.feature.social.presentation.FriendsViewModel
import dev.alllexey.itmowidgets.shared.feature.social.Res
import dev.alllexey.itmowidgets.shared.feature.social.friends_action_failed
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel

/**
 * The viewer's friends and requests with their Koin ViewModel. A failed action shows a snackbar; a remove asks in a
 * confirmation first. Android hosts it in `FriendsFragment`; the iOS shell hosts the same route.
 */
@Composable
fun FriendsRoute(
    onOpenProfile: (Int) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    viewModel: FriendsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    var pendingRemoval by remember { mutableStateOf<FriendsEvent.ConfirmRemove?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    // A snackbar waits until it is gone; the next event (a remove) must not wait behind it.
                    is FriendsEvent.ActionFailed -> launch {
                        snackbars.showSnackbar(
                            message = getString(Res.string.friends_action_failed, getString(event.error.textResource())),
                            duration = SnackbarDuration.Long,
                        )
                    }
                    is FriendsEvent.ConfirmRemove -> pendingRemoval = event
                }
            }
        }
    }

    FriendsScreen(
        state = state,
        onSelectTab = viewModel::selectTab,
        onRefresh = { viewModel.refresh(RefreshMode.Pull) },
        onRetry = { viewModel.refresh(RefreshMode.Force) },
        onAction = viewModel::onAction,
        onOpenProfile = onOpenProfile,
        onOpenSearch = onOpenSearch,
        onOpenSettings = onOpenSettings,
        onBack = onBack,
        pendingRemoval = pendingRemoval,
        onConfirmRemoval = { isu ->
            pendingRemoval = null
            viewModel.removeFriend(isu)
        },
        onDismissRemoval = { pendingRemoval = null },
        snackbarHostState = snackbars,
    )
}
