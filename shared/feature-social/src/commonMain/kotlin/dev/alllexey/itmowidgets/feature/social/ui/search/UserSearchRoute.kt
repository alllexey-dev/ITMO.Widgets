package dev.alllexey.itmowidgets.feature.social.ui.search

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.text.textResource
import dev.alllexey.itmowidgets.feature.social.presentation.UserSearchEvent
import dev.alllexey.itmowidgets.feature.social.presentation.UserSearchViewModel
import dev.alllexey.itmowidgets.shared.feature.social.Res
import dev.alllexey.itmowidgets.shared.feature.social.friends_action_failed
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel

/**
 * People search with its Koin ViewModel. The typed text survives recreation here and is handed back to a fresh
 * ViewModel once, so a restored screen searches its query again. A failed action shows a snackbar; `Пригласить`
 * leaves through [onInvite], where the host shares the invitation text with the download link of its distribution.
 * Android hosts it in `UserSearchFragment`; the iOS shell hosts the same route.
 */
@Composable
fun UserSearchRoute(
    onOpenProfile: (Int) -> Unit,
    onInvite: () -> Unit,
    onBack: () -> Unit,
    viewModel: UserSearchViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val snackbars = remember { SnackbarHostState() }
    val invite by rememberUpdatedState(onInvite)
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // The ViewModel ignores a query it already holds, so a configuration change does not search again.
    LaunchedEffect(viewModel) { if (query.isNotBlank()) viewModel.onQueryChanged(query) }

    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    // A snackbar waits until it is gone; an invite must not wait behind it.
                    is UserSearchEvent.ActionFailed -> launch {
                        snackbars.showSnackbar(
                            message = getString(Res.string.friends_action_failed, getString(event.error.textResource())),
                            duration = SnackbarDuration.Long,
                        )
                    }
                    is UserSearchEvent.Invite -> invite()
                }
            }
        }
    }

    UserSearchScreen(
        query = query,
        state = state,
        onQueryChange = {
            query = it
            viewModel.onQueryChanged(it)
        },
        onRetry = { viewModel.refresh(RefreshMode.Force) },
        onAction = viewModel::onAction,
        onLoadMore = viewModel::loadMore,
        onOpenProfile = onOpenProfile,
        onBack = onBack,
        requestFocus = true,
        snackbarHostState = snackbars,
    )
}
