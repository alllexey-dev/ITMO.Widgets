package dev.alllexey.itmowidgets.feature.social.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.core.text.personInitials
import dev.alllexey.itmowidgets.designsystem.components.avatar.Avatar
import dev.alllexey.itmowidgets.designsystem.components.buttons.TonalButton
import dev.alllexey.itmowidgets.designsystem.kit.ListGroup
import dev.alllexey.itmowidgets.designsystem.kit.ListRow
import dev.alllexey.itmowidgets.designsystem.kit.SectionHeader
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.social.presentation.UserAction
import dev.alllexey.itmowidgets.feature.social.presentation.UserListItem
import dev.alllexey.itmowidgets.feature.social.presentation.UserRowUi
import dev.alllexey.itmowidgets.shared.feature.social.SocialStrings

/** Test tags of the people rows, shared by every social list screen and its tests. */
object UserListTestTags {
    const val LOAD_MORE = "user_list_load_more"

    fun row(isu: Int): String = "user_list_row_$isu"
    fun action(isu: Int, action: UserAction): String = "user_list_${action.name.lowercase()}_$isu"
}

/**
 * One person of a list (DECISIONS.md, Друзья): the avatar, the name, the group line and a status line. A pair of
 * actions (an incoming request) sits under the texts, the filled one first; a single action is a text button at the
 * end; a row without actions opens the card with a chevron. A [UserRowUi.busy] row keeps its buttons inert.
 */
@Composable
fun PersonRow(row: UserRowUi, onOpen: (UserRowUi) -> Unit, onAction: (UserRowUi, UserAction) -> Unit) {
    val name = row.displayName.asString()
    val actions = listOfNotNull(row.primary, row.secondary)
    ListRow(
        title = name,
        modifier = Modifier.testTag(UserListTestTags.row(row.isu)),
        subtitle = listOfNotNull(row.subtitle.asString(), row.status?.asString()).joinToString("\n"),
        leading = { Avatar(name, row.pictureUrl, size = AvatarSize, initials = personInitials(name, row.familyName, row.givenName)) },
        accessory = actions.singleOrNull()?.let { action ->
            {
                TextButton(
                    onClick = { onAction(row, action) },
                    enabled = !row.busy,
                    modifier = Modifier.testTag(UserListTestTags.action(row.isu, action)),
                ) { Text(action.label().asString()) }
            }
        },
        chevron = actions.isEmpty() && row.opensProfile,
        below = if (actions.size > 1) {
            { PairedActions(row, actions, onAction) }
        } else {
            null
        },
        onClick = if (row.opensProfile) { { onOpen(row) } } else null,
    )
}

@Composable
private fun PairedActions(row: UserRowUi, actions: List<UserAction>, onAction: (UserRowUi, UserAction) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact)) {
        actions.forEachIndexed { index, action ->
            val tagged = Modifier.testTag(UserListTestTags.action(row.isu, action))
            val content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
                Text(action.label().asString())
            }
            if (index == 0) {
                Button(
                    onClick = { onAction(row, action) },
                    modifier = tagged,
                    enabled = !row.busy,
                    contentPadding = ButtonDefaults.SmallContentPadding,
                    content = content,
                )
            } else {
                TonalButton(
                    onClick = { onAction(row, action) },
                    modifier = tagged,
                    enabled = !row.busy,
                    contentPadding = ButtonDefaults.SmallContentPadding,
                    content = content,
                )
            }
        }
    }
}

/** [rows] as one connected group under an optional [title] with its [count]; nothing for an empty list. */
fun LazyListScope.personGroup(
    key: String,
    rows: List<UserRowUi>,
    onOpen: (UserRowUi) -> Unit,
    onAction: (UserRowUi, UserAction) -> Unit,
    title: UiText? = null,
    count: String? = null,
) {
    if (rows.isEmpty()) return
    if (title != null) item(key = "$key:title", contentType = "title") { SectionHeader(title.asString(), count = count) }
    item(key = key, contentType = "people") {
        ListGroup {
            rows.forEach { row -> item { PersonRow(row, onOpen, onAction) } }
        }
    }
}

/** A [UserListItem] list (another user's friends): its headers and the runs of rows between them as groups. */
fun LazyListScope.userList(
    items: List<UserListItem>,
    onOpen: (UserRowUi) -> Unit,
    onAction: (UserRowUi, UserAction) -> Unit = { _, _ -> },
) {
    var title: UiText? = null
    var run = mutableListOf<UserRowUi>()
    var index = 0
    fun flush() {
        personGroup("group:${index++}", run, onOpen, onAction, title)
        run = mutableListOf()
        title = null
    }
    items.forEach { item ->
        when (item) {
            is UserListItem.Header -> { flush(); title = item.title }
            is UserListItem.User -> run += item.row
            UserListItem.LoadMore -> Unit
        }
    }
    flush()
}

/** The button label of a row action. */
fun UserAction.label(): UiText = UiText.Res(
    when (this) {
        UserAction.ADD -> SocialStrings.user_action_add
        UserAction.ACCEPT -> SocialStrings.user_action_accept
        UserAction.REJECT -> SocialStrings.user_action_reject
        UserAction.CANCEL -> SocialStrings.user_action_cancel
        UserAction.REMOVE -> SocialStrings.user_action_remove
        UserAction.INVITE -> SocialStrings.user_action_invite
    },
)

/** The avatar of a list row (`ListGroup`'s 40 dp leading). */
private val AvatarSize = 40.dp
