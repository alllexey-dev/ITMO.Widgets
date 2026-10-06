package dev.alllexey.itmowidgets.designsystem.components.expressive

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter

/** One action of an [ItmoFloatingToolbar]: an icon with its TalkBack label. */
@Immutable
data class ItmoToolbarAction(
    val label: String,
    val icon: Painter,
    val onClick: () -> Unit,
)

/**
 * A floating toolbar of more than two icon [actions] over the content (`HorizontalFloatingToolbar`), never docked
 * together with the navigation bar. Expressive in both states of the theme's switch: Material has no other form of it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ItmoFloatingToolbar(
    actions: List<ItmoToolbarAction>,
    modifier: Modifier = Modifier,
) {
    require(actions.size > 2) { "A floating toolbar is for more than two actions, got ${actions.size}" }
    HorizontalFloatingToolbar(expanded = true, modifier = modifier) {
        actions.forEach { action ->
            IconButton(onClick = action.onClick) { Icon(action.icon, contentDescription = action.label) }
        }
    }
}
