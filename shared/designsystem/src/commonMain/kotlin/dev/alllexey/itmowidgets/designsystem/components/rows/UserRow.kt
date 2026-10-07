package dev.alllexey.itmowidgets.designsystem.components.rows

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.components.avatar.Avatar
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButton
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButtonStyle
import dev.alllexey.itmowidgets.designsystem.components.groups.IosCheckmark
import dev.alllexey.itmowidgets.designsystem.components.groups.IosDisclosure
import dev.alllexey.itmowidgets.designsystem.components.groups.IosListRow
import dev.alllexey.itmowidgets.designsystem.components.settings.SelectionMode
import dev.alllexey.itmowidgets.designsystem.components.settings.rememberSelectionFeedback
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.tokens.IosMetrics
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_check
import dev.alllexey.itmowidgets.shared.designsystem.ic_chevron_right
import dev.alllexey.itmowidgets.shared.designsystem.ic_lock
import org.jetbrains.compose.resources.painterResource

/** A button at the end of a [UserRow]: its [label] (`Принять`, `Отклонить`) and what it does. */
class UserRowAction(
    val label: String,
    val onClick: () -> Unit,
)

/**
 * A person in a list (port of `item_user_row.xml`, home and social): a 48 dp [Avatar], the [name] in `titleMedium`
 * that wraps, a one-line [subtitle] and an optional [status] in `bodySmall`, then the row's actions: a text button
 * for [secondaryAction] and a tonal one for [primaryAction], both inert while [busy]. With [onClick] the whole row
 * opens the person; a row that opens and has no actions ends in a chevron. TalkBack reads the row once.
 *
 * Under the iOS style it is a cell of an inset group: the second lines in subheadline `secondaryLabel`, the
 * disclosure indicator, the separator under it inset to the name, the pressed cell instead of a ripple; the avatar
 * and the buttons keep their size.
 */
@Composable
fun UserRow(
    name: String,
    pictureUrl: String?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    status: String? = null,
    onClick: (() -> Unit)? = null,
    primaryAction: UserRowAction? = null,
    secondaryAction: UserRowAction? = null,
    busy: Boolean = false,
) {
    val interaction = if (onClick == null) {
        Modifier.semantics(mergeDescendants = true) {}
    } else {
        Modifier.clickable(onClick = onClick)
    }
    UserRowLayout(name, pictureUrl, subtitle, status, modifier.then(interaction)) {
        secondaryAction?.let { ActionButton(it, ProgressButtonStyle.Text, enabled = !busy) }
        primaryAction?.let { ActionButton(it, ProgressButtonStyle.Tonal, enabled = !busy) }
        if (onClick != null && primaryAction == null && secondaryAction == null) {
            TrailingIcon(Modifier)
        }
    }
}

/**
 * A person the user picks from a list, such as friends to share with: the whole row is the target, a check in
 * `primary` shows the selection, and TalkBack reads the row once with its selected state ([mode] decides radio button
 * or checkbox). A closed row ([onSelect] null, a private profile) is neither a target nor selected and never shows the
 * check. With [onOpen] the row also leads to the person (the friend picker's profile): a long press on any row calls
 * it, and a closed row becomes a target for it that ends in a lock instead of staying inert. Under the iOS style the
 * check is UIKit's checkmark accessory and a pick plays the selection or toggle haptic.
 */
@Composable
fun UserSelectionRow(
    name: String,
    pictureUrl: String?,
    selected: Boolean,
    onSelect: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    status: String? = null,
    mode: SelectionMode = SelectionMode.Multiple,
    onOpen: (() -> Unit)? = null,
) {
    val pick = rememberSelectionFeedback(onSelect, mode)
    val interaction = when {
        onOpen != null -> pickOrOpen(selected, pick, onOpen, mode)
        pick == null -> Modifier.semantics(mergeDescendants = true) {}
        mode == SelectionMode.Single -> Modifier.selectable(selected, role = Role.RadioButton, onClick = pick)
        else -> Modifier.toggleable(selected, role = Role.Checkbox, onValueChange = { pick() })
    }
    UserRowLayout(name, pictureUrl, subtitle, status, modifier.then(interaction)) {
        when {
            onSelect != null -> TrailingIcon(Modifier.alpha(if (selected) 1f else 0f), check = true)
            onOpen != null -> LockIcon()
        }
    }
}

/**
 * The selection semantics of [UserSelectionRow] on a click that also takes a long press: a pick on tap, [open] on a
 * long press; a closed row ([pick] null) opens on both.
 */
private fun pickOrOpen(selected: Boolean, pick: (() -> Unit)?, open: () -> Unit, mode: SelectionMode): Modifier {
    if (pick == null) return Modifier.combinedClickable(onLongClick = open, onClick = open)
    val single = mode == SelectionMode.Single
    val state = if (single) {
        Modifier.semantics { this.selected = selected }
    } else {
        Modifier.semantics { toggleableState = ToggleableState(selected) }
    }
    return state.combinedClickable(
        role = if (single) Role.RadioButton else Role.Checkbox,
        onLongClick = open,
        onClick = pick,
    )
}

@Composable
private fun UserRowLayout(
    name: String,
    pictureUrl: String?,
    subtitle: String?,
    status: String?,
    modifier: Modifier,
    trailing: @Composable () -> Unit,
) {
    if (ItmoTheme.platformStyle == ItmoPlatformStyle.Ios) {
        IosUserRowLayout(name, pictureUrl, subtitle, status, modifier, trailing)
        return
    }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = ItmoTheme.spacing.touchTarget)
            .padding(horizontal = ItmoTheme.spacing.group, vertical = ItmoTheme.spacing.compact),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(name, pictureUrl, size = AvatarSize)
        Column(
            Modifier
                .weight(1f)
                .padding(start = ItmoTheme.spacing.content, end = ItmoTheme.spacing.compact),
        ) {
            Text(name, color = ItmoTheme.colorScheme.onSurface, style = ItmoTheme.typography.titleMedium)
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    subtitle,
                    Modifier.padding(top = ItmoTheme.spacing.related),
                    color = ItmoTheme.colorScheme.onSurfaceVariant,
                    style = ItmoTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (status != null) {
                Text(
                    status,
                    Modifier.padding(top = ItmoTheme.spacing.related),
                    color = ItmoTheme.colorScheme.onSurfaceVariant,
                    style = ItmoTheme.typography.bodySmall,
                )
            }
        }
        trailing()
    }
}

@Composable
private fun IosUserRowLayout(
    name: String,
    pictureUrl: String?,
    subtitle: String?,
    status: String?,
    modifier: Modifier,
    trailing: @Composable () -> Unit,
) {
    val secondary = ItmoTheme.iosColors.secondaryLabel
    IosListRow(separatorInset = IosMetrics.rowHorizontalPadding + AvatarSize + ItmoTheme.spacing.content) {
        Row(
            modifier
                .fillMaxWidth()
                .heightIn(min = IosMetrics.rowMinHeight)
                .padding(horizontal = IosMetrics.rowHorizontalPadding, vertical = ItmoTheme.spacing.compact),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(name, pictureUrl, size = AvatarSize)
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = ItmoTheme.spacing.content, end = ItmoTheme.spacing.compact),
            ) {
                Text(name, color = ItmoTheme.colorScheme.onSurface, style = ItmoTheme.typography.titleMedium)
                if (!subtitle.isNullOrEmpty()) {
                    Text(
                        subtitle,
                        color = secondary,
                        style = ItmoTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (status != null) Text(status, color = secondary, style = ItmoTheme.typography.bodyMedium)
            }
            trailing()
        }
    }
}

@Composable
private fun ActionButton(action: UserRowAction, style: ProgressButtonStyle, enabled: Boolean) {
    ProgressButton(action.label, action.onClick, style = style, enabled = enabled)
}

@Composable
private fun TrailingIcon(modifier: Modifier, check: Boolean = false) {
    if (ItmoTheme.platformStyle == ItmoPlatformStyle.Ios) {
        if (check) IosCheckmark(visible = true, modifier) else IosDisclosure(modifier)
        return
    }
    Icon(
        painterResource(if (check) Res.drawable.ic_check else Res.drawable.ic_chevron_right),
        contentDescription = null,
        modifier = modifier.size(TrailingIconSize),
        tint = if (check) ItmoTheme.colorScheme.primary else ItmoTheme.colorScheme.onSurfaceVariant,
    )
}

/** The closed row of a [UserSelectionRow] that leads to the person: `item_friend_selector.xml`'s lock. */
@Composable
private fun LockIcon() {
    val tint = if (ItmoTheme.platformStyle == ItmoPlatformStyle.Ios) {
        ItmoTheme.iosColors.secondaryLabel
    } else {
        ItmoTheme.colorScheme.onSurfaceVariant
    }
    Icon(painterResource(Res.drawable.ic_lock), contentDescription = null, Modifier.size(TrailingIconSize), tint = tint)
}

/** `item_user_row.xml`'s 48 dp avatar. */
private val AvatarSize = 48.dp

/** `item_user_row.xml`'s 24 dp chevron. */
private val TrailingIconSize = 24.dp
