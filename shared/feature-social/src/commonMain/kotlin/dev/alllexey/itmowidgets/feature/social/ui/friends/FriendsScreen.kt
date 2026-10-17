package dev.alllexey.itmowidgets.feature.social.ui.friends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButton
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButtonStyle
import dev.alllexey.itmowidgets.designsystem.components.state.AppRefreshBox
import dev.alllexey.itmowidgets.designsystem.components.state.ContentState
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateAction
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateActionStyle
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateLoading
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateSize
import dev.alllexey.itmowidgets.designsystem.kit.GateCard
import dev.alllexey.itmowidgets.designsystem.kit.ItmoDetailScreen
import dev.alllexey.itmowidgets.designsystem.kit.ListGroup
import dev.alllexey.itmowidgets.designsystem.kit.ListRow
import dev.alllexey.itmowidgets.designsystem.kit.SearchField
import dev.alllexey.itmowidgets.designsystem.kit.Tone
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.social.presentation.FriendLists
import dev.alllexey.itmowidgets.feature.social.presentation.FriendsUiState
import dev.alllexey.itmowidgets.feature.social.presentation.PeopleSearchResults
import dev.alllexey.itmowidgets.feature.social.presentation.UserAction
import dev.alllexey.itmowidgets.feature.social.presentation.UserRowUi
import dev.alllexey.itmowidgets.feature.social.ui.list.UserListTestTags
import dev.alllexey.itmowidgets.feature.social.ui.text
import dev.alllexey.itmowidgets.feature.social.ui.list.personGroup
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_error
import dev.alllexey.itmowidgets.shared.designsystem.ic_group
import dev.alllexey.itmowidgets.shared.designsystem.ic_search
import dev.alllexey.itmowidgets.shared.designsystem.ic_share
import dev.alllexey.itmowidgets.shared.feature.social.SocialStrings
import org.jetbrains.compose.resources.painterResource

/** What Друзья asks of its route and host. */
class FriendsActions(
    val onBack: () -> Unit = {},
    val onQueryChange: (String) -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onRetrySearch: () -> Unit = {},
    val onLoadMore: () -> Unit = {},
    val onAction: (UserRowUi, UserAction) -> Unit = { _, _ -> },
    val onOpenProfile: (Int) -> Unit = {},
    /** The gate's `Подключить`: the connection settings. */
    val onConnect: () -> Unit = {},
    /** `Поделиться профилем` with the viewer's name and ISU. */
    val onShareProfile: (name: String, isu: Int) -> Unit = { _, _ -> },
)

object FriendsTestTags {
    const val LIST = "friends_list"
    const val SEARCH = "friends_search"
    const val SHARE = "friends_share"
    const val GATE = "friends_gate"
    const val STATE = "friends_state"
}

/**
 * Друзья (DECISIONS.md): the search field on top; under it the incoming requests with `Принять` and `Отклонить` in the
 * row, the sent ones with `Отменить`, then the friends, who open their card (removing a friend lives there), and
 * `Поделиться профилем`. A typed query shows the people found in place of the lists. Without the connection the gate
 * says why; an empty screen leads to the search and the link. Stateless; [FriendsRoute] feeds it.
 */
@Composable
fun FriendsScreen(
    state: FriendsUiState,
    actions: FriendsActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val lists = state.lists
    ItmoDetailScreen(
        title = text(SocialStrings.friends_title),
        onBack = actions.onBack,
        backLabel = text(CoreStrings.common_back),
        modifier = modifier,
        subtitle = (lists as? FriendLists.Content)?.subtitle?.asString(),
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (lists == FriendLists.Disabled) {
                Column(Modifier.padding(ItmoTheme.spacing.screenMargin)) {
                    GateCard(
                        title = text(SocialStrings.friends_disabled_title),
                        reason = text(SocialStrings.friends_disabled_description),
                        fix = text(SocialStrings.friends_connect),
                        onFix = actions.onConnect,
                        modifier = Modifier.testTag(FriendsTestTags.GATE),
                        icon = painterResource(Res.drawable.ic_group),
                    )
                }
            } else {
                val refreshing = (lists as? FriendLists.Content)?.refreshing == true
                AppRefreshBox(refreshing, actions.onRefresh, Modifier.fillMaxSize()) {
                    Body(state, actions)
                }
            }
            SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun Body(state: FriendsUiState, actions: FriendsActions) {
    val open = { row: UserRowUi -> actions.onOpenProfile(row.isu) }
    LazyColumn(
        Modifier.fillMaxSize().testTag(FriendsTestTags.LIST),
        contentPadding = PaddingValues(
            start = ItmoTheme.spacing.screenMargin,
            end = ItmoTheme.spacing.screenMargin,
            bottom = ItmoTheme.spacing.section,
        ),
        verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
    ) {
        item(key = "search", contentType = "search") {
            SearchField(
                query = state.query,
                onQueryChange = actions.onQueryChange,
                placeholder = text(SocialStrings.user_search_hint),
                clearLabel = text(SocialStrings.user_search_clear),
                modifier = Modifier.testTag(FriendsTestTags.SEARCH),
            )
        }
        val search = state.search
        if (search != null) {
            searchResults(search, actions, open)
        } else {
            lists(state, actions, open)
        }
    }
}

private fun LazyListScope.searchResults(search: PeopleSearchResults, actions: FriendsActions, open: (UserRowUi) -> Unit) {
    when (search) {
        PeopleSearchResults.Loading -> item(key = "search:loading") { ContentStateLoading(size = ContentStateSize.Compact) }
        PeopleSearchResults.Empty -> item(key = "search:empty") {
            ContentState(
                title = text(SocialStrings.user_search_empty_title),
                modifier = Modifier.testTag(FriendsTestTags.STATE),
                size = ContentStateSize.Compact,
                icon = painterResource(Res.drawable.ic_search),
            )
        }
        is PeopleSearchResults.Error -> item(key = "search:error") {
            ContentState(
                title = text(CoreStrings.common_load_error_title),
                modifier = Modifier.testTag(FriendsTestTags.STATE),
                size = ContentStateSize.Compact,
                icon = painterResource(Res.drawable.ic_error),
                description = search.error.toUiText().asString(),
                action = ContentStateAction(text(CoreStrings.common_retry), actions.onRetrySearch),
            )
        }
        is PeopleSearchResults.Content -> {
            personGroup("registered", search.registered, open, actions.onAction, UiText.Res(SocialStrings.user_search_section_registered))
            personGroup("others", search.others, open, actions.onAction, UiText.Res(SocialStrings.user_search_section_others))
            if (search.offersMore || search.loadingMore) {
                item(key = "search:more") {
                    ProgressButton(
                        label = text(SocialStrings.user_search_load_more),
                        onClick = actions.onLoadMore,
                        modifier = Modifier.fillMaxWidth().testTag(UserListTestTags.LOAD_MORE),
                        inProgress = search.loadingMore,
                        style = ProgressButtonStyle.Text,
                    )
                }
            }
        }
    }
}

private fun LazyListScope.lists(state: FriendsUiState, actions: FriendsActions, open: (UserRowUi) -> Unit) {
    when (val lists = state.lists) {
        FriendLists.Loading, FriendLists.Disabled -> item(key = "loading") { ContentStateLoading(size = ContentStateSize.Compact) }
        is FriendLists.Error -> item(key = "error") {
            ContentState(
                title = text(CoreStrings.common_load_error_title),
                modifier = Modifier.testTag(FriendsTestTags.STATE),
                size = ContentStateSize.Compact,
                icon = painterResource(Res.drawable.ic_error),
                description = lists.error.toUiText().asString(),
                action = ContentStateAction(text(CoreStrings.common_retry), actions.onRetry),
            )
        }
        is FriendLists.Content -> if (lists.isEmpty) {
            item(key = "empty") {
                val viewer = state.viewer
                ContentState(
                    title = text(SocialStrings.friends_empty_title),
                    modifier = Modifier.testTag(FriendsTestTags.STATE),
                    size = ContentStateSize.Compact,
                    icon = painterResource(Res.drawable.ic_group),
                    description = text(SocialStrings.friends_empty_description),
                    action = viewer?.let {
                        ContentStateAction(
                            text(SocialStrings.friends_share_title),
                            { actions.onShareProfile(it.name, it.isu) },
                            ContentStateActionStyle.Filled,
                        )
                    },
                )
            }
        } else {
            personGroup(
                "requests",
                lists.incoming + lists.outgoing,
                open,
                actions.onAction,
                UiText.Res(SocialStrings.friends_tab_requests),
            )
            personGroup(
                "friends",
                lists.friends,
                open,
                actions.onAction,
                UiText.Res(SocialStrings.friends_tab_friends),
                count = lists.friends.size.toString(),
            )
            state.viewer?.let { viewer ->
                item(key = "share") {
                    ListGroup(Modifier.padding(top = ItmoTheme.spacing.content)) {
                        item {
                            ListRow(
                                text(SocialStrings.friends_share_title),
                                Modifier.testTag(FriendsTestTags.SHARE),
                                subtitle = text(SocialStrings.friends_share_subtitle),
                                icon = painterResource(Res.drawable.ic_share),
                                iconTone = Tone.Primary,
                                chevron = true,
                                onClick = { actions.onShareProfile(viewer.name, viewer.isu) },
                            )
                        }
                    }
                }
            }
        }
    }
}
