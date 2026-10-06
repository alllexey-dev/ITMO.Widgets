package dev.alllexey.itmowidgets.feature.social.ui.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.core.text.textResource
import dev.alllexey.itmowidgets.designsystem.components.bars.AppTopBar
import dev.alllexey.itmowidgets.designsystem.components.bars.AppTopBarAction
import dev.alllexey.itmowidgets.designsystem.components.dialogs.ConfirmDialog
import dev.alllexey.itmowidgets.designsystem.components.state.AppRefreshBox
import dev.alllexey.itmowidgets.designsystem.components.state.ContentState
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateAction
import dev.alllexey.itmowidgets.designsystem.components.state.Skeleton
import dev.alllexey.itmowidgets.designsystem.components.state.SkeletonStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.social.presentation.FriendsEmpty
import dev.alllexey.itmowidgets.feature.social.presentation.FriendsEvent
import dev.alllexey.itmowidgets.feature.social.presentation.FriendsTab
import dev.alllexey.itmowidgets.feature.social.presentation.FriendsUiState
import dev.alllexey.itmowidgets.feature.social.presentation.UserAction
import dev.alllexey.itmowidgets.feature.social.presentation.UserRowUi
import dev.alllexey.itmowidgets.feature.social.ui.list.UserList
import dev.alllexey.itmowidgets.feature.social.ui.list.UserListTestTags
import dev.alllexey.itmowidgets.shared.core.common_back
import dev.alllexey.itmowidgets.shared.core.common_cancel
import dev.alllexey.itmowidgets.shared.core.common_load_error_title
import dev.alllexey.itmowidgets.shared.core.common_retry
import dev.alllexey.itmowidgets.shared.core.settings_title
import dev.alllexey.itmowidgets.shared.designsystem.ic_arrow_back
import dev.alllexey.itmowidgets.shared.designsystem.ic_error
import dev.alllexey.itmowidgets.shared.designsystem.ic_group
import dev.alllexey.itmowidgets.shared.designsystem.ic_how_to_reg
import dev.alllexey.itmowidgets.shared.designsystem.ic_lock
import dev.alllexey.itmowidgets.shared.designsystem.ic_search
import dev.alllexey.itmowidgets.shared.feature.social.Res
import dev.alllexey.itmowidgets.shared.feature.social.friends_disabled_description
import dev.alllexey.itmowidgets.shared.feature.social.friends_disabled_title
import dev.alllexey.itmowidgets.shared.feature.social.friends_empty_description
import dev.alllexey.itmowidgets.shared.feature.social.friends_empty_title
import dev.alllexey.itmowidgets.shared.feature.social.friends_find_people
import dev.alllexey.itmowidgets.shared.feature.social.friends_remove_confirm_message
import dev.alllexey.itmowidgets.shared.feature.social.friends_remove_confirm_title
import dev.alllexey.itmowidgets.shared.feature.social.friends_requests_empty_title
import dev.alllexey.itmowidgets.shared.feature.social.friends_tab_friends
import dev.alllexey.itmowidgets.shared.feature.social.friends_tab_requests
import dev.alllexey.itmowidgets.shared.feature.social.friends_title
import dev.alllexey.itmowidgets.shared.feature.social.user_action_remove
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/** Test tags of [FriendsScreen]; the list and its rows carry [UserListTestTags]. */
object FriendsTestTags {
    const val LOADING = "friends_loading"

    /** The empty, disabled or error state in the list's place. */
    const val STATE = "friends_state"
    const val TABS = "friends_tabs"
    const val BADGE = "friends_requests_badge"

    fun tab(tab: FriendsTab): String = "friends_tab_${tab.name.lowercase()}"
}

/**
 * The viewer's own friends and requests. `Друзья` lists friends with a remove button, `Заявки` the incoming requests
 * (accept, reject) over the outgoing ones (cancel), and carries the count of incoming requests in a badge beside its
 * label. A row in flight keeps its buttons inert; failed actions go to [snackbarHostState]. Removing asks first: while
 * [pendingRemoval] is set the screen shows the confirmation, which answers through [onConfirmRemoval] or
 * [onDismissRemoval].
 *
 * The first load shows list placeholders; afterwards a reload keeps the list and only a pull shows the indicator.
 * An empty tab says so (`Друзья` with a way to people search), disabled services offer the settings and an error a
 * retry. Stateless apart from the list kept over a reload; [FriendsRoute] feeds it.
 */
@Composable
fun FriendsScreen(
    state: FriendsUiState,
    onSelectTab: (FriendsTab) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onAction: (UserRowUi, UserAction) -> Unit,
    onOpenProfile: (Int) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    pendingRemoval: FriendsEvent.ConfirmRemove? = null,
    onConfirmRemoval: (Int) -> Unit = {},
    onDismissRemoval: () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val shown = rememberShownState(state)
    Box(modifier.fillMaxSize().background(ItmoTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize()) {
            AppTopBar(
                title = stringResource(Res.string.friends_title),
                navigation = {
                    AppTopBarAction(
                        painterResource(KitRes.drawable.ic_arrow_back),
                        stringResource(CoreRes.string.common_back),
                        onClick = onBack,
                    )
                },
                actions = {
                    AppTopBarAction(
                        painterResource(KitRes.drawable.ic_search),
                        stringResource(Res.string.friends_find_people),
                        onClick = onOpenSearch,
                    )
                },
            )
            if (shown is FriendsUiState.Content) FriendsTabs(shown.tab, shown.incomingCount, onSelectTab)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                FriendsBody(shown, onRefresh, onRetry, onAction, onOpenProfile, onOpenSearch, onOpenSettings)
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
    pendingRemoval?.let { removal -> RemoveConfirmation(removal, onConfirmRemoval, onDismissRemoval) }
}

/** The list on screen stays while the repository reloads; only an empty screen shows the placeholders. */
@Composable
private fun rememberShownState(state: FriendsUiState): FriendsUiState {
    var kept by remember { mutableStateOf<FriendsUiState.Content?>(null) }
    return when (state) {
        is FriendsUiState.Content -> state.also { kept = it }
        FriendsUiState.Loading -> kept ?: state
        else -> state.also { kept = null }
    }
}

/** `TabLayout` in fixed mode inside the 16 dp margins; the badge sits beside «Заявки», never over it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FriendsTabs(selected: FriendsTab, incomingCount: Int, onSelectTab: (FriendsTab) -> Unit) {
    PrimaryTabRow(
        selectedTabIndex = selected.ordinal,
        modifier = Modifier.padding(horizontal = ItmoTheme.spacing.group).testTag(FriendsTestTags.TABS),
        containerColor = ItmoTheme.colorScheme.surface,
        indicator = {
            TabRowDefaults.PrimaryIndicator(
                Modifier.tabIndicatorOffset(selected.ordinal, matchContentSize = true),
                width = Dp.Unspecified,
            )
        },
    ) {
        FriendsTab.entries.forEach { tab ->
            Tab(
                selected = tab == selected,
                onClick = { onSelectTab(tab) },
                modifier = Modifier.testTag(FriendsTestTags.tab(tab)),
                selectedContentColor = ItmoTheme.colorScheme.primary,
                unselectedContentColor = ItmoTheme.colorScheme.onSurfaceVariant,
                text = { TabLabel(tab, if (tab == FriendsTab.REQUESTS) incomingCount else 0) },
            )
        }
    }
}

@Composable
private fun TabLabel(tab: FriendsTab, badgeCount: Int) {
    val label = stringResource(
        when (tab) {
            FriendsTab.FRIENDS -> Res.string.friends_tab_friends
            FriendsTab.REQUESTS -> Res.string.friends_tab_requests
        },
    )
    if (badgeCount <= 0) {
        Text(label)
        return
    }
    val count = badgeCount.badgeText()
    Layout(
        content = {
            Text(label)
            Badge(Modifier.testTag(FriendsTestTags.BADGE).semantics { contentDescription = count }) { Text(count) }
        },
    ) { measurables, constraints ->
        val text = measurables[0].measure(constraints)
        val badge = measurables[1].measure(Constraints())
        // The label keeps its own size, so it stays centred in the tab; the badge hangs off its end, raised above
        // the line like `BadgeDrawable` with `horizontalOffsetWithText = 0`.
        layout(text.width, text.height) {
            text.place(0, 0)
            badge.place(text.width, -BadgeRaise.roundToPx())
        }
    }
}

/** `BadgeDrawable`'s default cap of four characters: up to 999, then `999+`. */
private fun Int.badgeText(): String = if (this > MAX_BADGE_NUMBER) "$MAX_BADGE_NUMBER+" else toString()

@Composable
private fun FriendsBody(
    state: FriendsUiState,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onAction: (UserRowUi, UserAction) -> Unit,
    onOpenProfile: (Int) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    when (state) {
        FriendsUiState.Loading ->
            Skeleton(SkeletonStyle.List, Modifier.fillMaxSize().testTag(FriendsTestTags.LOADING))
        FriendsUiState.Disabled -> FriendsState(
            painterResource(KitRes.drawable.ic_lock),
            stringResource(Res.string.friends_disabled_title),
            stringResource(Res.string.friends_disabled_description),
            ContentStateAction(stringResource(CoreRes.string.settings_title), onOpenSettings),
        )
        is FriendsUiState.Error -> FriendsState(
            painterResource(KitRes.drawable.ic_error),
            stringResource(CoreRes.string.common_load_error_title),
            stringResource(state.error.textResource()),
            ContentStateAction(stringResource(CoreRes.string.common_retry), onRetry),
        )
        is FriendsUiState.Content -> when (state.empty) {
            FriendsEmpty.NO_FRIENDS -> FriendsState(
                painterResource(KitRes.drawable.ic_group),
                stringResource(Res.string.friends_empty_title),
                stringResource(Res.string.friends_empty_description),
                ContentStateAction(stringResource(Res.string.friends_find_people), onOpenSearch),
            )
            FriendsEmpty.NO_REQUESTS -> FriendsState(
                painterResource(KitRes.drawable.ic_how_to_reg),
                stringResource(Res.string.friends_requests_empty_title),
            )
            null -> AppRefreshBox(state.refreshing, onRefresh, Modifier.fillMaxSize()) {
                UserList(
                    state.items,
                    onOpen = { onOpenProfile(it.isu) },
                    modifier = Modifier.fillMaxSize(),
                    onAction = onAction,
                )
            }
        }
    }
}

@Composable
private fun FriendsState(
    icon: Painter,
    title: String,
    description: String? = null,
    action: ContentStateAction? = null,
) {
    ContentState(
        title = title,
        modifier = Modifier.fillMaxSize().testTag(FriendsTestTags.STATE),
        icon = icon,
        description = description,
        action = action,
    )
}

@Composable
private fun RemoveConfirmation(
    removal: FriendsEvent.ConfirmRemove,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ConfirmDialog(
        title = stringResource(Res.string.friends_remove_confirm_title),
        confirmLabel = stringResource(Res.string.user_action_remove),
        dismissLabel = stringResource(CoreRes.string.common_cancel),
        onConfirm = { onConfirm(removal.isu) },
        onDismiss = onDismiss,
        text = stringResource(Res.string.friends_remove_confirm_message, removal.name.asString()),
        destructive = true,
    )
}

private const val MAX_BADGE_NUMBER = 999
private val BadgeRaise: Dp = 4.dp
