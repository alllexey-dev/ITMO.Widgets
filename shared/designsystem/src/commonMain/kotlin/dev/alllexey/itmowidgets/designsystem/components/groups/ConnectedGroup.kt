package dev.alllexey.itmowidgets.designsystem.components.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/** What a connected group stands on, which decides the surface of its rows. */
enum class GroupSurface {
    /** A screen's background: rows on `surfaceContainerLow`, the surface of schedule days. */
    Screen,

    /** A bottom sheet, itself `surfaceContainerLow`: rows one step stronger, on `surfaceContainerHigh`. */
    Sheet,
}

/**
 * One row of a connected group (port of `core/ui/ConnectedGroup.kt`): the rows of a section share one surface, stand
 * 2 dp apart and round 20 dp outside and 4 dp between rows. The row is clipped to that shape, so a `clickable` added
 * after this modifier ripples in it, and an informational row uses the same call.
 */
@Composable
fun Modifier.connectedGroupItem(position: GroupPosition, surface: GroupSurface = GroupSurface.Screen): Modifier {
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
