package dev.alllexey.itmowidgets.feature.friendselector.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.core.text.textResource
import dev.alllexey.itmowidgets.core.text.userDisplayName
import dev.alllexey.itmowidgets.designsystem.components.avatar.Avatar
import dev.alllexey.itmowidgets.designsystem.components.expressive.ItmoButtonGroup
import dev.alllexey.itmowidgets.designsystem.components.rows.UserSelectionRow
import dev.alllexey.itmowidgets.designsystem.components.settings.SelectionMode
import dev.alllexey.itmowidgets.designsystem.components.state.ContentState
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateAction
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateLoading
import dev.alllexey.itmowidgets.designsystem.gesture.tabSwipeHandover
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.friendselector.presentation.FriendSelectorBody
import dev.alllexey.itmowidgets.feature.friendselector.presentation.FriendSelectorScope
import dev.alllexey.itmowidgets.feature.friendselector.presentation.FriendSelectorUiState
import dev.alllexey.itmowidgets.shared.core.common_load_error_title
import dev.alllexey.itmowidgets.shared.core.common_retry
import dev.alllexey.itmowidgets.shared.designsystem.ic_check
import dev.alllexey.itmowidgets.shared.designsystem.ic_error
import dev.alllexey.itmowidgets.shared.designsystem.ic_group
import dev.alllexey.itmowidgets.shared.designsystem.ic_lock
import dev.alllexey.itmowidgets.shared.designsystem.ic_person
import dev.alllexey.itmowidgets.shared.designsystem.ic_search
import dev.alllexey.itmowidgets.shared.feature.social.Res
import dev.alllexey.itmowidgets.shared.feature.social.friend_picker_disabled
import dev.alllexey.itmowidgets.shared.feature.social.friend_picker_empty_title
import dev.alllexey.itmowidgets.shared.feature.social.friend_picker_loading
import dev.alllexey.itmowidgets.shared.feature.social.friend_picker_more_groups
import dev.alllexey.itmowidgets.shared.feature.social.friend_picker_my_schedule_accessibility
import dev.alllexey.itmowidgets.shared.feature.social.friend_picker_my_schedule_short
import dev.alllexey.itmowidgets.shared.feature.social.friend_picker_no_group
import dev.alllexey.itmowidgets.shared.feature.social.friend_picker_people_empty_description
import dev.alllexey.itmowidgets.shared.feature.social.friend_picker_people_empty_title
import dev.alllexey.itmowidgets.shared.feature.social.friend_picker_people_idle_description
import dev.alllexey.itmowidgets.shared.feature.social.friend_picker_people_idle_title
import dev.alllexey.itmowidgets.shared.feature.social.friend_picker_recent
import dev.alllexey.itmowidgets.shared.feature.social.friend_picker_schedule_hidden
import dev.alllexey.itmowidgets.shared.feature.social.friend_picker_scope_all
import dev.alllexey.itmowidgets.shared.feature.social.friend_picker_scope_friends
import dev.alllexey.itmowidgets.shared.feature.social.friend_picker_search_hint
import dev.alllexey.itmowidgets.shared.feature.social.friend_picker_search_people_hint
import dev.alllexey.itmowidgets.shared.feature.social.friend_picker_user_subtitle
import dev.alllexey.itmowidgets.shared.feature.social.friends_empty_description
import dev.alllexey.itmowidgets.shared.feature.social.friends_empty_title
import dev.alllexey.itmowidgets.shared.feature.social.user_search_clear
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/**
 * `Недавние` and its chips (`item_recent_friend.xml`): the own schedule first, then the recent friends whose schedule
 * is open, each keyed by its person, so a refresh or a new choice never moves a chip under a finger. The chosen chip
 * has a ring and a check badge; TalkBack reads the full name, or `Моё расписание`, with the selection.
 */
@Composable
internal fun RecentChips(state: FriendSelectorUiState, actions: FriendSelectorActions, modifier: Modifier) {
    val friends = remember(state.recentFriends) {
        state.recentFriends.filter { it.sharing.schedule }.distinctBy(UserSummary::isu)
    }
    Text(
        stringResource(Res.string.friend_picker_recent),
        modifier
            .fillMaxWidth()
            .padding(top = ItmoTheme.spacing.compact)
            .semantics { heading() },
        color = ItmoTheme.colorScheme.onSurfaceVariant,
        style = ItmoTheme.typography.labelLarge,
    )
    val listState = rememberLazyListState()
    // In a sheet the handover changes nothing; it keeps the row right if the picker is ever hosted on a tab.
    LazyRow(
        modifier
            .fillMaxWidth()
            .padding(top = ItmoTheme.spacing.related)
            .tabSwipeHandover(listState)
            .selectableGroup()
            .testTag(FriendSelectorTestTags.RECENT),
        state = listState,
    ) {
        item(key = OWN_CHIP_KEY) {
            RecentChip(
                label = stringResource(Res.string.friend_picker_my_schedule_short),
                description = stringResource(Res.string.friend_picker_my_schedule_accessibility),
                avatarUser = state.currentUser,
                selected = state.selectedIsu == null,
                onClick = actions.onSelectOwn,
                modifier = Modifier.testTag(FriendSelectorTestTags.OWN_CHIP),
            )
        }
        friends.forEach { friend ->
            item(key = friend.isu) {
                RecentChip(
                    label = friend.name.substringBefore(" "),
                    description = friend.name,
                    avatarUser = friend,
                    selected = state.selectedIsu == friend.isu,
                    onClick = { actions.onSelect(friend) },
                    modifier = Modifier.testTag(FriendSelectorTestTags.chip(friend.isu)),
                )
            }
        }
    }
}

/**
 * One chip: a 48 dp avatar on `secondaryContainer` in a 56 dp box, the badge at its bottom end, the label under it in
 * one line. Without [avatarUser] (the own profile not known yet) it shows the person icon.
 */
@Composable
private fun RecentChip(
    label: String,
    description: String,
    avatarUser: UserSummary?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier
            .width(ChipWidth)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(vertical = ItmoTheme.spacing.related),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(ChipAvatarBox).clearAndSetSemantics {}) {
            val ring = if (selected) {
                Modifier.border(SelectionStroke, ItmoTheme.colorScheme.primary, CircleShape)
            } else {
                Modifier
            }
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .size(ChipAvatarSize)
                    .clip(CircleShape)
                    .background(ItmoTheme.colorScheme.secondaryContainer)
                    .then(ring),
                contentAlignment = Alignment.Center,
            ) {
                if (avatarUser == null) {
                    Icon(
                        painterResource(KitRes.drawable.ic_person),
                        contentDescription = null,
                        modifier = Modifier.size(OwnIconSize),
                        tint = ItmoTheme.colorScheme.onSecondaryContainer,
                    )
                } else {
                    Avatar(avatarUser.name, avatarUser.pictureUrl, size = ChipAvatarSize)
                }
            }
            if (selected) SelectionBadge(Modifier.align(Alignment.BottomEnd))
        }
        Text(
            label,
            Modifier
                .fillMaxWidth()
                .padding(top = ItmoTheme.spacing.related)
                .clearAndSetSemantics {},
            color = ItmoTheme.colorScheme.onSurface,
            style = ItmoTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** `bg_friend_selection_badge`: a `primary` circle in a 2 dp `surface` ring with a check in `onPrimary`. */
@Composable
private fun SelectionBadge(modifier: Modifier) {
    Box(
        modifier
            .size(BadgeSize)
            .clip(CircleShape)
            .background(ItmoTheme.colorScheme.surface)
            .padding(SelectionStroke)
            .clip(CircleShape)
            .background(ItmoTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(KitRes.drawable.ic_check),
            contentDescription = null,
            modifier = Modifier.size(BadgeIconSize),
            tint = ItmoTheme.colorScheme.onPrimary,
        )
    }
}

/** `scope_toggle`: `Друзья` and `Все` side by side, one always chosen. */
@Composable
internal fun ScopeToggle(scope: FriendSelectorScope, onScope: (FriendSelectorScope) -> Unit, modifier: Modifier) {
    ItmoButtonGroup(
        options = listOf(
            stringResource(Res.string.friend_picker_scope_friends),
            stringResource(Res.string.friend_picker_scope_all),
        ),
        selectedIndex = if (scope == FriendSelectorScope.ALL) 1 else 0,
        onSelect = { index -> onScope(if (index == 1) FriendSelectorScope.ALL else FriendSelectorScope.FRIENDS) },
        modifier = modifier.testTag(FriendSelectorTestTags.SCOPE),
    )
}

/**
 * `search_layout`: the dense outlined field with a search icon, the scope's hint as the label and a clear button while
 * it holds text. Plain text, `Done` on the keyboard.
 */
@Composable
internal fun PickerSearchField(
    query: String,
    scope: FriendSelectorScope,
    onQueryChange: (String) -> Unit,
    modifier: Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hint = stringResource(
        if (scope == FriendSelectorScope.ALL) {
            Res.string.friend_picker_search_people_hint
        } else {
            Res.string.friend_picker_search_hint
        },
    )
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .heightIn(min = ItmoTheme.spacing.touchTarget)
            .testTag(FriendSelectorTestTags.SEARCH),
        textStyle = ItmoTheme.typography.bodyLarge.copy(color = ItmoTheme.colorScheme.onSurface),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        singleLine = true,
        interactionSource = interactionSource,
        cursorBrush = SolidColor(ItmoTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = query,
                innerTextField = innerTextField,
                enabled = true,
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                label = { Text(hint) },
                leadingIcon = { Icon(painterResource(KitRes.drawable.ic_search), contentDescription = null) },
                trailingIcon = if (query.isEmpty()) {
                    null
                } else {
                    {
                        IconButton(onClick = { onQueryChange("") }, Modifier.testTag(FriendSelectorTestTags.CLEAR)) {
                            Icon(ClearTextIcon, stringResource(Res.string.user_search_clear))
                        }
                    }
                },
                contentPadding = OutlinedTextFieldDefaults.contentPaddingWithLabel(
                    top = ItmoTheme.spacing.content,
                    bottom = ItmoTheme.spacing.content,
                ),
                container = {
                    OutlinedTextFieldDefaults.Container(
                        enabled = true,
                        isError = false,
                        interactionSource = interactionSource,
                    )
                },
            )
        },
    )
}

/** The rows, the progress or a state, all in the same area under the field. */
@Composable
internal fun PickerBody(state: FriendSelectorUiState, actions: FriendSelectorActions) {
    when (val body = state.body) {
        FriendSelectorBody.Loading, FriendSelectorBody.PeopleLoading -> {
            val description = stringResource(Res.string.friend_picker_loading)
            ContentStateLoading(
                Modifier
                    .testTag(FriendSelectorTestTags.LOADING)
                    .semantics { contentDescription = description },
            )
        }
        is FriendSelectorBody.Users -> PickerRows(body.users, state.selectedIsu, actions)
        FriendSelectorBody.NoMatches -> PickerState(
            painterResource(KitRes.drawable.ic_search),
            stringResource(Res.string.friend_picker_empty_title),
        )
        FriendSelectorBody.PeopleIdle -> PickerState(
            painterResource(KitRes.drawable.ic_search),
            stringResource(Res.string.friend_picker_people_idle_title),
            stringResource(Res.string.friend_picker_people_idle_description),
        )
        FriendSelectorBody.PeopleEmpty -> PickerState(
            painterResource(KitRes.drawable.ic_person),
            stringResource(Res.string.friend_picker_people_empty_title),
            stringResource(Res.string.friend_picker_people_empty_description),
        )
        FriendSelectorBody.NoFriends -> PickerState(
            painterResource(KitRes.drawable.ic_group),
            stringResource(Res.string.friends_empty_title),
            stringResource(Res.string.friends_empty_description),
        )
        FriendSelectorBody.Disabled -> PickerState(
            painterResource(KitRes.drawable.ic_lock),
            stringResource(CoreRes.string.common_load_error_title),
            stringResource(Res.string.friend_picker_disabled),
        )
        is FriendSelectorBody.PeopleError -> PickerState(
            painterResource(KitRes.drawable.ic_error),
            stringResource(CoreRes.string.common_load_error_title),
            stringResource(body.error.textResource()),
            ContentStateAction(stringResource(CoreRes.string.common_retry), actions.onRetry),
        )
        is FriendSelectorBody.Error -> PickerState(
            painterResource(KitRes.drawable.ic_error),
            stringResource(CoreRes.string.common_load_error_title),
            stringResource(body.error.textResource()),
            ContentStateAction(stringResource(CoreRes.string.common_retry), actions.onRetry),
        )
    }
}

@Composable
private fun PickerState(
    icon: Painter,
    title: String,
    description: String? = null,
    action: ContentStateAction? = null,
) {
    ContentState(
        title = title,
        modifier = Modifier.testTag(FriendSelectorTestTags.STATE),
        icon = icon,
        description = description,
        action = action,
    )
}

/**
 * The rows (`item_friend_selector.xml`) on the kit's selection row. The avatars line up with the chips' avatars; a
 * person listed twice (Backend does not promise otherwise) keeps a row of their own.
 */
@Composable
private fun PickerRows(users: List<UserSummary>, selectedIsu: Int?, actions: FriendSelectorActions) {
    val keys = remember(users) {
        val seen = HashSet<Int>()
        users.mapIndexed { index, user -> if (seen.add(user.isu)) "${user.isu}" else "${user.isu}#$index" }
    }
    LazyColumn(
        Modifier
            .fillMaxSize()
            .selectableGroup()
            .testTag(FriendSelectorTestTags.LIST),
        contentPadding = PaddingValues(bottom = ItmoTheme.spacing.compact),
    ) {
        itemsIndexed(users, key = { index, _ -> keys[index] }) { _, user ->
            PickerRow(user, selected = user.sharing.schedule && user.isu == selectedIsu, actions)
        }
    }
}

/**
 * One person: the name, `<ИСУ> • <группы>`, and for a closed schedule `Расписание скрыто`, a lock and the dimmed row
 * that leads to the profile. The chosen row lies on a `secondaryContainer` surface with the check; the check's column
 * is reserved, so choosing never rewraps the name. A long press opens the profile from any row.
 */
@Composable
private fun PickerRow(user: UserSummary, selected: Boolean, actions: FriendSelectorActions) {
    val open = user.sharing.schedule
    val shape = ItmoTheme.shapes.cardContent
    val surface = if (selected) Modifier.background(ItmoTheme.colorScheme.secondaryContainer, shape) else Modifier
    UserSelectionRow(
        name = userDisplayName(user.name, user.isu).asString(),
        pictureUrl = user.pictureUrl,
        selected = selected,
        onSelect = if (open) {
            { actions.onSelect(user) }
        } else {
            null
        },
        modifier = Modifier
            .testTag(FriendSelectorTestTags.row(user.isu))
            .clip(shape)
            .then(surface)
            .alpha(if (open) 1f else ClosedRowAlpha),
        subtitle = subtitle(user),
        status = if (open) null else stringResource(Res.string.friend_picker_schedule_hidden),
        mode = SelectionMode.Single,
        onOpen = { actions.onOpenProfile(user) },
    )
}

/** Up to two groups joined, else the first and how many more, or `Нет группы`; after the ISU. */
@Composable
private fun subtitle(user: UserSummary): String {
    val groups = user.groups
    val groupsText = when {
        groups.isEmpty() -> stringResource(Res.string.friend_picker_no_group)
        groups.size <= 2 -> groups.joinToString(" • ") { it.name }
        else -> stringResource(Res.string.friend_picker_more_groups, groups.first().name, groups.size - 1)
    }
    return stringResource(Res.string.friend_picker_user_subtitle, user.isu, groupsText)
}

/** The key of the own chip; friends' chips are keyed by their ISU. */
private const val OWN_CHIP_KEY = "own"

/** `item_recent_friend.xml`: 72 dp wide, a 48 dp avatar in a 56 dp box, a 20 dp badge with a 14 dp check. */
private val ChipWidth = 72.dp
private val ChipAvatarBox = 56.dp
private val ChipAvatarSize = 48.dp
private val OwnIconSize = 24.dp
private val BadgeSize = 20.dp
private val BadgeIconSize = 14.dp

/** `friend_picker_selection_stroke`: the chosen chip's ring and the badge's ring. */
private val SelectionStroke = 2.dp

/** `FriendSelectorAdapter`'s dimmed closed row. */
private const val ClosedRowAlpha = 0.72f

/** Material's `mtrl_ic_cancel`, the clear-text end icon of `TextInputLayout`; the kit has no filled cancel symbol. */
private val ClearTextIcon: ImageVector by lazy {
    ImageVector.Builder("ClearText", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 2f)
            curveTo(6.47f, 2f, 2f, 6.47f, 2f, 12f)
            reflectiveCurveToRelative(4.47f, 10f, 10f, 10f)
            reflectiveCurveToRelative(10f, -4.47f, 10f, -10f)
            reflectiveCurveTo(17.53f, 2f, 12f, 2f)
            close()
            moveTo(17f, 15.59f)
            lineTo(15.59f, 17f)
            lineTo(12f, 13.41f)
            lineTo(8.41f, 17f)
            lineTo(7f, 15.59f)
            lineTo(10.59f, 12f)
            lineTo(7f, 8.41f)
            lineTo(8.41f, 7f)
            lineTo(12f, 10.59f)
            lineTo(15.59f, 7f)
            lineTo(17f, 8.41f)
            lineTo(13.41f, 12f)
            close()
        }
    }.build()
}
