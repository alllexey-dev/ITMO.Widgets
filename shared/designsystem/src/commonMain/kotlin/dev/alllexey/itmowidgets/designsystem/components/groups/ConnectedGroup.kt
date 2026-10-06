package dev.alllexey.itmowidgets.designsystem.components.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/** What a connected group stands on, which decides the surface of its rows. */
enum class GroupSurface {
    /**
     * A screen's background: rows on `surfaceContainerLow`, the surface of schedule days. Under the iOS style the
     * cells of an inset group, `secondarySystemGroupedBackground` on `systemGroupedBackground`.
     */
    Screen,

    /**
     * A bottom sheet, itself `surfaceContainerLow`: rows one step stronger, on `surfaceContainerHigh`. Under the iOS
     * style the elevated grouped colours of a sheet.
     */
    Sheet,
}

/**
 * One row of a connected group (port of `core/ui/ConnectedGroup.kt`): the rows of a section share one surface, stand
 * 2 dp apart and round 20 dp outside and 4 dp between rows. The row is clipped to that shape, so a `clickable` added
 * after this modifier ripples in it, and an informational row uses the same call.
 *
 * Under the iOS style the group is an inset group as in iOS Settings: one section rounded at
 * `IosMetrics.insetGroupRadius`, rows touching, a hairline separator under every row but the last, inset to the row's
 * text. The kit's rows ([GroupActionRow], `LinkRow`, `UserRow`) set that inset and show the pressed cell instead of a
 * ripple; [position] still decides which corners round and where separators go.
 */
@Composable
fun Modifier.connectedGroupItem(position: GroupPosition, surface: GroupSurface = GroupSurface.Screen): Modifier {
    if (ItmoTheme.platformStyle == ItmoPlatformStyle.Ios) return iosGroupItem(position, surface)
    val shapes = ItmoTheme.shapes
    val top = if (position.isFirst) shapes.groupOuterRadius else shapes.groupInnerRadius
    val bottom = if (position.isLast) shapes.groupOuterRadius else shapes.groupInnerRadius
    val color = when (surface) {
        GroupSurface.Screen -> ItmoTheme.colorScheme.surfaceContainerLow
        GroupSurface.Sheet -> ItmoTheme.colorScheme.surfaceContainerHigh
    }
    return this
        .padding(top = if (position.isFirst) 0.dp else shapes.groupGap)
        .clip(RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom))
        .background(color)
}

/** The iOS inset group's row: no gap, square inner corners from the theme's shapes, the separator below. */
@Composable
private fun Modifier.iosGroupItem(position: GroupPosition, surface: GroupSurface): Modifier {
    val shapes = ItmoTheme.shapes
    val colors = ItmoTheme.iosColors
    val top = if (position.isFirst) shapes.groupOuterRadius else shapes.groupInnerRadius
    val bottom = if (position.isLast) shapes.groupOuterRadius else shapes.groupInnerRadius
    val color = when (surface) {
        GroupSurface.Screen -> colors.groupedCell
        GroupSurface.Sheet -> colors.groupedSheetCell
    }
    val clipped = this
        .clip(RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom))
        .background(color)
    return if (position.isLast) clipped else clipped.iosSeparator(colors.separator)
}
