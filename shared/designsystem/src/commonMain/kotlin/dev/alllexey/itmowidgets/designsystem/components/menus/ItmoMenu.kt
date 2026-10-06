package dev.alllexey.itmowidgets.designsystem.components.menus

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.tokens.IosMetrics

/**
 * One action of an [ItmoMenu]: [label], an optional trailing [icon] (decorative: the label says it), what it does.
 * [destructive] draws it in the error colour (system red on iOS); a disabled item stays visible and does nothing.
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
 *
 * Under Material it is today's menu. Under the iOS style it is a context-menu panel: [IosMetrics.menuWidth] wide with
 * [IosMetrics.menuRadius] corners on the opaque overlay colour, rows in body text with the icons trailing, red for a
 * destructive item.
 */
@Composable
fun ItmoMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    groups: List<List<ItmoMenuItem>>,
    modifier: Modifier = Modifier,
) {
    when (ItmoTheme.platformStyle) {
        ItmoPlatformStyle.Material -> DropdownMenu(expanded, onDismissRequest, modifier) {
            MaterialMenuItems(groups, onDismissRequest)
        }
        ItmoPlatformStyle.Ios -> DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = modifier.width(IosMetrics.menuWidth),
            shape = RoundedCornerShape(IosMetrics.menuRadius),
            containerColor = ItmoTheme.iosColors.overlay,
            tonalElevation = 0.dp,
            shadowElevation = IosMenuShadow,
        ) {
            IosMenuItems(groups, onDismissRequest)
        }
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
    when (ItmoTheme.platformStyle) {
        ItmoPlatformStyle.Material -> Surface(
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
        ItmoPlatformStyle.Ios -> Surface(
            modifier.width(IosMetrics.menuWidth),
            shape = RoundedCornerShape(IosMetrics.menuRadius),
            color = ItmoTheme.iosColors.overlay,
            shadowElevation = IosMenuShadow,
        ) {
            Column(Modifier.padding(vertical = MenuVerticalPadding)) { IosMenuItems(groups, onDismissRequest) }
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

@Composable
private fun IosMenuItems(groups: List<List<ItmoMenuItem>>, onDismissRequest: () -> Unit) {
    groups.forEachIndexed { index, group ->
        if (index > 0) {
            Box(
                Modifier
                    .padding(horizontal = IosMetrics.rowHorizontalPadding, vertical = IosGroupGap)
                    .fillMaxWidth()
                    .height(IosMetrics.separatorThickness)
                    .background(ItmoTheme.iosColors.separator),
            )
        }
        group.forEach { item -> IosMenuRow(item, onDismissRequest) }
    }
}

/** `_UIContextMenuCell`: a row at least a touch target high, highlighted on the fill while pressed. */
@Composable
private fun IosMenuRow(item: ItmoMenuItem, onDismissRequest: () -> Unit) {
    val colors = ItmoTheme.iosColors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val content = when {
        !item.enabled -> colors.tertiaryLabel
        item.destructive -> colors.systemRed
        else -> colors.label
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = maxOf(IosMetrics.menuRowHeight, ItmoTheme.spacing.touchTarget))
            .background(if (pressed) colors.systemFill else Color.Transparent)
            // No role, as material3's menu item has none: TalkBack reads both the same way.
            .clickable(interaction, indication = null, enabled = item.enabled) {
                onDismissRequest()
                item.onClick()
            }
            .padding(horizontal = IosMetrics.rowHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(item.label, Modifier.weight(1f), color = content, style = ItmoTheme.typography.bodyLarge)
        if (item.icon != null) {
            Icon(
                item.icon,
                contentDescription = null,
                modifier = Modifier.padding(start = IosIconGap).size(IosIconSize),
                tint = content,
            )
        }
    }
}

/** `DropdownMenu`'s own padding above the first and below the last item, which the panel repeats. */
private val MenuVerticalPadding = 8.dp

/**
 * The iOS panel's shadow: the glass platter's depth without glass. Not measured (the simulator draws the platter
 * without its shape; `IosMetrics.menuRadius`).
 */
private val IosMenuShadow = 12.dp

/** Above and below a group separator of the iOS panel; not measured. */
private val IosGroupGap = 4.dp

/** A row's trailing icon: a Material Symbol in the box of a 17 pt SF Symbol line. */
private val IosIconSize = 22.dp

/** Between a row's label and its trailing icon. */
private val IosIconGap = 12.dp
