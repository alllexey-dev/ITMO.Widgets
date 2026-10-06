package dev.alllexey.itmowidgets.designsystem.components.expressive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.components.bars.IosBarButton
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

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
 * Under the iOS style it is an opaque capsule on the grouped cell colour with the actions as plain tint icons in
 * 44 pt targets.
 */
@Composable
fun ItmoFloatingToolbar(
    actions: List<ItmoToolbarAction>,
    modifier: Modifier = Modifier,
) {
    require(actions.size > 2) { "A floating toolbar is for more than two actions, got ${actions.size}" }
    when (ItmoTheme.platformStyle) {
        ItmoPlatformStyle.Material -> MaterialToolbar(actions, modifier)
        ItmoPlatformStyle.Ios -> IosToolbar(actions, modifier)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MaterialToolbar(actions: List<ItmoToolbarAction>, modifier: Modifier) {
    HorizontalFloatingToolbar(expanded = true, modifier = modifier) {
        actions.forEach { action ->
            IconButton(onClick = action.onClick) { Icon(action.icon, contentDescription = action.label) }
        }
    }
}

@Composable
private fun IosToolbar(actions: List<ItmoToolbarAction>, modifier: Modifier) {
    Row(
        modifier
            .shadow(IosToolbarElevation, CircleShape)
            .background(ItmoTheme.iosColors.groupedCell, CircleShape)
            .padding(horizontal = IosToolbarPadding, vertical = IosToolbarPadding / 2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEach { action ->
            IosBarButton(action.onClick) {
                Icon(action.icon, contentDescription = action.label, tint = ItmoTheme.colorScheme.primary)
            }
        }
    }
}

/** Lifts the capsule off a white grouped cell without glass; the shadow is the only depth cue. */
private val IosToolbarElevation = 6.dp

/** The capsule's inner padding beside the outer buttons; half of it above and below. */
private val IosToolbarPadding = 8.dp
