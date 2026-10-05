package dev.alllexey.itmowidgets.designsystem.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/** A threshold on a [GradeScale]: a tick at [score] (0-100) with an optional short [label] under it. */
@Immutable
data class GradeScaleTick(val score: Double, val label: String)

/**
 * A 0-100 bar filled to [score] in [fillColor], with the grade thresholds as ticks and their labels under them. The
 * ticks sit at their share of the width at any font scale; labels near the edges stay inside. A missing or
 * non-finite score leaves the track empty, a score outside 0-100 is clamped for drawing. TalkBack skips it: the
 * score is said in text next to it.
 */
@Composable
fun GradeScale(
    score: Double?,
    ticks: List<GradeScaleTick>,
    fillColor: Color,
    modifier: Modifier = Modifier,
) {
    val trackColor = ItmoTheme.colorScheme.surfaceContainerHighest
    val inkColor = ItmoTheme.colorScheme.onSurfaceVariant
    val labelStyle = ItmoTheme.typography.labelSmall.copy(
        color = inkColor,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
    )
    val measurer = rememberTextMeasurer()
    val hasLabels = ticks.any { it.label.isNotEmpty() }
    val labelHeight = remember(measurer, labelStyle, hasLabels) {
        if (hasLabels) measurer.measure(LINE_PROBE, labelStyle).size.height else 0
    }
    val labelsDp = with(LocalDensity.current) { if (hasLabels) LabelGap + labelHeight.toDp() else 0.dp }
    val value = score?.takeIf { it.isFinite() }
    Canvas(modifier.fillMaxWidth().height(TickOverhang * 2 + TrackHeight + labelsDp)) {
        val overhang = TickOverhang.toPx()
        val track = TrackHeight.toPx()
        val radius = CornerRadius(track / 2f)
        val top = overhang
        drawRoundRect(trackColor, Offset(0f, top), Size(size.width, track), radius)
        val share = ((value ?: 0.0) / MAX_SCORE).coerceIn(0.0, 1.0).toFloat()
        if (value != null && share > 0f) {
            drawRoundRect(fillColor, Offset(0f, top), Size(maxOf(track, size.width * share), track), radius)
        }
        val tickWidth = TickWidth.toPx()
        val labelTop = top + track + overhang + LabelGap.toPx()
        ticks.forEach { tick ->
            val x = size.width * (tick.score / MAX_SCORE).coerceIn(0.0, 1.0).toFloat()
            drawRect(inkColor, Offset(x - tickWidth / 2f, 0f), Size(tickWidth, track + overhang * 2f))
            if (tick.label.isNotEmpty()) {
                val layout = measurer.measure(tick.label, labelStyle)
                val half = layout.size.width / 2f
                val centerX = if (size.width > half * 2f) x.coerceIn(half, size.width - half) else size.width / 2f
                drawText(layout, topLeft = Offset(centerX - half, labelTop))
            }
        }
    }
}

private const val MAX_SCORE = 100.0

/** Measures one line of the label style. */
private const val LINE_PROBE = "0"

// `GradeScaleView`'s geometry, kept at parity.
private val TrackHeight = 8.dp
private val TickWidth = 2.dp
private val TickOverhang = 3.dp
private val LabelGap = 4.dp
