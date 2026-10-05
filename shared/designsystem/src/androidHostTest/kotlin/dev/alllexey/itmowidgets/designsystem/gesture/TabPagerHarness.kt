package dev.alllexey.itmowidgets.designsystem.gesture

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.swipe
import kotlin.math.cos
import kotlin.math.sin

/**
 * The bottom tabs as the Android shell's tab pager builds them: a [HorizontalPager] that starts a drag only after
 * [TabSwipeDefaults.slopMultiplier] touch slops and commits at [TabSwipeDefaults.commitFraction], while the pages keep
 * the system slop.
 */
@Composable
internal fun TabPager(state: PagerState, page: @Composable (Int) -> Unit) {
    val system = LocalViewConfiguration.current
    val tabs = remember(system) { SlopScaled(system, TabSwipeDefaults.slopMultiplier) }
    CompositionLocalProvider(LocalViewConfiguration provides tabs) {
        HorizontalPager(
            state = state,
            modifier = Modifier.fillMaxSize(),
            flingBehavior = PagerDefaults.flingBehavior(state, snapPositionalThreshold = TabSwipeDefaults.commitFraction),
        ) { index ->
            CompositionLocalProvider(LocalViewConfiguration provides system) { page(index) }
        }
    }
}

private class SlopScaled(system: ViewConfiguration, multiplier: Float) : ViewConfiguration by system {
    override val touchSlop: Float = system.touchSlop * multiplier
}

/** A steady drag of [distance] px from [start] at [degreesOffVertical] from straight up, released after [millis]. */
internal fun TouchInjectionScope.dragAt(start: Offset, distance: Float, degreesOffVertical: Double, millis: Long) {
    val radians = Math.toRadians(degreesOffVertical)
    swipe(start, start + Offset((-distance * sin(radians)).toFloat(), (-distance * cos(radians)).toFloat()), millis)
}
