package dev.alllexey.itmowidgets.designsystem.components.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/**
 * The bottom navigation bar of the root screens: Material 3 Expressive's `ShortNavigationBar` with
 * [ItmoNavigationBarItem]s of equal width on `surfaceContainer`, [NavigationBarTokens.MinHeight] tall, padded by
 * [windowInsets] inside its background. Android only: iOS has its own tab bar.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ItmoNavigationBar(
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.navigationBars,
    content: @Composable () -> Unit,
) {
    ShortNavigationBar(
        modifier = modifier,
        containerColor = ItmoTheme.colorScheme.surfaceContainer,
        windowInsets = windowInsets,
        content = content,
    )
}

/**
 * One tab of [ItmoNavigationBar]: [icon], or [selectedIcon] (the FILL 1 variant) in the active indicator while
 * [selected], over its label on every tab (M3E labels every destination); a long label ellipsizes. The whole cell is
 * the target and TalkBack reads it as a tab with its label.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ItmoNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    icon: Painter,
    selectedIcon: Painter,
    modifier: Modifier = Modifier,
) {
    ShortNavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(if (selected) selectedIcon else icon, contentDescription = null) },
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier,
    )
}

/** The bar's values, for hosts that keep room for it. */
object NavigationBarTokens {
    /** `ShortNavigationBar`'s container height. */
    val MinHeight = 64.dp
}
