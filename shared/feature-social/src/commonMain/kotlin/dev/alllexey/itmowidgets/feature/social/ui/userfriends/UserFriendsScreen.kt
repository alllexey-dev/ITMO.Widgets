package dev.alllexey.itmowidgets.feature.social.ui.userfriends

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import dev.alllexey.itmowidgets.core.text.textResource
import dev.alllexey.itmowidgets.designsystem.components.bars.AppTopBar
import dev.alllexey.itmowidgets.designsystem.components.bars.AppTopBarAction
import dev.alllexey.itmowidgets.designsystem.components.state.AppRefreshBox
import dev.alllexey.itmowidgets.designsystem.components.state.ContentState
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateAction
import dev.alllexey.itmowidgets.designsystem.components.state.Skeleton
import dev.alllexey.itmowidgets.designsystem.components.state.SkeletonStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.social.presentation.UserFriendsUiState
import dev.alllexey.itmowidgets.feature.social.ui.list.UserList
import dev.alllexey.itmowidgets.feature.social.ui.list.UserListTestTags
import dev.alllexey.itmowidgets.shared.core.common_back
import dev.alllexey.itmowidgets.shared.core.common_load_error_title
import dev.alllexey.itmowidgets.shared.core.common_retry
import dev.alllexey.itmowidgets.shared.core.settings_title
import dev.alllexey.itmowidgets.shared.designsystem.ic_arrow_back
import dev.alllexey.itmowidgets.shared.designsystem.ic_error
import dev.alllexey.itmowidgets.shared.designsystem.ic_group
import dev.alllexey.itmowidgets.shared.designsystem.ic_lock
import dev.alllexey.itmowidgets.shared.feature.social.Res
import dev.alllexey.itmowidgets.shared.feature.social.friends_disabled_description
import dev.alllexey.itmowidgets.shared.feature.social.friends_disabled_title
import dev.alllexey.itmowidgets.shared.feature.social.friends_title
import dev.alllexey.itmowidgets.shared.feature.social.user_friends_empty_title
import dev.alllexey.itmowidgets.shared.feature.social.user_friends_hidden_description
import dev.alllexey.itmowidgets.shared.feature.social.user_friends_hidden_title
import dev.alllexey.itmowidgets.shared.feature.social.user_friends_owner
import dev.alllexey.itmowidgets.shared.feature.social.user_friends_refresh
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/** Test tags of [UserFriendsScreen]; the list and its rows carry [UserListTestTags]. */
object UserFriendsTestTags {
    const val LOADING = "user_friends_loading"

    /** The empty, denied, disabled or error state in the list's place. */
    const val STATE = "user_friends_state"
}

/**
 * Another user's accepted friends: rows open the person's profile and offer no mutation. The first load shows list
 * placeholders; empty, denied (403, nothing to retry), disabled services (a way to the settings) and errors (a retry)
 * take the list's place. The list follows a pull; failed refreshes of a shown list go to [snackbarHostState].
 * Stateless; [UserFriendsRoute] feeds it.
 */
@Composable
fun UserFriendsScreen(
    state: UserFriendsUiState,
    ownerName: String,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onOpenProfile: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Box(modifier.fillMaxSize().background(ItmoTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize()) {
            AppTopBar(
                title = if (ownerName.isBlank()) {
                    stringResource(Res.string.friends_title)
                } else {
                    stringResource(Res.string.user_friends_owner, ownerName)
                },
                navigation = {
                    AppTopBarAction(
                        painterResource(KitRes.drawable.ic_arrow_back),
                        stringResource(CoreRes.string.common_back),
                        onClick = onBack,
                    )
                },
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                UserFriendsBody(state, onRefresh, onRetry, onOpenProfile, onOpenSettings)
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun UserFriendsBody(
    state: UserFriendsUiState,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onOpenProfile: (Int) -> Unit,
    onOpenSettings: () -> Unit,
) {
    when (state) {
        UserFriendsUiState.Loading ->
            Skeleton(SkeletonStyle.List, Modifier.fillMaxSize().testTag(UserFriendsTestTags.LOADING))
        UserFriendsUiState.Hidden -> FriendsState(
            painterResource(KitRes.drawable.ic_lock),
            stringResource(Res.string.user_friends_hidden_title),
            stringResource(Res.string.user_friends_hidden_description),
        )
        UserFriendsUiState.Disabled -> FriendsState(
            painterResource(KitRes.drawable.ic_lock),
            stringResource(Res.string.friends_disabled_title),
            stringResource(Res.string.friends_disabled_description),
            ContentStateAction(stringResource(CoreRes.string.settings_title), onOpenSettings),
        )
        is UserFriendsUiState.Error -> FriendsState(
            painterResource(KitRes.drawable.ic_error),
            stringResource(CoreRes.string.common_load_error_title),
            stringResource(state.error.textResource()),
            ContentStateAction(stringResource(CoreRes.string.common_retry), onRetry),
        )
        is UserFriendsUiState.Content -> if (state.items.isEmpty()) {
            FriendsState(
                painterResource(KitRes.drawable.ic_group),
                stringResource(Res.string.user_friends_empty_title),
                description = null,
                ContentStateAction(stringResource(Res.string.user_friends_refresh), onRetry),
            )
        } else {
            AppRefreshBox(state.refreshing, onRefresh, Modifier.fillMaxSize()) {
                UserList(state.items, onOpen = { onOpenProfile(it.isu) }, Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun FriendsState(icon: Painter, title: String, description: String?, action: ContentStateAction? = null) {
    ContentState(
        title = title,
        modifier = Modifier.fillMaxSize().testTag(UserFriendsTestTags.STATE),
        icon = icon,
        description = description,
        action = action,
    )
}
