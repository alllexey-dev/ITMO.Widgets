package dev.alllexey.itmowidgets.designsystem.components.buttons

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/**
 * Buttons side by side across the full width, the first one taking the room the others leave, or stacked at full
 * width when their labels do not fit in one row (a large font scale on a narrow screen), so no label breaks inside a
 * word. [spacing] is the gap between them either way. Port of `core/ui/ButtonRow.kt`; the choice depends on the width
 * alone, so it never flips back and forth while measuring.
 */
@Composable
fun ButtonRow(
    modifier: Modifier = Modifier,
    spacing: Dp = ItmoTheme.spacing.compact,
    content: @Composable () -> Unit,
) {
    Layout(content, modifier) { measurables, constraints ->
        val gap = spacing.roundToPx()
        val natural = measurables.map { it.maxIntrinsicWidth(Constraints.Infinity) }
        val stacked = constraints.hasBoundedWidth && natural.sum() + gaps(natural.size, gap) > constraints.maxWidth
        if (stacked) stack(measurables, constraints, gap) else row(measurables, natural, constraints, gap)
    }
}

private fun MeasureScope.row(
    measurables: List<Measurable>,
    natural: List<Int>,
    constraints: Constraints,
    gap: Int,
): MeasureResult {
    val width = if (constraints.hasBoundedWidth) constraints.maxWidth else natural.sum() + gaps(natural.size, gap)
    val rest = natural.drop(1).sum() + gaps(natural.size, gap)
    val widths = natural.mapIndexed { index, own -> if (index == 0) (width - rest).coerceAtLeast(own) else own }
    val placeables = measurables.mapIndexed { index, measurable ->
        measurable.measure(Constraints.fixedWidth(widths[index]).copy(maxHeight = constraints.maxHeight))
    }
    val height = (placeables.maxOfOrNull(Placeable::height) ?: 0).coerceIn(constraints.minHeight, constraints.maxHeight)
    return layout(width.coerceAtLeast(constraints.minWidth), height) {
        var x = 0
        placeables.forEach { placeable ->
            placeable.placeRelative(x, (height - placeable.height) / 2)
            x += placeable.width + gap
        }
    }
}

private fun MeasureScope.stack(
    measurables: List<Measurable>,
    constraints: Constraints,
    gap: Int,
): MeasureResult {
    val width = constraints.maxWidth
    val placeables = measurables.map { it.measure(Constraints.fixedWidth(width)) }
    val content = placeables.sumOf(Placeable::height) + gaps(placeables.size, gap)
    return layout(width, content.coerceIn(constraints.minHeight, constraints.maxHeight)) {
        var y = 0
        placeables.forEach { placeable ->
            placeable.placeRelative(0, y)
            y += placeable.height + gap
        }
    }
}

private fun gaps(count: Int, gap: Int): Int = gap * (count - 1).coerceAtLeast(0)
