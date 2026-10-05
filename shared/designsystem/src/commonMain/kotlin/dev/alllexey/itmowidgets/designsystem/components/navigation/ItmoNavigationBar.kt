package dev.alllexey.itmowidgets.designsystem.components.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.tokens.rememberReducedMotion

/**
 * The bottom navigation bar of the root screens (`Widget.ItmoWidgets.BottomNavigationView`): [ItmoNavigationBarItem]s
 * of equal width on `surfaceContainer`, at least 64 dp tall, padded by [windowInsets] inside its background.
 */
@Composable
fun ItmoNavigationBar(
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.navigationBars,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(modifier.fillMaxWidth(), color = ItmoTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier
                .windowInsetsPadding(windowInsets)
                .heightIn(min = NavigationBarTokens.MinHeight)
                // Every cell as tall as the tallest, so an unselected tab is a full-height target too.
                .height(IntrinsicSize.Min)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/**
 * One tab of [ItmoNavigationBar]: [icon], or [selectedIcon] (the FILL 1 variant) in the pill of the active indicator
 * while [selected]. The label shows on the selected tab only while [NavigationBarTokens.LabelsOnSelectedOnly] holds;
 * TalkBack reads it as the tab's name either way (the cell's description). The whole cell is the target.
 */
@Composable
fun RowScope.ItmoNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    icon: Painter,
    selectedIcon: Painter,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val motion = ItmoTheme.motion
    val indicator by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else tween(motion.standardMillis, easing = motion.easing),
        label = "indicator",
    )
    val showLabel = selected || !NavigationBarTokens.LabelsOnSelectedOnly
    Column(
        modifier
            .weight(1f)
            .fillMaxHeight()
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .semantics { contentDescription = label }
            .padding(vertical = NavigationBarTokens.ItemVerticalPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(NavigationBarTokens.IndicatorWidth, NavigationBarTokens.IndicatorHeight)
                    .graphicsLayer {
                        scaleX = indicator
                        alpha = indicator
                    }
                    .clip(ItmoTheme.shapes.full)
                    .background(ItmoTheme.colorScheme.secondaryContainer),
            )
            Icon(
                if (selected) selectedIcon else icon,
                contentDescription = null,
                Modifier.size(NavigationBarTokens.IconSize),
                tint = if (selected) ItmoTheme.colorScheme.onSecondaryContainer else ItmoTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showLabel) {
            Spacer(Modifier.height(NavigationBarTokens.IndicatorLabelGap))
            Text(
                label,
                // Alone in the bar, the selected label may run past its cell into its neighbours' empty label space
                // rather than lose letters (`Расписание` at 1.3 in 320 dp); with every label shown it ellipsizes.
                if (NavigationBarTokens.LabelsOnSelectedOnly) Modifier.wrapContentWidth(unbounded = true) else Modifier,
                color = if (selected) ItmoTheme.colorScheme.onSurface else ItmoTheme.colorScheme.onSurfaceVariant,
                style = ItmoTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The bar's values at View parity (`Widget.ItmoWidgets.BottomNavigationView` over MDC 1.13's M3 bar); the M3E token
 * change revisits them here, in one place.
 */
object NavigationBarTokens {
    /** `labelVisibilityMode="selected"` (`activity_main.xml`): no permanent label row. */
    const val LabelsOnSelectedOnly: Boolean = true

    /** `android:minHeight` of the app's style. */
    val MinHeight = 64.dp

    /** `itemPaddingTop` and `itemPaddingBottom` of the app's style (`design_spacing_related`). */
    val ItemVerticalPadding = 4.dp

    val IndicatorWidth = 64.dp
    val IndicatorHeight = 32.dp
    val IconSize = 24.dp

    /** `m3_navigation_item_active_indicator_label_padding`. */
    val IndicatorLabelGap = 4.dp
}
