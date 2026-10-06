package dev.alllexey.itmowidgets.designsystem.components.state

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.tokens.IosMetrics
import dev.alllexey.itmowidgets.designsystem.tokens.ShapeTokens
import dev.alllexey.itmowidgets.designsystem.tokens.rememberReducedMotion

/** The shape of the content a [Skeleton] stands in for, with `Widget.ItmoWidgets.Skeleton.*`'s row defaults. */
enum class SkeletonStyle(internal val defaultRows: Int, internal val defaultRowHeight: Dp) {
    /** An avatar circle with two text bars per row (`Skeleton.List`). */
    List(defaultRows = 6, defaultRowHeight = 64.dp),

    /** A filled card with a title bar and a shorter line (`Skeleton.Cards`). */
    Cards(defaultRows = 4, defaultRowHeight = 96.dp),
}

/**
 * Placeholder rows for a first load without a cache (`core/ui/SkeletonView`): the shape of the coming content in
 * the area it will take, pulsing between `pulseMinAlpha` and opaque every `pulseMillis`, static under reduced
 * motion. Rows that do not fit the bounds are not drawn; TalkBack skips it.
 *
 * Under the iOS style the shapes and bars are UIKit's `systemFill` (a bar over a card is the fill twice, as a
 * placeholder over a filled cell) and a card has the inset group's corners; the geometry and the pulse stay.
 */
@Composable
fun Skeleton(
    style: SkeletonStyle,
    modifier: Modifier = Modifier,
    rows: Int = style.defaultRows,
    rowHeight: Dp = style.defaultRowHeight,
) {
    val padding = ItmoTheme.spacing.screenMargin
    val ios = ItmoTheme.platformStyle == ItmoPlatformStyle.Ios
    val surface = if (ios) ItmoTheme.iosColors.systemFill else ItmoTheme.colorScheme.surfaceVariant
    val bar = if (ios) surface else ItmoTheme.colorScheme.onSurfaceVariant.copy(alpha = BAR_ALPHA)
    val cardRadius = if (ios) IosMetrics.insetGroupRadius else CardRadius
    val alpha = rememberPulseAlpha()
    Canvas(
        modifier
            .fillMaxWidth()
            .height(padding * 2 + rowHeight * rows + RowGap * (rows - 1).coerceAtLeast(0))
            .graphicsLayer { this.alpha = alpha.value }
            .clearAndSetSemantics {},
    ) {
        val inset = padding.toPx()
        val height = rowHeight.toPx()
        var top = inset
        for (row in 0 until rows) {
            if (top + height > size.height - inset) break
            val area = RowArea(left = inset, top = top, width = size.width - 2 * inset, height = height)
            when (style) {
                SkeletonStyle.List -> drawListRow(area, surface, bar)
                SkeletonStyle.Cards -> drawCard(area, surface, bar, cardRadius.toPx())
            }
            top += height + RowGap.toPx()
        }
    }
}

private class RowArea(val left: Float, val top: Float, val width: Float, val height: Float)

private fun DrawScope.drawCard(area: RowArea, surface: Color, bar: Color, radius: Float) {
    drawRoundRect(surface, Offset(area.left, area.top), Size(area.width, area.height), CornerRadius(radius))
    val inset = CardInset.toPx()
    val barHeight = CardBarHeight.toPx()
    drawBar(bar, area.left + inset, area.top + inset, area.width * TITLE_BAR_FRACTION - inset, barHeight)
    val secondTop = area.top + inset + barHeight + CardLineGap.toPx()
    val secondHeight = barHeight * SECOND_BAR_SCALE
    if (secondTop + secondHeight < area.top + area.height - inset) {
        drawBar(bar, area.left + inset, secondTop, area.width * SECOND_BAR_FRACTION - inset, secondHeight)
    }
}

private fun DrawScope.drawListRow(area: RowArea, surface: Color, bar: Color) {
    val avatar = minOf(AvatarSize.toPx(), area.height - AvatarClearance.toPx())
    val centerY = area.top + area.height / 2
    drawCircle(surface, radius = avatar / 2, center = Offset(area.left + avatar / 2, centerY))
    val textLeft = area.left + avatar + AvatarTextGap.toPx()
    val textWidth = area.left + area.width - textLeft
    val barHeight = ListBarHeight.toPx()
    val halfGap = ListBarHalfGap.toPx()
    drawBar(bar, textLeft, centerY - barHeight - halfGap, textWidth * TITLE_BAR_FRACTION_LIST, barHeight)
    drawBar(bar, textLeft, centerY + halfGap, textWidth * SECOND_BAR_FRACTION, barHeight * SECOND_BAR_SCALE)
}

private fun DrawScope.drawBar(color: Color, left: Float, top: Float, width: Float, height: Float) {
    val radius = BarRadius.toPx()
    drawRoundRect(color, Offset(left, top), Size(width, height), CornerRadius(radius))
}

/** The pulse as a state read in the draw layer, so it never recomposes; opaque under reduced motion. */
@Composable
private fun rememberPulseAlpha(): State<Float> {
    val motion = ItmoTheme.motion
    if (rememberReducedMotion()) return remember { Opaque }
    return rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = motion.pulseMinAlpha,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(motion.pulseMillis, easing = LinearEasing), RepeatMode.Reverse),
        label = "skeletonPulse",
    )
}

private object Opaque : State<Float> {
    override val value: Float = 1f
}

/** `SkeletonView`'s 16 dp card corners, not the content cards' 20 dp. */
private val CardRadius = ShapeTokens.Large

// The rest of `SkeletonView.kt`'s geometry, kept at parity.
private val RowGap = 12.dp
private val BarRadius = 6.dp
private val CardInset = 16.dp
private val CardBarHeight = 14.dp
private val CardLineGap = 10.dp
private val AvatarSize = 40.dp
private val AvatarClearance = 8.dp
private val AvatarTextGap = 16.dp
private val ListBarHeight = 12.dp
private val ListBarHalfGap = 3.dp

/** `BAR_ALPHA = 0x38` of the view. */
private const val BAR_ALPHA = 0x38 / 255f
private const val TITLE_BAR_FRACTION = 0.55f
private const val TITLE_BAR_FRACTION_LIST = 0.6f
private const val SECOND_BAR_FRACTION = 0.35f
private const val SECOND_BAR_SCALE = 0.8f
