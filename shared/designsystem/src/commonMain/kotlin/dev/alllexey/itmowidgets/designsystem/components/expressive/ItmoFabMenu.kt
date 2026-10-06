package dev.alllexey.itmowidgets.designsystem.components.expressive

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.alllexey.itmowidgets.designsystem.components.bars.IOS_PRESSED_ALPHA
import dev.alllexey.itmowidgets.designsystem.components.menus.ItmoMenu
import dev.alllexey.itmowidgets.designsystem.components.menus.ItmoMenuItem
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.tokens.IosMetrics
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_close
import org.jetbrains.compose.resources.painterResource

/** One action of an [ItmoFabMenu]: its label, its icon and what it does. */
@Immutable
data class ItmoFabMenuItem(
    val label: String,
    val icon: Painter,
    val onClick: () -> Unit,
)

/**
 * A FAB that opens a menu of [items] above it (`FloatingActionButtonMenu`), for a screen whose floating actions would
 * otherwise stack: the toggle shows [icon] while collapsed and a close mark while [expanded]. [label] is the toggle's
 * TalkBack label. Choosing an item collapses the menu ([onExpandedChange] with false) and then runs the item.
 * Expressive in both states of the theme's switch: Material has no other form of it.
 *
 * Under the iOS style the toggle is a large filled capsule button, round, in the tint, and the items open in the iOS
 * [ItmoMenu] with their icons trailing.
 */
@Composable
fun ItmoFabMenu(
    items: List<ItmoFabMenuItem>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    icon: Painter,
    label: String,
    modifier: Modifier = Modifier,
) {
    when (ItmoTheme.platformStyle) {
        ItmoPlatformStyle.Material -> MaterialFabMenu(items, expanded, onExpandedChange, icon, label, modifier)
        ItmoPlatformStyle.Ios -> Box(modifier) {
            IosFabToggle(expanded, onExpandedChange, icon, label)
            ItmoMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) },
                groups = listOf(items.map { ItmoMenuItem(it.label, it.onClick, icon = it.icon) }),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MaterialFabMenu(
    items: List<ItmoFabMenuItem>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    icon: Painter,
    label: String,
    modifier: Modifier,
) {
    val close = painterResource(Res.drawable.ic_close)
    FloatingActionButtonMenu(
        expanded = expanded,
        modifier = modifier,
        button = {
            ToggleFloatingActionButton(
                checked = expanded,
                onCheckedChange = onExpandedChange,
                modifier = Modifier.semantics { contentDescription = label },
            ) {
                Icon(
                    if (checkedProgress > HALF) close else icon,
                    contentDescription = null,
                    modifier = Modifier.animateIcon({ checkedProgress }),
                )
            }
        },
    ) {
        items.forEach { item ->
            FloatingActionButtonMenuItem(
                onClick = {
                    onExpandedChange(false)
                    item.onClick()
                },
                text = { Text(item.label) },
                icon = { Icon(item.icon, contentDescription = null) },
            )
        }
    }
}

/**
 * The iOS toggle: [icon], or the close mark while [expanded], in `onPrimary` on a round filled button as tall as a
 * large `UIButton` (`IosMetrics.buttonLargeHeight`), dimmed while pressed. Toggle semantics and the label as
 * Material's toggle has them.
 */
@Composable
private fun IosFabToggle(expanded: Boolean, onExpandedChange: (Boolean) -> Unit, icon: Painter, label: String) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier
            .toggleable(expanded, interaction, indication = null, onValueChange = onExpandedChange)
            .semantics { contentDescription = label }
            .graphicsLayer { alpha = if (pressed) IOS_PRESSED_ALPHA else 1f }
            .size(IosMetrics.buttonLargeHeight)
            .background(ItmoTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (expanded) painterResource(Res.drawable.ic_close) else icon,
            contentDescription = null,
            tint = ItmoTheme.colorScheme.onPrimary,
        )
    }
}

private const val HALF = 0.5f
