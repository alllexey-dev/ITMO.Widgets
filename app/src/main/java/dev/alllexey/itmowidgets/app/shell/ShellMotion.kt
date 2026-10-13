package dev.alllexey.itmowidgets.app.shell

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.TransformOrigin
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEvent
import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.designsystem.tokens.ItmoMotion

/**
 * The shell's transitions for `NavDisplay`, from the M3 motion patterns on [ItmoMotion]'s standard scheme. The system
 * animator scale applies to all of them, so "remove animations" makes every switch instant.
 * - A tab switch is a fade through: the old root fades out in 90 ms, the new one fades in over 210 ms after it,
 *   growing from 92 %. Tab roots carry it as entry metadata ([tabRoot]), for the push onto and the pop off the start
 *   tab alike.
 * - A pushed screen comes and goes on the shared X axis: a tenth of the width with a fade through, towards the end on
 *   a push and back on a pop.
 * - The predictive Back follows the finger on Material's predictive back curve: the top screen shrinks to 90 % towards
 *   the side away from the swipe edge and fades out only at the very end, the one under it grows from 95 % and drifts in from the swipe edge, so it is
 *   already in place when the gesture commits.
 */
internal object ShellMotion {
    private val scheme = ItmoMotion.Default.scheme
    private const val FADE_OUT_MILLIS = 90
    private const val FADE_IN_MILLIS = 210
    private const val TAB_INITIAL_SCALE = 0.92f
    private const val AXIS_SHIFT = 10
    private const val BACK_MILLIS = 300
    private const val BACK_FADE_MILLIS = 60
    private const val BACK_TOP_SCALE = 0.9f
    private const val BACK_UNDER_SCALE = 0.95f
    private const val BACK_UNDER_SHIFT = 20

    /** Material's predictive back curve: most of the move in the first part of the gesture. */
    private val PredictiveBackEasing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

    private val fadeThrough: AnimatedContentTransitionScope<Scene<*>>.() -> ContentTransform = {
        val enter = tween<Float>(FADE_IN_MILLIS, delayMillis = FADE_OUT_MILLIS, easing = LinearOutSlowInEasing)
        (fadeIn(enter) + scaleIn(enter, initialScale = TAB_INITIAL_SCALE)) togetherWith
            fadeOut(tween(FADE_OUT_MILLIS, easing = FastOutLinearInEasing))
    }

    /** A tab root's metadata: the fade through on the push onto and the pop off the start tab's root. */
    val tabRoot: Map<String, Any> = metadata {
        put(NavDisplay.TransitionKey, fadeThrough)
        put(NavDisplay.PopTransitionKey, fadeThrough)
    }

    val push: AnimatedContentTransitionScope<Scene<AppRoute>>.() -> ContentTransform = { sharedAxisX(SlideDirection.Start) }

    val pop: AnimatedContentTransitionScope<Scene<AppRoute>>.() -> ContentTransform = { sharedAxisX(SlideDirection.End) }

    val predictivePop: AnimatedContentTransitionScope<Scene<AppRoute>>.(Int) -> ContentTransform = { edge ->
        val fromRight = edge == NavigationEvent.EDGE_RIGHT
        val gesture = tween<Float>(BACK_MILLIS, easing = PredictiveBackEasing)
        val under = scaleIn(gesture, initialScale = BACK_UNDER_SCALE) +
            slideInHorizontally(tween(BACK_MILLIS, easing = PredictiveBackEasing)) { width ->
                (if (fromRight) width else -width) / BACK_UNDER_SHIFT
            }
        val top = scaleOut(gesture, BACK_TOP_SCALE, TransformOrigin(if (fromRight) 0f else 1f, 0.5f)) +
            fadeOut(tween(BACK_FADE_MILLIS, delayMillis = BACK_MILLIS - BACK_FADE_MILLIS, easing = LinearEasing))
        under togetherWith top
    }

    /** M3 shared axis X: both screens move [towards] one side by a tenth of the width and fade through. */
    private fun <T> AnimatedContentTransitionScope<T>.sharedAxisX(towards: SlideDirection): ContentTransform {
        val enter = slideIntoContainer(towards, scheme.defaultSpatialSpec()) { it / AXIS_SHIFT } +
            fadeIn(tween(FADE_IN_MILLIS, delayMillis = FADE_OUT_MILLIS, easing = LinearOutSlowInEasing))
        val exit = slideOutOfContainer(towards, scheme.defaultSpatialSpec()) { it / AXIS_SHIFT } +
            fadeOut(tween(FADE_OUT_MILLIS, easing = FastOutLinearInEasing))
        return enter togetherWith exit
    }
}
