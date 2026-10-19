package dev.alllexey.itmowidgets.feature.schedule.ui.friends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetClose
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetScaffold
import dev.alllexey.itmowidgets.designsystem.components.state.ContentState
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateAction
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateLoading
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateSize
import dev.alllexey.itmowidgets.designsystem.kit.GateCard
import dev.alllexey.itmowidgets.designsystem.kit.ListGroup
import dev.alllexey.itmowidgets.designsystem.kit.ListGroupNote
import dev.alllexey.itmowidgets.designsystem.kit.PersonListRow
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.schedule.presentation.feed.SelectedUser
import dev.alllexey.itmowidgets.feature.schedule.presentation.friends.FriendPickerUiState
import dev.alllexey.itmowidgets.feature.schedule.presentation.friends.FriendPickerViewModel
import dev.alllexey.itmowidgets.feature.schedule.presentation.friends.PickerFriends
import dev.alllexey.itmowidgets.feature.schedule.presentation.friends.PickerRow
import dev.alllexey.itmowidgets.shared.compose.strings.common_close
import dev.alllexey.itmowidgets.shared.compose.strings.common_load_error_title
import dev.alllexey.itmowidgets.shared.compose.strings.common_retry
import dev.alllexey.itmowidgets.shared.designsystem.ic_check_circle
import dev.alllexey.itmowidgets.shared.feature.schedule.Res
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_picker_empty
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_picker_gate_fix
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_picker_gate_reason
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_picker_gate_title
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_picker_note
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_picker_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.compose.strings.Res as CoreRes
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/** Test tags of [FriendPickerSheetContent]. */
object FriendPickerTestTags {
    const val OWN = "friend_picker:own"
    const val GATE = "friend_picker:gate"
    const val EMPTY = "friend_picker:empty"

    fun friend(isu: Int) = "friend_picker:friend:$isu"
}

/** What the picker asks of its host. */
data class FriendPickerActions(
    /** The choice: a friend, or null for the own schedule; the host closes the sheet. */
    val onPick: (SelectedUser?) -> Unit = {},
    val onConnect: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onClose: () -> Unit = {},
)

/** The picker with its ViewModel. */
@Composable
fun FriendPickerRoute(viewModel: FriendPickerViewModel, actions: FriendPickerActions) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FriendPickerSheetContent(state, actions.copy(onRetry = viewModel::retry))
}

/**
 * `Чьё расписание` (docs/features/schedule.md): the own schedule, then the friends with their avatars and where they
 * are now; one tap picks. A friend who hid the schedule is dimmed and cannot be picked. Without the connection a gate
 * with `Подключить`. Stateless.
 */
@Composable
fun FriendPickerSheetContent(state: FriendPickerUiState, actions: FriendPickerActions, modifier: Modifier = Modifier) {
    SheetScaffold(
        title = stringResource(Res.string.schedule_picker_title),
        modifier = modifier,
        close = SheetClose(stringResource(CoreRes.string.common_close), actions.onClose),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ItmoTheme.spacing.screenMargin)
                .padding(bottom = ItmoTheme.spacing.group),
            verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
        ) {
            val friends = state.friends
            Column {
                ListGroup {
                    item { Person(state.own, actions, Modifier.testTag(FriendPickerTestTags.OWN)) }
                    if (friends is PickerFriends.Content) {
                        friends.rows.forEach { row ->
                            item { Person(row, actions, Modifier.testTag(FriendPickerTestTags.friend(row.user?.isu ?: 0))) }
                        }
                    }
                }
                if (friends is PickerFriends.Content && friends.rows.isNotEmpty()) {
                    ListGroupNote(stringResource(Res.string.schedule_picker_note))
                }
            }
            when (friends) {
                PickerFriends.Loading -> ContentStateLoading(size = ContentStateSize.Compact)
                PickerFriends.Disabled -> GateCard(
                    title = stringResource(Res.string.schedule_picker_gate_title),
                    reason = stringResource(Res.string.schedule_picker_gate_reason),
                    fix = stringResource(Res.string.schedule_picker_gate_fix),
                    onFix = actions.onConnect,
                    modifier = Modifier.testTag(FriendPickerTestTags.GATE),
                )
                is PickerFriends.Failed -> ContentState(
                    title = stringResource(CoreRes.string.common_load_error_title),
                    size = ContentStateSize.Compact,
                    description = friends.error.toUiText().asString(),
                    action = ContentStateAction(stringResource(CoreRes.string.common_retry), actions.onRetry),
                )
                is PickerFriends.Content -> if (friends.rows.isEmpty()) {
                    ContentState(
                        title = stringResource(Res.string.schedule_picker_empty),
                        modifier = Modifier.testTag(FriendPickerTestTags.EMPTY),
                        size = ContentStateSize.Compact,
                        description = stringResource(Res.string.schedule_picker_note),
                    )
                }
            }
        }
    }
}

@Composable
private fun Person(row: PickerRow, actions: FriendPickerActions, modifier: Modifier) {
    PersonListRow(
        title = row.title.asString(),
        pictureUrl = row.avatar,
        modifier = modifier
            .alpha(if (row.selectable) 1f else HIDDEN_ALPHA)
            .semantics { selected = row.selected },
        name = row.person,
        initials = row.initials,
        subtitle = row.status?.asString(),
        accessory = if (row.selected) {
            {
                Icon(
                    painterResource(KitRes.drawable.ic_check_circle),
                    contentDescription = null,
                    tint = ItmoTheme.colorScheme.primary,
                )
            }
        } else {
            null
        },
        onClick = if (row.selectable) ({ actions.onPick(row.user) }) else null,
    )
}

private const val HIDDEN_ALPHA = 0.5f
