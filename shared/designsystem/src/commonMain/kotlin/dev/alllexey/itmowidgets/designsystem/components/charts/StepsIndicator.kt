package dev.alllexey.itmowidgets.designsystem.components.charts

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.tokens.IosMetrics
import dev.alllexey.itmowidgets.designsystem.tokens.rememberReducedMotion

/**
 * Progress dots of a flow: [count] dots, the [current] one (0-based) stretched into a `primary` pill. It reports
 * progress only and is not tappable; TalkBack reads it as one node with [contentDescription] (`Шаг 2 из 4`). The
 * pill moves over `ItmoTheme.motion.standardMillis`, at once under reduced motion.
 *
 * Under the iOS style it is a `UIPageControl`'s row of equal dots ([IosMetrics.pageIndicatorDotSize],
 * [IosMetrics.pageIndicatorGap] apart): the current one in the tint, the others in `tertiaryLabel` (UIKit's own
 * defaults, white and white at 45 %, are meant for photos); the colour moves over the same time.
 */
@Composable
fun StepsIndicator(
    count: Int,
    current: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val active = ItmoTheme.colorScheme.primary
    val inactive = ItmoTheme.colorScheme.outlineVariant
    val motion = ItmoTheme.motion
    val reducedMotion = rememberReducedMotion()
    if (ItmoTheme.platformStyle == ItmoPlatformStyle.Ios) {
        IosPageDots(count, current, contentDescription, modifier, reducedMotion)
        return
    }
    Row(
        modifier.clearAndSetSemantics { this.contentDescription = contentDescription },
        horizontalArrangement = Arrangement.spacedBy(DotGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val isCurrent = index == current
            val width by animateDpAsState(
                targetValue = if (isCurrent) ActiveWidth else DotSize,
                animationSpec = if (reducedMotion) snap() else tween(motion.standardMillis, easing = motion.easing),
                label = "stepWidth",
            )
            Box(Modifier.size(width, DotSize).background(if (isCurrent) active else inactive, CircleShape))
        }
    }
}

@Composable
private fun IosPageDots(
    count: Int,
    current: Int,
    contentDescription: String,
    modifier: Modifier,
    reducedMotion: Boolean,
) {
    val active = ItmoTheme.colorScheme.primary
    val inactive = ItmoTheme.iosColors.tertiaryLabel
    val motion = ItmoTheme.motion
    Row(
        modifier.clearAndSetSemantics { this.contentDescription = contentDescription },
        horizontalArrangement = Arrangement.spacedBy(IosMetrics.pageIndicatorGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val color by animateColorAsState(
                targetValue = if (index == current) active else inactive,
                animationSpec = if (reducedMotion) snap() else tween(motion.standardMillis, easing = motion.easing),
                label = "pageDot",
            )
            Box(Modifier.size(IosMetrics.pageIndicatorDotSize).background(color, CircleShape))
        }
    }
}

// `OnboardingStepsView`'s geometry: 8 dp dots 8 dp apart, the current one 24 dp wide.
private val DotSize = 8.dp
private val ActiveWidth = 24.dp
private val DotGap = 8.dp
