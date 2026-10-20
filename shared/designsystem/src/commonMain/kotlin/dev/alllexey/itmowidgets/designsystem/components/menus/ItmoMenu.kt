package dev.alllexey.itmowidgets.designsystem.components.menus

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/**
 * One action of an [ItmoMenu]: [label], an optional trailing [icon] (decorative: the label says it), what it does.
 * [destructive] draws it in the error colour; a disabled item stays visible and does nothing.
 */
@Immutable
data class ItmoMenuItem(
    val label: String,
    val onClick: () -> Unit,
    val icon: Painter? = null,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
)

/**
 * A menu of actions anchored to its parent (material3 `DropdownMenu`), shown while [expanded]. [groups] are drawn in
 * order with a separator between two groups (`listOf(items)` for one). Choosing an item calls [onDismissRequest] and
 * then runs the item, so the caller stops showing the menu as it does for a tap outside or back.
 */
@Composable
fun ItmoMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    groups: List<List<ItmoMenuItem>>,
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
    groups: List<List<ItmoMenuItem>>,
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
private fun MaterialMenuItems(groups: List<List<ItmoMenuItem>>, onDismissRequest: () -> Unit) {
    groups.forEachIndexed { index, group ->
        if (index > 0) HorizontalDivider(Modifier.padding(vertical = MenuVerticalPadding))
        group.forEach { item ->
            val error = ItmoTheme.colorScheme.error
            DropdownMenuItem(
                text = { Text(item.label) },
                onClick = {
                    onDismissRequest()
                    item.onClick()
                },
                trailingIcon = item.icon?.let { icon -> { Icon(icon, contentDescription = null) } },
                enabled = item.enabled,
                colors = if (item.destructive) {
                    MenuDefaults.itemColors(textColor = error, trailingIconColor = error)
                } else {
                    MenuDefaults.itemColors()
                },
            )
        }
    }
}

/** `DropdownMenu`'s own padding above the first and below the last item, which the panel repeats. */
private val MenuVerticalPadding = 8.dp
