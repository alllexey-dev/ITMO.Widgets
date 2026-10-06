package dev.alllexey.itmowidgets.designsystem.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.tokens.IosMetrics
import dev.alllexey.itmowidgets.designsystem.tokens.ItmoSpacing

// Goldens of the platform style beside the token previews: the active style's spacing and UIKit's metrics as the iOS
// variants draw them. Labels are token names, not user-visible text.

/** The active style's spacing as bars, so the iOS set's margin, padding and touch target show next to Material's. */
@Preview(heightDp = 900)
@Composable
private fun PlatformStyleSpacingPreview() = ItmoPreview {
    MetricColumn {
        Label("style ${ItmoTheme.platformStyle.name}")
        spacingSlots(ItmoTheme.spacing).forEach { (name, value) -> Bar(name, value) }
    }
}

/**
 * UIKit's metrics: an inset group of three rows with inset separators on the grouped background, then the radii and
 * heights of sheets, alerts, menus, segmented controls and buttons.
 */
@Preview(heightDp = 1200)
@Composable
private fun PlatformStyleMetricsPreview() = ItmoPreview {
    val colors = ItmoTheme.iosColors
    Column(Modifier.background(colors.groupedBackground).padding(vertical = ItmoTheme.spacing.group)) {
        Column(
            Modifier
                .padding(horizontal = IosMetrics.insetGroupMargin)
                .fillMaxWidth()
                .clip(RoundedCornerShape(IosMetrics.insetGroupRadius))
                .background(colors.groupedCell),
        ) {
            listOf("rowMinHeight", "separatorInset", "separatorInsetWithIcon").forEachIndexed { index, name ->
                if (index > 0) {
                    val inset = if (index == 2) IosMetrics.separatorInsetWithIcon else IosMetrics.separatorInset
                    Box(
                        Modifier
                            .padding(start = inset, end = IosMetrics.separatorTrailingInset)
                            .fillMaxWidth()
                            .height(IosMetrics.separatorThickness)
                            .background(colors.separator),
                    )
                }
                Box(
                    Modifier
                        .heightIn(min = IosMetrics.rowMinHeight)
                        .padding(horizontal = IosMetrics.rowHorizontalPadding),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(name, color = ItmoTheme.colorScheme.onSurface, style = ItmoTheme.typography.bodyLarge)
                }
            }
        }
        MetricColumn {
            Radius("sheetRadius", RoundedCornerShape(IosMetrics.sheetRadius, IosMetrics.sheetRadius, 0.dp, 0.dp))
            Radius("alertRadius", RoundedCornerShape(IosMetrics.alertRadius), width = IosMetrics.alertWidth / 2)
            Radius("menuRadius", RoundedCornerShape(IosMetrics.menuRadius), height = IosMetrics.menuRowHeight * 2)
            Radius("segmentedHeight", CircleShape, height = IosMetrics.segmentedHeight)
            Radius("buttonHeight", CircleShape, height = IosMetrics.buttonHeight)
            Radius("buttonLargeHeight", CircleShape, height = IosMetrics.buttonLargeHeight)
            Radius("alertButtonHeight", CircleShape, height = IosMetrics.alertButtonHeight)
        }
    }
}

@Composable
private fun MetricColumn(content: @Composable () -> Unit) {
    Column(
        Modifier.padding(ItmoTheme.spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact),
    ) { content() }
}

@Composable
private fun Label(text: String) {
    Text(text, color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.labelSmall)
}

@Composable
private fun Bar(name: String, value: Dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = value, height = BarHeight).background(ItmoTheme.colorScheme.primary))
        Text(
            "$name ${value.value.toInt()}",
            Modifier.padding(start = ItmoTheme.spacing.content),
            color = ItmoTheme.colorScheme.onSurface,
            style = ItmoTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun Radius(name: String, shape: Shape, width: Dp = SampleWidth, height: Dp = SampleHeight) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(width).height(height).clip(shape).background(ItmoTheme.colorScheme.primaryContainer))
        Text(
            name,
            Modifier.padding(start = ItmoTheme.spacing.content),
            color = ItmoTheme.colorScheme.onSurface,
            style = ItmoTheme.typography.bodySmall,
        )
    }
}

private fun spacingSlots(spacing: ItmoSpacing): List<Pair<String, Dp>> = with(spacing) {
    listOf(
        "related" to related,
        "compact" to compact,
        "content" to content,
        "group" to group,
        "section" to section,
        "screenMargin" to screenMargin,
        "cardPadding" to cardPadding,
        "summaryPadding" to summaryPadding,
        "touchTarget" to touchTarget,
        "statePadding" to statePadding,
        "stateIcon" to stateIcon,
        "stateInlineIcon" to stateInlineIcon,
    )
}

private val BarHeight = 12.dp
private val SampleWidth = 120.dp
private val SampleHeight = 80.dp
