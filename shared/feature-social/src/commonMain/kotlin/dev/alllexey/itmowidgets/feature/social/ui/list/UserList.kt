package dev.alllexey.itmowidgets.feature.social.ui.list

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButton
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButtonStyle
import dev.alllexey.itmowidgets.designsystem.components.rows.UserRow
import dev.alllexey.itmowidgets.designsystem.components.rows.UserRowAction
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.social.presentation.UserAction
import dev.alllexey.itmowidgets.feature.social.presentation.UserListItem
import dev.alllexey.itmowidgets.feature.social.presentation.UserRowUi
import dev.alllexey.itmowidgets.shared.feature.social.Res
import dev.alllexey.itmowidgets.shared.feature.social.user_action_accept
import dev.alllexey.itmowidgets.shared.feature.social.user_action_add
import dev.alllexey.itmowidgets.shared.feature.social.user_action_cancel
import dev.alllexey.itmowidgets.shared.feature.social.user_action_invite
import dev.alllexey.itmowidgets.shared.feature.social.user_action_reject
import dev.alllexey.itmowidgets.shared.feature.social.user_action_remove
import dev.alllexey.itmowidgets.shared.feature.social.user_search_load_more
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Test tags of [UserList], shared by every social list screen and its tests. */
object UserListTestTags {
    const val LIST = "user_list"
    const val LOAD_MORE = "user_list_load_more"

    fun row(isu: Int): String = "user_list_row_$isu"
}

/**
 * A social people list (port of `UserListAdapter`): section headers, one [UserRow] per person with the actions the
 * viewer can take now, and a load-more button. The friends, requests, search and another user's friends screens all
 * render their [UserListItem]s through it; a row that is [UserRowUi.busy] keeps its buttons inert.
 */
@Composable
fun UserList(
    items: List<UserListItem>,
    onOpen: (UserRowUi) -> Unit,
    modifier: Modifier = Modifier,
    onAction: (UserRowUi, UserAction) -> Unit = { _, _ -> },
    onLoadMore: () -> Unit = {},
    state: LazyListState = rememberLazyListState(),
) {
    val keys = remember(items) { items.stableKeys() }
    LazyColumn(
        modifier.testTag(UserListTestTags.LIST),
        state = state,
        contentPadding = PaddingValues(bottom = ItmoTheme.spacing.group),
    ) {
        itemsIndexed(
            items,
            key = { index, _ -> keys[index] },
            contentType = { _, item -> item.contentType() },
        ) { _, item ->
            when (item) {
                is UserListItem.Header -> UserListHeader(item.title.asString())
                is UserListItem.User -> UserListRow(item.row, onOpen, onAction)
                UserListItem.LoadMore -> UserListLoadMore(onLoadMore)
            }
        }
    }
}

/** One person of a [UserList]: the kit row with this row's texts, its labelled actions and its busy state. */
@Composable
fun UserListRow(
    row: UserRowUi,
    onOpen: (UserRowUi) -> Unit,
    onAction: (UserRowUi, UserAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    UserRow(
        name = row.displayName.asString(),
        pictureUrl = row.pictureUrl,
        modifier = modifier.testTag(UserListTestTags.row(row.isu)),
        subtitle = row.subtitle.asString(),
        status = row.status?.asString(),
        onClick = if (row.opensProfile) {
            { onOpen(row) }
        } else {
            null
        },
        primaryAction = row.primary?.let { action ->
            UserRowAction(stringResource(action.label())) { onAction(row, action) }
        },
        secondaryAction = row.secondary?.let { action ->
            UserRowAction(stringResource(action.label())) { onAction(row, action) }
        },
        busy = row.busy,
    )
}

/** `item_user_list_header.xml`: `labelLarge` in `onSurfaceVariant`, in line with the rows' avatars. */
@Composable
private fun UserListHeader(title: String) {
    Text(
        title,
        Modifier
            .fillMaxWidth()
            .padding(
                start = ItmoTheme.spacing.group,
                end = ItmoTheme.spacing.group,
                top = ItmoTheme.spacing.group,
                bottom = ItmoTheme.spacing.related,
            )
            .semantics { heading() },
        color = ItmoTheme.colorScheme.onSurfaceVariant,
        style = ItmoTheme.typography.labelLarge,
    )
}

/** `item_user_list_load_more.xml`: a full-width text button under the last row. */
@Composable
private fun UserListLoadMore(onLoadMore: () -> Unit) {
    ProgressButton(
        label = stringResource(Res.string.user_search_load_more),
        onClick = onLoadMore,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ItmoTheme.spacing.group, vertical = ItmoTheme.spacing.compact)
            .testTag(UserListTestTags.LOAD_MORE),
        style = ProgressButtonStyle.Text,
    )
}

/** The button label of a row action. */
fun UserAction.label(): StringResource = when (this) {
    UserAction.ADD -> Res.string.user_action_add
    UserAction.ACCEPT -> Res.string.user_action_accept
    UserAction.REJECT -> Res.string.user_action_reject
    UserAction.CANCEL -> Res.string.user_action_cancel
    UserAction.REMOVE -> Res.string.user_action_remove
    UserAction.INVITE -> Res.string.user_action_invite
}

/**
 * A person keeps their key while the list changes around them; a second row of the same person (not expected, but
 * Backend does not promise it) gets its position appended, since a repeated key crashes a lazy list.
 */
private fun List<UserListItem>.stableKeys(): List<String> {
    val seen = HashSet<String>()
    return mapIndexed { index, item ->
        val key = when (item) {
            is UserListItem.Header -> "header:$index"
            is UserListItem.User -> "user:${item.row.isu}"
            UserListItem.LoadMore -> "load-more"
        }
        if (seen.add(key)) key else "$key#$index"
    }
}

private fun UserListItem.contentType(): String = when (this) {
    is UserListItem.Header -> "header"
    is UserListItem.User -> "user"
    UserListItem.LoadMore -> "load-more"
}
