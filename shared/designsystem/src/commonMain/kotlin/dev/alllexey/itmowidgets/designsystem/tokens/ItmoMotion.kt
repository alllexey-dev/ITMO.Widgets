package dev.alllexey.itmowidgets.designsystem.tokens

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Durations in milliseconds at the View screens' values (03 §Motion), one easing and Material's [MotionScheme] slot
 * for components. A kit animation shows its end state at once when [rememberReducedMotion] is true.
 */
@Immutable
data class ItmoMotion(
    /** A label that changes in place (the sport month label). */
    val quickMillis: Int,
    /** Screen-level and selection changes: the overlay slide, settings levels, calendar selection. */
    val standardMillis: Int,
    /** A status that changes on screen (the sport status chip). */
    val emphasisMillis: Int,
    /** Content that appears in place (the QR preview reveal). */
    val revealMillis: Int,
    /** A progress ring filling to its value (the sport score ring). */
    val progressMillis: Int,
    /** `CircularProgressBar`'s default fill. */
    val progressSlowMillis: Int,
    /** One half of the skeleton pulse, from [pulseMinAlpha] to opaque. */
    val pulseMillis: Int,
    val pulseMinAlpha: Float,
    /** Material's standard easing `(0.2, 0, 0, 1)`. */
    val easing: Easing,
    /** What Material components animate with: `MotionScheme.standard()` until the M3E token change. */
    val scheme: MotionScheme,
) {
    companion object {
        /** [easing]'s control points, for the token export. */
        internal val EasingControlPoints = listOf(0.2f, 0f, 0f, 1f)

        val Default = ItmoMotion(
            quickMillis = 180,
            standardMillis = 220,
            emphasisMillis = 260,
            revealMillis = 300,
            progressMillis = 700,
            progressSlowMillis = 1000,
            pulseMillis = 1200,
            pulseMinAlpha = 0.55f,
            easing = EasingControlPoints.let { (a, b, c, d) -> CubicBezierEasing(a, b, c, d) },
            scheme = MotionScheme.standard(),
        )
    }
}

/**
 * True when the user turned animations off (Android's animator duration scale 0, iOS's Reduce Motion); a kit
 * animation then shows its end state at once.
 */
@Composable
expect fun rememberReducedMotion(): Boolean

internal val LocalItmoMotion = staticCompositionLocalOf { ItmoMotion.Default }
