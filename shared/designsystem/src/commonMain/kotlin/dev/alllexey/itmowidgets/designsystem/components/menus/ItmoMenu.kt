package dev.alllexey.itmowidgets.designsystem.components.menus

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_check
import org.jetbrains.compose.resources.painterResource

/**
 * One item of an [ItmoMenu]: [label] with an optional second line [supportingText] (`3 семестр` over
 * `2 курс, 2026/2027`), an optional trailing [icon] (decorative: the label says it), what it does. [selected] marks the
 * current choice of a picker with a check in place of the icon. [destructive] draws it in the error colour; a disabled
 * item stays visible and does nothing.
 */
@Immutable
data class ItmoMenuItem(
    val label: String,
    val onClick: () -> Unit,
    val icon: Painter? = null,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
    val supportingText: String? = null,
    val selected: Boolean = false,
)

/** The items of an [ItmoMenu] between two separators, under an optional [label] (a programme of the record book). */
@Immutable
data class ItmoMenuGroup(val items: List<ItmoMenuItem>, val label: String? = null)

/**
 * The one menu of the app, anchored to its parent (material3 `DropdownMenu`), shown while [expanded]: actions or the
 * choices of a picker. [groups] are drawn in order with a separator between two groups. One-line items keep the 48 dp
 * Material row; an item with supporting text gets Material's 12 dp above and below, so two lines never touch the next
 * item. Choosing an item calls [onDismissRequest] and then runs the item, so the caller stops showing the menu as it
 * does for a tap outside or back.
 */
@Composable
fun ItmoMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    groups: List<ItmoMenuGroup>,
    modifier: Modifier = Modifier,
) {
    DropdownMenu(expanded, onDismissRequest, modifier) {
        MaterialMenuItems(groups, onDismissRequest)
    }
}

/**
 * The open menu without its popup, as [ItmoMenu] draws it: for previews, which cannot capture a popup window, and for
 * tests of the rows.
 */
@Composable
internal fun ItmoMenuPanel(
    groups: List<ItmoMenuGroup>,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier,
        shape = MenuDefaults.shape,
        color = MenuDefaults.containerColor,
        tonalElevation = MenuDefaults.TonalElevation,
        shadowElevation = MenuDefaults.ShadowElevation,
    ) {
        Column(Modifier.padding(vertical = MenuVerticalPadding).width(IntrinsicSize.Max)) {
            MaterialMenuItems(groups, onDismissRequest)
        }
    }
}

@Composable
private fun MaterialMenuItems(groups: List<ItmoMenuGroup>, onDismissRequest: () -> Unit) {
    groups.forEachIndexed { index, group ->
        if (index > 0) HorizontalDivider(Modifier.padding(vertical = MenuVerticalPadding))
        group.label?.let { label ->
            // The items' own side padding, so a long label keeps the same margin at its end.
            MenuDefaults.Label(padding = MenuDefaults.DropdownMenuItemContentPadding) {
                Text(label, color = ItmoTheme.colorScheme.primary)
            }
        }
        group.items.forEach { item -> MaterialMenuItem(item, onDismissRequest) }
    }
}

@Composable
private fun MaterialMenuItem(item: ItmoMenuItem, onDismissRequest: () -> Unit) {
    val error = ItmoTheme.colorScheme.error
    val primary = ItmoTheme.colorScheme.primary
    val trailing = if (item.selected) painterResource(Res.drawable.ic_check) else item.icon
    DropdownMenuItem(
        text = {
            Column {
                Text(item.label)
                item.supportingText?.let { supporting ->
                    Text(
                        supporting,
                        color = if (item.enabled) ItmoTheme.colorScheme.onSurfaceVariant else LocalContentColor.current,
                        style = ItmoTheme.typography.bodyMedium,
                    )
                }
            }
        },
        onClick = {
            onDismissRequest()
            item.onClick()
        },
        modifier = if (item.selected) Modifier.semantics { selected = true } else Modifier,
        trailingIcon = trailing?.let { icon -> { Icon(icon, contentDescription = null) } },
        enabled = item.enabled,
        colors = when {
            item.destructive -> MenuDefaults.itemColors(textColor = error, trailingIconColor = error)
            item.selected -> MenuDefaults.itemColors(trailingIconColor = primary)
            else -> MenuDefaults.itemColors()
        },
        contentPadding = if (item.supportingText != null) {
            MenuDefaults.DropdownMenuSelectableItemContentPadding
        } else {
            MenuDefaults.DropdownMenuItemContentPadding
        },
    )
}

/** `DropdownMenu`'s own padding above the first and below the last item, which the panel repeats. */
private val MenuVerticalPadding = 8.dp
