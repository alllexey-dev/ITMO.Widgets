package dev.alllexey.itmowidgets.feature.social.ui.userfriends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.designsystem.components.state.AppRefreshBox
import dev.alllexey.itmowidgets.designsystem.components.state.ContentState
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateAction
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateLoading
import dev.alllexey.itmowidgets.designsystem.kit.ItmoDetailScreen
import dev.alllexey.itmowidgets.designsystem.kit.ItmoSnackbarHost
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.social.presentation.UserFriendsUiState
import dev.alllexey.itmowidgets.feature.social.ui.list.userList
import dev.alllexey.itmowidgets.feature.social.ui.text
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_error
import dev.alllexey.itmowidgets.shared.designsystem.ic_group
import dev.alllexey.itmowidgets.shared.feature.social.SocialStrings
import org.jetbrains.compose.resources.painterResource

/** Test tags of [UserFriendsScreen]; its rows carry `UserListTestTags`. */
object UserFriendsTestTags {
    const val LIST = "user_friends_list"
    const val LOADING = "user_friends_loading"

    /** The empty, disabled or error state in the list's place. */
    const val STATE = "user_friends_state"
}

/**
 * Another user's accepted friends under `Друзья` and the owner's name: rows open the person's card and offer no
 * mutation. Hidden friends (403) are simply absent, as any of someone else's private data; without the connection
 * the screen leads to it; an error offers a retry. The list follows a pull; failed refreshes of a shown list go to
 * [snackbarHostState]. Stateless; [UserFriendsRoute] feeds it.
 */
@Composable
fun UserFriendsScreen(
    state: UserFriendsUiState,
    ownerName: String,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onOpenProfile: (Int) -> Unit,
    onConnect: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    ItmoDetailScreen(
        title = text(SocialStrings.friends_title),
        onBack = onBack,
        backLabel = text(CoreStrings.common_back),
        modifier = modifier,
        subtitle = ownerName.takeIf { it.isNotBlank() },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                UserFriendsUiState.Loading -> ContentStateLoading(Modifier.testTag(UserFriendsTestTags.LOADING))
                UserFriendsUiState.Hidden -> Empty(painterResource(Res.drawable.ic_group), text(SocialStrings.user_friends_empty_title))
                UserFriendsUiState.Disabled -> Empty(
                    painterResource(Res.drawable.ic_group),
                    text(SocialStrings.friends_disabled_title),
                    text(SocialStrings.friends_disabled_description),
                    ContentStateAction(text(SocialStrings.friends_connect), onConnect),
                )
                is UserFriendsUiState.Error -> Empty(
                    painterResource(Res.drawable.ic_error),
                    text(CoreStrings.common_load_error_title),
                    state.error.toUiText().asString(),
                    ContentStateAction(text(CoreStrings.common_retry), onRetry),
                )
                is UserFriendsUiState.Content -> if (state.items.isEmpty()) {
                    Empty(
                        painterResource(Res.drawable.ic_group),
                        text(SocialStrings.user_friends_empty_title),
                        action = ContentStateAction(text(SocialStrings.user_friends_refresh), onRetry),
                    )
                } else {
                    AppRefreshBox(state.refreshing, onRefresh, Modifier.fillMaxSize()) {
                        LazyColumn(
                            Modifier.fillMaxSize().testTag(UserFriendsTestTags.LIST),
                            contentPadding = PaddingValues(
                                start = ItmoTheme.spacing.screenMargin,
                                end = ItmoTheme.spacing.screenMargin,
                                bottom = ItmoTheme.spacing.section,
                            ),
                            verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
                        ) {
                            userList(state.items, onOpen = { onOpenProfile(it.isu) })
                        }
                    }
                }
            }
            ItmoSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun Empty(icon: Painter, title: String, description: String? = null, action: ContentStateAction? = null) {
    ContentState(
        title = title,
        modifier = Modifier.fillMaxSize().testTag(UserFriendsTestTags.STATE),
        icon = icon,
        description = description,
        action = action,
    )
}
