package dev.alllexey.itmowidgets.designsystem.components.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.tokens.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.launch

/** One coloured part of a [ScoreRing]: [percentage] of the full turn, 0-100. */
@Immutable
data class ScoreRingSector(val color: Color, val percentage: Float)

/**
 * A ring of coloured sectors over a track, such as the sport score's attendance and bonus, with [content] in the
 * middle. Sectors keep a constant [gapAngle] between them and a visible dot however small they are; sectors that
 * fill the ring keep the gap at the top too, also when the values add up to more than 100. With [animated] every
 * sector fills from its previous value (from zero at first) over `ItmoTheme.motion.progressMillis`; under reduced
 * motion, and without [animated], the ring shows its values at once. The ring itself is silent to TalkBack.
 */
@Composable
fun ScoreRing(
    sectors: List<ScoreRingSector>,
    modifier: Modifier = Modifier,
    thickness: Dp = DefaultThickness,
    trackColor: Color = ItmoTheme.colorScheme.surfaceContainerHighest,
    gapAngle: Float = DefaultGapAngle,
    animated: Boolean = false,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val targets = sectors.map { it.percentage }
    val closedRing = isClosedRing(targets)
    val values = rememberSectorValues(targets, animate = animated && !rememberReducedMotion())
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = thickness.toPx()
            val radius = (min(size.width, size.height) - stroke) / 2f
            if (radius <= 0f) return@Canvas
            drawCircle(trackColor, radius, style = Stroke(stroke))
            val capAngle = roundedCapAngle(radius, stroke)
            val frame = values.map { it.value }
            ringSegments(frame, gapAngle, capAngle, closedRing).forEach { segment ->
                val color = sectors.getOrNull(segment.sector)?.color ?: return@forEach
                drawSegment(segment, color, radius, stroke, capAngle)
            }
        }
        content()
    }
}

/**
 * One [Animatable] per sector. A new sector count starts a new set, from zero when animating, which is what the View
 * did when the sport score went from none to some.
 */
@Composable
private fun rememberSectorValues(targets: List<Float>, animate: Boolean): List<Animatable<Float, AnimationVector1D>> {
    val values = remember(targets.size) { targets.map { Animatable(if (animate) 0f else it) } }
    val durationMillis = ItmoTheme.motion.progressMillis
    LaunchedEffect(values, targets, animate) {
        values.forEachIndexed { index, value ->
            val target = targets[index]
            launch {
                if (animate) value.animateTo(target, tween(durationMillis, easing = FillEasing)) else value.snapTo(target)
            }
        }
    }
    return values
}

private fun DrawScope.drawSegment(segment: RingSegment, color: Color, radius: Float, stroke: Float, capAngle: Float) {
    if (segment.dot) {
        val angle = (segment.start + segment.sweep / 2f) * RADIANS_PER_DEGREE
        val dotCenter = Offset(center.x + radius * cos(angle), center.y + radius * sin(angle))
        drawCircle(color, stroke / 2f, dotCenter)
    } else {
        drawArc(
            color = color,
            startAngle = segment.start + capAngle,
            sweepAngle = segment.sweep - capAngle * 2f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}

private const val RADIANS_PER_DEGREE = (PI / 180.0).toFloat()

/** `view_sport_circle.xml`'s `progressBarThickness`; the recordbook card uses 8 dp. */
private val DefaultThickness = 12.dp

/** `sectorGapAngle="4"` of both View rings. */
private const val DefaultGapAngle = 4f

/** `CircularProgressBar.animateSectors`'s `PathInterpolator(.31, .09, .2, .99)`. */
private val FillEasing = CubicBezierEasing(0.31f, 0.09f, 0.2f, 0.99f)
