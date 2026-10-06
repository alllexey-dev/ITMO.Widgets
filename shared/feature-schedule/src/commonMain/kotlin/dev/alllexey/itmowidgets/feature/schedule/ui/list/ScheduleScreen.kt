package dev.alllexey.itmowidgets.feature.schedule.ui.list

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.text.textResource
import dev.alllexey.itmowidgets.designsystem.components.avatar.Avatar
import dev.alllexey.itmowidgets.designsystem.components.state.AppRefreshBox
import dev.alllexey.itmowidgets.designsystem.components.state.ContentState
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateAction
import dev.alllexey.itmowidgets.designsystem.components.state.Skeleton
import dev.alllexey.itmowidgets.designsystem.components.state.SkeletonStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.schedule.presentation.SelectedUser
import dev.alllexey.itmowidgets.shared.core.common_load_error_title
import dev.alllexey.itmowidgets.shared.core.common_retry
import dev.alllexey.itmowidgets.shared.designsystem.ic_close
import dev.alllexey.itmowidgets.shared.designsystem.ic_error
import dev.alllexey.itmowidgets.shared.designsystem.ic_event_note
import dev.alllexey.itmowidgets.shared.designsystem.ic_group
import dev.alllexey.itmowidgets.shared.designsystem.ic_keyboard_arrow_up
import dev.alllexey.itmowidgets.shared.feature.schedule.Res
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_change_friend
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_empty_description
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_empty_title
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_friends_action
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_return_to_mine
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_scroll_to_top
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_selected_friend_label
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/** Test tags of [ScheduleScreen]; days and rows carry [ScheduleListTestTags]. */
object ScheduleScreenTestTags {
    const val LIST = "schedule_list"
    const val LOADING = "schedule_loading"

    /** The empty or error state in the list's place. */
    const val STATE = "schedule_state"
    const val SELECTED_USER = "schedule_selected_user"
    const val FRIENDS_BUTTON = "schedule_friends_button"
    const val SCROLL_TO_TOP = "schedule_scroll_to_top"
}

/**
 * The schedule list (port of `fragment_schedule.xml`): the selected friend's card above, then the day cards keyed by
 * date under a pull to refresh, placeholder cards on a first load, the empty or error state in the list's place.
 * Two FABs at the bottom end: back to the top once the reader is past the third day, and the friends picker while
 * [ScheduleScreenState.canPickFriend]. [listState] belongs to the host, which scrolls it to today; failed refreshes
 * of a shown list go to [snackbarHostState]. Stateless.
 */
@Composable
fun ScheduleScreen(
    state: ScheduleScreenState,
    actions: ScheduleScreenActions,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val scope = rememberCoroutineScope()
    val pastThirdDay by remember(listState) { derivedStateOf { listState.firstVisibleItemIndex > TOP_BUTTON_AFTER } }
    Box(modifier.fillMaxSize().background(ItmoTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize()) {
            state.selectedUser?.let { user ->
                SelectedUserCard(user, onChange = actions.onPickFriend, onClear = actions.onClearFriend)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                ScheduleBody(state, actions, listState)
            }
        }
        Column(
            Modifier.align(Alignment.BottomEnd).padding(FabMargin),
            verticalArrangement = Arrangement.spacedBy(FabMargin * 2),
            horizontalAlignment = Alignment.End,
        ) {
            AnimatedVisibility(
                visible = state.body is ScheduleScreenBody.Days && pastThirdDay,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                FloatingActionButton(
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    modifier = Modifier.testTag(ScheduleScreenTestTags.SCROLL_TO_TOP),
                    containerColor = ItmoTheme.colorScheme.surfaceContainerHigh,
                    contentColor = ItmoTheme.colorScheme.primary,
                ) {
                    Icon(
                        painterResource(KitRes.drawable.ic_keyboard_arrow_up),
                        stringResource(Res.string.schedule_scroll_to_top),
                    )
                }
            }
            if (state.canPickFriend) {
                FloatingActionButton(
                    onClick = actions.onPickFriend,
                    modifier = Modifier.testTag(ScheduleScreenTestTags.FRIENDS_BUTTON),
                ) {
                    Icon(painterResource(KitRes.drawable.ic_group), stringResource(Res.string.schedule_friends_action))
                }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun ScheduleBody(state: ScheduleScreenState, actions: ScheduleScreenActions, listState: LazyListState) {
    when (val body = state.body) {
        ScheduleScreenBody.Loading -> Skeleton(
            SkeletonStyle.Cards,
            Modifier.fillMaxSize().testTag(ScheduleScreenTestTags.LOADING),
            rows = SKELETON_ROWS,
            rowHeight = SkeletonRowHeight,
        )
        ScheduleScreenBody.Empty -> ContentState(
            title = stringResource(Res.string.schedule_empty_title),
            modifier = Modifier.fillMaxSize().testTag(ScheduleScreenTestTags.STATE),
            icon = painterResource(KitRes.drawable.ic_event_note),
            description = stringResource(Res.string.schedule_empty_description),
        )
        is ScheduleScreenBody.Failed -> ContentState(
            title = stringResource(CoreRes.string.common_load_error_title),
            modifier = Modifier.fillMaxSize().testTag(ScheduleScreenTestTags.STATE),
            icon = painterResource(KitRes.drawable.ic_error),
            description = stringResource(body.error.textResource()),
            action = ContentStateAction(stringResource(CoreRes.string.common_retry), actions.onRetry),
        )
        is ScheduleScreenBody.Days -> AppRefreshBox(state.refreshing, actions.onRefresh, Modifier.fillMaxSize()) {
            ScheduleDays(body, actions, listState)
        }
    }
}

@Composable
private fun ScheduleDays(body: ScheduleScreenBody.Days, actions: ScheduleScreenActions, listState: LazyListState) {
    val loadMore by rememberUpdatedState(actions.onLoadMore)
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last > info.totalItemsCount - LOAD_MORE_WITHIN
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { loadMore() }
    }
    LazyColumn(
        Modifier.fillMaxSize().testTag(ScheduleScreenTestTags.LIST),
        state = listState,
        contentPadding = PaddingValues(top = ListTopPadding, bottom = ItmoTheme.spacing.fabStackClearance),
    ) {
        items(body.days, key = { it.date.toString() }) { day ->
            ScheduleDayCard(
                day,
                onLessonClick = { lesson -> actions.onLessonClick(lesson, day.date) },
                onPendingClick = actions.onPendingClick,
                modifier = Modifier.padding(
                    horizontal = ItmoTheme.spacing.screenMargin,
                    vertical = ItmoTheme.spacing.compact,
                ),
            )
        }
    }
}

/** Whose schedule is shown when it is not the own one: change the friend or go back to the own schedule. */
@Composable
private fun SelectedUserCard(user: SelectedUser, onChange: () -> Unit, onClear: () -> Unit) {
    val onContainer = ItmoTheme.colorScheme.onSecondaryContainer
    Surface(
        Modifier
            .fillMaxWidth()
            .padding(start = SelectedUserMargin, top = ItmoTheme.spacing.compact, end = SelectedUserMargin)
            .testTag(ScheduleScreenTestTags.SELECTED_USER),
        shape = ItmoTheme.shapes.cardContent,
        color = ItmoTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            Modifier
                .heightIn(min = SelectedUserMinHeight)
                .padding(
                    start = ItmoTheme.spacing.content,
                    top = ItmoTheme.spacing.compact,
                    end = ItmoTheme.spacing.related,
                    bottom = ItmoTheme.spacing.compact,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(user.name, user.avatar, size = SelectedUserAvatar)
            Column(Modifier.weight(1f).padding(start = ItmoTheme.spacing.content)) {
                Text(
                    stringResource(Res.string.schedule_selected_friend_label),
                    color = onContainer,
                    style = ItmoTheme.typography.labelMedium,
                )
                Text(
                    user.name,
                    color = onContainer,
                    style = ItmoTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onChange, modifier = Modifier.heightIn(min = ItmoTheme.spacing.touchTarget)) {
                Text(stringResource(Res.string.schedule_change_friend), color = onContainer)
            }
            IconButton(onClick = onClear) {
                Icon(
                    painterResource(KitRes.drawable.ic_close),
                    stringResource(Res.string.schedule_return_to_mine),
                    tint = onContainer,
                )
            }
        }
    }
}

/** `firstVisible > 2` of the fragment's scroll listener. */
private const val TOP_BUTTON_AFTER = 2

/** The fragment asked for more days once the last visible day was within three of the end. */
private const val LOAD_MORE_WITHIN = 3

// `fragment_schedule.xml`: three 180 dp placeholder cards, 8 dp above the list, 16 dp FAB margins.
private const val SKELETON_ROWS = 3
private val SkeletonRowHeight = 180.dp
private val ListTopPadding = 8.dp
private val FabMargin = 16.dp

// The selected-user card: 12 dp side margins, at least 64 dp high, a 44 dp avatar.
private val SelectedUserMargin = 12.dp
private val SelectedUserMinHeight = 64.dp
private val SelectedUserAvatar = 44.dp
