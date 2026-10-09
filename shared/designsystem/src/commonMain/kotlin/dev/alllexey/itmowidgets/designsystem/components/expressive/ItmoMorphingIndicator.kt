package dev.alllexey.itmowidgets.designsystem.components.expressive

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import dev.alllexey.itmowidgets.designsystem.tokens.rememberReducedMotion
import kotlin.math.floor

/**
 * The app's indeterminate expressive loading indicator: Material's `LoadingIndicator` layout (a [containerColor]
 * circle of `LoadingIndicatorDefaults.ContainerWidth`, the shape at `IndicatorSize`, fitted as Material fits it) with
 * its own motion ([ItmoLoadingMotion]). The shape morphs through [polygons] and turns about its own centre
 * ([LoadingMorphs.centre]), never about the morph's changing bounds. Under reduced motion it rests on the first shape. TalkBack and VoiceOver read an indeterminate progress, as Material's.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ItmoMorphingIndicator(
    color: Color,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Unspecified,
    polygons: List<RoundedPolygon> = ItmoLoadingShapes.indeterminate,
) {
    val shapes = remember(polygons) { LoadingMorphs(polygons) }
    val shapeScale = remember(polygons) { ItmoLoadingShapes.fitScale(polygons) * ACTIVE_SCALE }
    val steps = rememberSteps(shapes.size)
    Box(
        modifier
            .progressSemantics()
            .size(LoadingIndicatorDefaults.ContainerWidth, LoadingIndicatorDefaults.ContainerHeight)
            .fillMaxSize()
            .clip(LoadingIndicatorDefaults.containerShape)
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Spacer(
            Modifier
                .aspectRatio(1f, matchHeightConstraintsFirst = true)
                .drawWithCache {
                    val path = Path()
                    onDrawBehind {
                        val frame = ItmoLoadingMotion.frame(steps.value, shapes.size)
                        shapes.toPath(frame, path, center, size.minDimension * shapeScale)
                        rotate(frame.degrees) { drawPath(path, color) }
                    }
                },
        )
    }
}

/** The indicator's time in steps, read in the draw layer only; zero (the first shape at rest) under reduced motion. */
@Composable
private fun rememberSteps(shapeCount: Int): State<Float> {
    if (rememberReducedMotion()) return remember { AtRest }
    val cycle = ItmoLoadingMotion.cycleSteps(shapeCount)
    return rememberInfiniteTransition(label = "loadingIndicator").animateFloat(
        initialValue = ItmoLoadingMotion.START_STEPS,
        targetValue = ItmoLoadingMotion.START_STEPS + cycle,
        animationSpec = infiniteRepeatable(tween(cycle * ItmoLoadingMotion.STEP_MILLIS, easing = LinearEasing)),
        label = "loadingSteps",
    )
}

private object AtRest : State<Float> {
    override val value: Float = 0f
}

/** One frame of the indicator: the morph from shape [morphIndex] to the next at [morphProgress], turned [degrees]. */
@Immutable
internal data class LoadingFrame(val morphIndex: Int, val morphProgress: Float, val degrees: Float)

/**
 * The indicator's motion, the web design system's (`@alllexey/ui` `loading.ts`): every [STEP_MILLIS] the shape morphs
 * into the next one and turns [STEP_DEGREES] clockwise, both on the same [StepEasing] of the step's time. A step
 * starts exactly where the last one ended (the morph at its end is the next morph's start, the angle carries over),
 * so nothing snaps at the boundaries; Material's own indicator ends each step's spring short of its target and jumps
 * the rest, which reads as a twitch every 650 ms.
 */
internal object ItmoLoadingMotion {
    /** Material's morph interval. */
    const val STEP_MILLIS = 650

    /** The web indicator's turn per shape: Material's quarter turn plus its global rotation's share of a step. */
    const val STEP_DEGREES = 140f

    /** The web indicator's curve: a quick start, a slight overshoot of the angle (1.4 %) and a soft landing. */
    val StepEasing = CubicBezierEasing(0.38f, 1.21f, 0.22f, 1f)

    /**
     * Where the indicator's time starts: 30 % before the first step, in the still tail of the step that lands on the
     * first shape (1.3 degrees past its angle, the curve's overshoot), so the shape rests for 195 ms before it first
     * moves. The pull-to-refresh hand-off cross-fades the pulled shape into a still one, not into one already turning
     * at full speed (a double image).
     */
    const val START_STEPS = -0.3f

    /** Steps after which the angle repeats modulo a full turn: 18 * 140 = 7 * 360 degrees. */
    private const val TURN_STEPS = 18

    /** Steps after which both the shape and the angle repeat, so the infinite animation restarts seamlessly. */
    fun cycleSteps(shapeCount: Int): Int = lcm(shapeCount, TURN_STEPS)

    /** The frame [steps] steps after the start; the angle is continuous across steps modulo a full turn. */
    fun frame(steps: Float, shapeCount: Int): LoadingFrame {
        val step = floor(steps).toInt()
        val eased = StepEasing.transform(steps - step)
        return LoadingFrame(
            morphIndex = step.mod(shapeCount),
            morphProgress = eased.coerceIn(0f, 1f),
            degrees = (step.mod(TURN_STEPS) + eased) * STEP_DEGREES,
        )
    }

    private fun lcm(a: Int, b: Int): Int = a / gcd(a, b) * b

    private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
}

/**
 * The circular morph sequence over the normalized [polygons] (the last shape morphs back into the first) and the
 * centre each in-between shape turns about: its centroid. `LoadingIndicator` centres the morph's control-point bounds
 * instead, and so does `normalized()`, which misses Cookie12Sided's centre by half a percent; the shapes in between two
 * even-symmetric ones are not exactly symmetric either (up to 0.2 %).
 */
internal class LoadingMorphs(polygons: List<RoundedPolygon>) {
    private val shapes = polygons.map { it.normalized() }

    init {
        require(shapes.size > 1) { "a loading indicator morphs between at least two shapes" }
    }

    val size: Int get() = shapes.size

    val morphs: List<Morph> = shapes.indices.map { Morph(shapes[it], shapes[(it + 1) % shapes.size]) }

    /**
     * The centroid of morph [index] at [progress] by Green's theorem over its closed outline: `A` is the integral of
     * `x dy`, `Cx` that of `x^2 dy` over `2A`, `Cy` that of `-y^2 dx` over `2A`. On a cubic each integrand is a
     * polynomial of degree 8 at most, which five-point Gauss-Legendre quadrature integrates exactly.
     */
    fun centre(index: Int, progress: Float): Offset {
        var area = 0f
        var momentX = 0f
        var momentY = 0f
        morphs[index].forEachCubic(progress) { cubic ->
            GAUSS_NODES.forEachIndexed { node, u ->
                val t = (u + 1f) / 2f
                val weight = GAUSS_WEIGHTS[node] / 2f
                val x = bezier(cubic.anchor0X, cubic.control0X, cubic.control1X, cubic.anchor1X, t)
                val y = bezier(cubic.anchor0Y, cubic.control0Y, cubic.control1Y, cubic.anchor1Y, t)
                val dx = bezierSlope(cubic.anchor0X, cubic.control0X, cubic.control1X, cubic.anchor1X, t)
                val dy = bezierSlope(cubic.anchor0Y, cubic.control0Y, cubic.control1Y, cubic.anchor1Y, t)
                area += weight * x * dy
                momentX += weight * x * x * dy
                momentY -= weight * y * y * dx
            }
        }
        return Offset(momentX / (2 * area), momentY / (2 * area))
    }

    /** [frame]'s shape as a path [scale] px across, its centre on [centre]. */
    fun toPath(frame: LoadingFrame, path: Path, centre: Offset, scale: Float): Path {
        val origin = centre(frame.morphIndex, frame.morphProgress)
        fun x(value: Float) = centre.x + (value - origin.x) * scale
        fun y(value: Float) = centre.y + (value - origin.y) * scale
        path.rewind()
        var first = true
        morphs[frame.morphIndex].forEachCubic(frame.morphProgress) { cubic ->
            if (first) {
                path.moveTo(x(cubic.anchor0X), y(cubic.anchor0Y))
                first = false
            }
            path.cubicTo(
                x(cubic.control0X),
                y(cubic.control0Y),
                x(cubic.control1X),
                y(cubic.control1Y),
                x(cubic.anchor1X),
                y(cubic.anchor1Y),
            )
        }
        path.close()
        return path
    }

    private fun bezier(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
        val u = 1 - t
        return u * u * u * p0 + 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t * p3
    }

    private fun bezierSlope(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
        val u = 1 - t
        return 3 * u * u * (p1 - p0) + 6 * u * t * (p2 - p1) + 3 * t * t * (p3 - p2)
    }

    private companion object {
        val GAUSS_NODES = floatArrayOf(-0.9061798f, -0.5384693f, 0f, 0.5384693f, 0.9061798f)
        val GAUSS_WEIGHTS = floatArrayOf(0.23692689f, 0.47862867f, 0.56888889f, 0.47862867f, 0.23692689f)
    }
}

/** Material's `ActiveIndicatorScale`: the shape's size within the container. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val ACTIVE_SCALE = LoadingIndicatorDefaults.IndicatorSize / LoadingIndicatorDefaults.ContainerWidth
