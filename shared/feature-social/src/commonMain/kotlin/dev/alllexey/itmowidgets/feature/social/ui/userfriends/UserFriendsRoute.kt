package dev.alllexey.itmowidgets.feature.social.ui.userfriends

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.text.textResource
import dev.alllexey.itmowidgets.feature.social.presentation.UserFriendsEvent
import dev.alllexey.itmowidgets.feature.social.presentation.UserFriendsViewModel
import dev.alllexey.itmowidgets.shared.core.Res
import dev.alllexey.itmowidgets.shared.core.common_retry
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel

/**
 * Another user's friends with their Koin ViewModel, which reads the owner's ISU and name from the destination's
 * arguments. A failed refresh of a shown list offers a retry in a snackbar. Android hosts it in
 * `UserFriendsFragment`; the iOS shell hosts the same route.
 */
@Composable
fun UserFriendsRoute(
    onOpenProfile: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    viewModel: UserFriendsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    is UserFriendsEvent.RefreshFailed -> {
                        val result = snackbars.showSnackbar(
                            message = getString(event.error.textResource()),
                            actionLabel = getString(Res.string.common_retry),
                            duration = SnackbarDuration.Long,
                        )
                        if (result == SnackbarResult.ActionPerformed) viewModel.refresh(RefreshMode.Force)
                    }
                }
            }
        }
    }

    UserFriendsScreen(
        state = state,
        ownerName = viewModel.name,
        onRefresh = { viewModel.refresh(RefreshMode.Pull) },
        onRetry = { viewModel.refresh(RefreshMode.Force) },
        onOpenProfile = onOpenProfile,
        onOpenSettings = onOpenSettings,
        onBack = onBack,
        snackbarHostState = snackbars,
    )
}
