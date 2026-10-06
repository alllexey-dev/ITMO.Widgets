package dev.alllexey.itmowidgets.designsystem.components.controls

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateValue
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.tokens.IosMetrics
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The two sizes of `UIActivityIndicatorView`: `.medium` inside buttons and rows, `.large` for a section. */
enum class ItmoActivityIndicatorSize(internal val size: Dp) {
    Medium(IosMetrics.activityIndicatorMedium),
    Large(IosMetrics.activityIndicatorLarge),
}

/**
 * An indeterminate spinner of a fixed [size]. Under the iOS style it is `UIActivityIndicatorView`: eight spokes
 * whose shades turn clockwise, in the secondary label colour unless [color] is given, still turning with Reduce
 * Motion as UIKit's does. Under Material it is a circular indicator of the same size in `primary` (or [color]).
 * TalkBack and VoiceOver read it as an indeterminate progress.
 */
@Composable
fun ItmoActivityIndicator(
    modifier: Modifier = Modifier,
    size: ItmoActivityIndicatorSize = ItmoActivityIndicatorSize.Medium,
    color: Color = Color.Unspecified,
) {
    when (ItmoTheme.platformStyle) {
        ItmoPlatformStyle.Material -> CircularProgressIndicator(
            modifier.size(size.size),
            color = color.takeOrElse { ItmoTheme.colorScheme.primary },
            strokeWidth = size.size * MATERIAL_STROKE_FRACTION,
        )
        ItmoPlatformStyle.Ios -> IosSpinner(modifier, size.size, color.takeOrElse { ItmoTheme.iosColors.secondaryLabel })
    }
}

@Composable
private fun IosSpinner(modifier: Modifier, size: Dp, color: Color) {
    val turning = rememberInfiniteTransition(label = "spinner")
    val step by turning.animateValue(
        initialValue = 0,
        targetValue = SPOKES,
        typeConverter = Int.VectorConverter,
        animationSpec = infiniteRepeatable(tween(PERIOD_MILLIS, easing = LinearEasing)),
        label = "step",
    )
    Canvas(
        modifier
            .size(size)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate },
    ) {
        val width = this.size.minDimension
        val stroke = width * IosMetrics.activityIndicatorSpokeWidth
        val inner = width * IosMetrics.activityIndicatorSpokeInner + stroke / 2
        val outer = width * IosMetrics.activityIndicatorSpokeOuter - stroke / 2
        val lead = step % SPOKES
        repeat(SPOKES) { spoke ->
            val angle = 2 * PI * spoke / SPOKES - PI / 2
            val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
            val behind = (lead - spoke + SPOKES) % SPOKES
            drawLine(
                color = color.copy(alpha = color.alpha * IosMetrics.activityIndicatorSpokeAlphas[behind]),
                start = center + direction * inner,
                end = center + direction * outer,
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

private const val SPOKES = 8

/** One turn of the shades: UIKit's spinner steps through its eight spokes in about a second. */
private const val PERIOD_MILLIS = 1000

/** The Material indicator's track at the width of `ProgressButton`'s, 2 dp in an 18 dp slot. */
private const val MATERIAL_STROKE_FRACTION = 0.1f
