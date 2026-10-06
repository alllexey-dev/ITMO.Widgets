package dev.alllexey.itmowidgets.designsystem.components.expressive

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ItmoFabMenu(
    items: List<ItmoFabMenuItem>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    icon: Painter,
    label: String,
    modifier: Modifier = Modifier,
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

private const val HALF = 0.5f
