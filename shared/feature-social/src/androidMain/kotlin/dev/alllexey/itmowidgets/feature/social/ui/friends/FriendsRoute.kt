package dev.alllexey.itmowidgets.feature.social.ui.friends

import androidx.compose.material3.SnackbarDuration
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
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.resolve
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.feature.social.presentation.FriendsEvent
import dev.alllexey.itmowidgets.feature.social.presentation.FriendsViewModel
import dev.alllexey.itmowidgets.shared.feature.social.SocialStrings
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/** Where Друзья leaves to; every exit is the host's. */
class FriendsExits(
    val onBack: () -> Unit = {},
    val onOpenProfile: (Int) -> Unit = {},
    val onConnect: () -> Unit = {},
    val onShareProfile: (name: String, isu: Int) -> Unit = { _, _ -> },
    /** `Пригласить` for someone without ITMO.Widgets. */
    val onInvite: () -> Unit = {},
)

/** Друзья with its Koin ViewModel; a failed action shows a snackbar. Android hosts it in the Compose shell. */
@Composable
fun FriendsRoute(exits: FriendsExits, viewModel: FriendsViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    is FriendsEvent.ActionFailed -> launch {
                        val reason = event.error.toUiText().resolve()
                        snackbars.showSnackbar(
                            message = UiText.Res(SocialStrings.friends_action_failed, listOf(reason)).resolve(),
                            duration = SnackbarDuration.Long,
                        )
                    }
                    is FriendsEvent.Invite -> exits.onInvite()
                }
            }
        }
    }

    FriendsScreen(
        state = state,
        actions = FriendsActions(
            onBack = exits.onBack,
            onQueryChange = viewModel::onQueryChanged,
            onRefresh = { viewModel.refresh(RefreshMode.Pull) },
            onRetry = { viewModel.refresh(RefreshMode.Force) },
            onRetrySearch = viewModel::retrySearch,
            onLoadMore = viewModel::loadMore,
            onAction = viewModel::onAction,
            onOpenProfile = exits.onOpenProfile,
            onConnect = exits.onConnect,
            onShareProfile = exits.onShareProfile,
        ),
        snackbarHostState = snackbars,
    )
}
