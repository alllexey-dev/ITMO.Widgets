package dev.alllexey.itmowidgets.designsystem.components.state

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import dev.alllexey.itmowidgets.designsystem.components.controls.ItmoActivityIndicator
import dev.alllexey.itmowidgets.designsystem.components.expressive.ItmoLoadingShapes
import dev.alllexey.itmowidgets.designsystem.platform.ItmoHapticEvent
import dev.alllexey.itmowidgets.designsystem.platform.ItmoHaptics
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.platform.rememberItmoHaptics
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.tokens.IosMetrics
import dev.alllexey.itmowidgets.designsystem.tokens.rememberReducedMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Pull-to-refresh over scrollable [content] with the app's palette (`applyAppRefreshColors()`): indicator `primary`
 * on a `background` container; with [ItmoTheme.expressive] the contained `LoadingIndicator` in Material's colours.
 * [refreshing] is true only for a refresh the user asked for (the pull, a retry); an automatic refresh stays silent
 * and the content simply updates, so a skeleton is never followed by the indicator.
 *
 * Under the iOS style it behaves as a `UIRefreshControl`: the content follows the pull with UIKit's rubber band and
 * the spinner's spokes appear above it, without a container; at [IosMetrics.refreshTriggerFraction] of the box's
 * height it plays the refresh haptic and asks for the refresh at once, and while [refreshing] the content stays
 * [IosMetrics.refreshControlHeight] down under the turning spinner.
 */
@Composable
fun AppRefreshBox(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    when (ItmoTheme.platformStyle) {
        ItmoPlatformStyle.Material -> MaterialRefreshBox(refreshing, onRefresh, modifier, content)
        ItmoPlatformStyle.Ios -> IosRefreshBox(refreshing, onRefresh, modifier, content)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MaterialRefreshBox(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier,
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
 * `PullToRefreshDefaults.LoadingIndicator` with two fixes, both measured on a device (M3-FIX1). The turning shape
 * morphs through [ItmoLoadingShapes] (no wobble around its centre), and the pull's shape is drawn at
 * [ItmoLoadingShapes.pullToSpinScale], so the handoff keeps the shape's size instead of shrinking it by a seventh while
 * the two cross-fade. The turning indicator starts at 90 degrees and the pull's SoftBurst rests at 180; -90 puts the
 * first SoftBurst onto the last one (it repeats every 36 degrees). Past the threshold the pull turns as upstream's
 * does; with reduced motion the handoff is a cut.
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
                ContainedLoadingIndicator(
                    modifier = fill.graphicsLayer { rotationZ = SPIN_START_DEGREES },
                    containerColor = containerColor,
                    indicatorColor = color,
                    polygons = ItmoLoadingShapes.indeterminate,
                )
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

@Composable
private fun IosRefreshBox(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val restPx = with(LocalDensity.current) { IosMetrics.refreshControlHeight.toPx() }
    val state = remember { IosPullState(initialOffset = if (refreshing) restPx else 0f) }
    val scope = rememberCoroutineScope()
    val haptics = rememberItmoHaptics()
    val reducedMotion = rememberReducedMotion()
    val settleMillis = ItmoTheme.motion.standardMillis
    val easing = ItmoTheme.motion.easing
    val currentRefreshing by rememberUpdatedState(refreshing)
    val currentOnRefresh by rememberUpdatedState(onRefresh)
    val settle by rememberUpdatedState { target: Float ->
        state.settle?.cancel()
        state.settle = scope.launch {
            val spec = if (reducedMotion) snap<Float>() else tween<Float>(settleMillis, easing = easing)
            animate(state.offset, target, animationSpec = spec) { value, _ -> state.offset = value }
        }
    }
    val rest by rememberUpdatedState(restPx)
    val connection = remember(state, haptics) {
        IosPullConnection(
            state = state,
            haptics = haptics,
            refreshing = { currentRefreshing },
            onRefresh = { currentOnRefresh() },
            onRelease = { settle(if (currentRefreshing && state.offset > 0f) rest else 0f) },
        )
    }
    LaunchedEffect(refreshing) {
        if (!state.dragging) settle(if (refreshing) rest else 0f)
    }
    Box(
        modifier
            .onSizeChanged { state.heightPx = it.height.toFloat() }
            .nestedScroll(connection)
            .clipToBounds(),
    ) {
        IosRefreshSpinner(state, refreshing, Modifier.align(Alignment.TopCenter))
        Box(Modifier.offset { IntOffset(0, state.offset.roundToInt()) }, propagateMinConstraints = true) { content() }
    }
}

/** Where the content of an iOS refresh box is: [offset] px down, the gesture's flags and the settle animation. */
@Stable
private class IosPullState(initialOffset: Float) {
    var offset by mutableFloatStateOf(initialOffset)
    var heightPx = 0f
    var dragging = false
    var triggered = false
    var settle: Job? = null
}

/**
 * The pull of an iOS refresh box: a user drag past the top of [content][IosRefreshBox] moves it down by UIScrollView's
 * rubber band, a drag back up takes the pull back before the content scrolls, and the release settles it.
 */
private class IosPullConnection(
    private val state: IosPullState,
    private val haptics: ItmoHaptics,
    private val refreshing: () -> Boolean,
    private val onRefresh: () -> Unit,
    private val onRelease: () -> Unit,
) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.y >= 0f || state.offset <= 0f) return Offset.Zero
        val pulled = unband(state.offset)
        val consumed = maxOf(available.y, -pulled)
        drag(pulled + consumed)
        return Offset(0f, consumed)
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
        drag(unband(state.offset) + available.y)
        return Offset(0f, available.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        val visible = state.offset > 0f
        if (state.dragging) {
            state.dragging = false
            state.triggered = false
            onRelease()
        }
        return if (visible) available else Velocity.Zero
    }

    private fun drag(pulled: Float) {
        state.settle?.cancel()
        state.dragging = true
        state.offset = band(pulled)
        val threshold = state.heightPx * IosMetrics.refreshTriggerFraction
        if (!state.triggered && !refreshing() && threshold > 0f && state.offset >= threshold) {
            state.triggered = true
            haptics.perform(ItmoHapticEvent.RefreshTrigger)
            onRefresh()
        }
    }

    /** UIScrollView's rubber band: a finger [pulled] px past the edge moves the content this far. */
    private fun band(pulled: Float): Float {
        val dimension = state.heightPx
        if (dimension <= 0f) return pulled
        return (1f - 1f / (pulled * RUBBER_BAND / dimension + 1f)) * dimension
    }

    /** The finger distance that [band] turns into [offset]. */
    private fun unband(offset: Float): Float {
        val dimension = state.heightPx
        if (dimension <= 0f) return offset
        val fraction = (offset / dimension).coerceAtMost(MAX_BAND_FRACTION)
        return (1f / (1f - fraction) - 1f) * dimension / RUBBER_BAND
    }
}

/**
 * The refresh control's spinner in the band above the content, clipped to what the pull reveals: while the user
 * pulls, one spoke per eighth of the way to the trigger; while [refreshing], the turning [ItmoActivityIndicator].
 */
@Composable
private fun IosRefreshSpinner(state: IosPullState, refreshing: Boolean, modifier: Modifier) {
    val revealed by remember(state) { derivedStateOf { state.offset > 0f } }
    if (!revealed && !refreshing) return
    val color = ItmoTheme.iosColors.secondaryLabel
    Box(
        modifier
            .fillMaxWidth()
            .height(IosMetrics.refreshControlHeight)
            .drawWithContent { clipRect(bottom = state.offset) { this@drawWithContent.drawContent() } },
        contentAlignment = Alignment.Center,
    ) {
        if (refreshing) {
            // A size before the indicator's own wins, so it draws at the refresh control's 30 pt.
            ItmoActivityIndicator(Modifier.size(IosMetrics.refreshSpinnerSize), color = color)
        } else {
            Canvas(Modifier.size(IosMetrics.refreshSpinnerSize)) {
                val threshold = state.heightPx * IosMetrics.refreshTriggerFraction
                val shown = if (threshold > 0f) floor(state.offset / threshold * SPOKES).toInt() else 0
                drawSpokes(shown.coerceIn(0, SPOKES), color)
            }
        }
    }
}

private fun DrawScope.drawSpokes(count: Int, color: Color) {
    val width = size.minDimension
    val stroke = width * IosMetrics.activityIndicatorSpokeWidth
    val inner = width * IosMetrics.activityIndicatorSpokeInner + stroke / 2
    val outer = width * IosMetrics.activityIndicatorSpokeOuter - stroke / 2
    repeat(count) { spoke ->
        val angle = 2 * PI * spoke / SPOKES - PI / 2
        val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
        drawLine(color, center + direction * inner, center + direction * outer, stroke, StrokeCap.Round)
    }
}

private const val SPOKES = 8

/** Lines the turning indicator's first SoftBurst up with the pull's last one (see [ExpressiveRefreshIndicator]). */
private const val SPIN_START_DEGREES = -90f

/** Upstream's turn of the pull past the threshold: half a turn per threshold distance. */
private const val OVER_PULL_DEGREES = 180f

/** UIScrollView's rubber-band constant: the content moves `(1 - 1 / (x * c / d + 1)) * d` for a pull `x`. */
private const val RUBBER_BAND = 0.55f

/** The band never reaches the box's height; the inverse stays finite. */
private const val MAX_BAND_FRACTION = 0.99f
