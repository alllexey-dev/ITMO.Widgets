package dev.alllexey.itmowidgets.designsystem.components.state

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import dev.alllexey.itmowidgets.designsystem.components.expressive.ItmoLoadingShapes
import dev.alllexey.itmowidgets.designsystem.components.expressive.ItmoMorphingIndicator
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.tokens.rememberReducedMotion

/**
 * Pull-to-refresh over scrollable [content] with the app's palette (`applyAppRefreshColors()`): indicator `primary`
 * on a `background` container; with [ItmoTheme.expressive] the contained `LoadingIndicator` in Material's colours.
 * [refreshing] is true only for a refresh the user asked for (the pull, a retry); an automatic refresh stays silent
 * and the content simply updates, so a skeleton is never followed by the indicator.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppRefreshBox(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = {
            if (ItmoTheme.expressive) {
                ExpressiveRefreshIndicator(state, refreshing, Modifier.align(Alignment.TopCenter))
            } else {
                PullToRefreshDefaults.Indicator(
                    state = state,
                    isRefreshing = refreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                    containerColor = ItmoTheme.colorScheme.background,
                    color = ItmoTheme.colorScheme.primary,
                )
            }
        },
        content = content,
    )
}

/**
 * `PullToRefreshDefaults.LoadingIndicator` with fixes measured on a device (M3-FIX1, M3-FIX2). The turning shape is
 * the app's [ItmoMorphingIndicator] (no wobble around its centre, no snap between steps), and the pull's shape is
 * drawn at [ItmoLoadingShapes.pullToSpinScale], so the handoff keeps the shape's size instead of shrinking it by a
 * seventh while the two cross-fade. The turning indicator starts on SoftBurst at 0 degrees and the pull's SoftBurst
 * rests at 180, the same outline (it repeats every 36 degrees). Past the threshold the pull turns as upstream's does;
 * with reduced motion the handoff is a cut.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveRefreshIndicator(state: PullToRefreshState, refreshing: Boolean, modifier: Modifier) {
    val containerColor = PullToRefreshDefaults.loadingIndicatorContainerColor
    val color = PullToRefreshDefaults.loadingIndicatorColor
    val fill = Modifier.requiredSize(LoadingIndicatorDefaults.ContainerWidth, LoadingIndicatorDefaults.ContainerHeight)
    val fade = if (rememberReducedMotion()) snap() else MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    PullToRefreshDefaults.IndicatorBox(
        state = state,
        isRefreshing = refreshing,
        modifier = modifier.size(LoadingIndicatorDefaults.ContainerWidth, LoadingIndicatorDefaults.ContainerHeight),
        containerColor = containerColor,
        elevation = PullToRefreshDefaults.LoadingIndicatorElevation,
    ) {
        Crossfade(targetState = refreshing, animationSpec = fade) { turning ->
            if (turning) {
                ItmoMorphingIndicator(color = color, modifier = fill, containerColor = containerColor)
            } else {
                ContainedLoadingIndicator(
                    progress = { state.distanceFraction },
                    modifier = fill
                        .graphicsLayer {
                            scaleX = ItmoLoadingShapes.pullToSpinScale
                            scaleY = ItmoLoadingShapes.pullToSpinScale
                        }
                        .drawWithContent {
                            val over = state.distanceFraction - 1f
                            if (over > 0f) rotate(-over * OVER_PULL_DEGREES) { this@drawWithContent.drawContent() }
                            else drawContent()
                        },
                    containerColor = containerColor,
                    indicatorColor = color,
                    polygons = ItmoLoadingShapes.determinate,
                )
            }
        }
    }
}

/** Upstream's turn of the pull past the threshold: half a turn per threshold distance. */
private const val OVER_PULL_DEGREES = 180f
