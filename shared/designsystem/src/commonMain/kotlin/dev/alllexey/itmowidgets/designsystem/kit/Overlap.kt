package dev.alllexey.itmowidgets.designsystem.kit

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The View's negative margins: the content keeps its measured size and target, but reports [top], [bottom] and
 * [start] less to the parent and is drawn shifted by them, so a 48 dp target reaches into the space around it.
 */
fun Modifier.overlap(top: Dp = 0.dp, bottom: Dp = 0.dp, start: Dp = 0.dp): Modifier = layout { measurable, constraints ->
    val vertical = (top + bottom).roundToPx()
    val horizontal = start.roundToPx()
    val placeable = measurable.measure(
        constraints.copy(
            minHeight = 0,
            minWidth = 0,
            maxHeight = if (constraints.hasBoundedHeight) constraints.maxHeight + vertical else constraints.maxHeight,
            maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + horizontal else constraints.maxWidth,
        ),
    )
    val width = (placeable.width - horizontal).coerceAtLeast(0)
    val height = (placeable.height - vertical).coerceAtLeast(0)
    layout(width, height) { placeable.place(-horizontal, -top.roundToPx()) }
}
