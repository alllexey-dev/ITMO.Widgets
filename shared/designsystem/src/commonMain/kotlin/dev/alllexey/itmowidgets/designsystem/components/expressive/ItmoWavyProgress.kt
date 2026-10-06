package dev.alllexey.itmowidgets.designsystem.components.expressive

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.tokens.IosMetrics

/** The two forms of [ItmoWavyProgress]. */
enum class ItmoWavyProgressShape { Linear, Circular }

/**
 * Determinate progress of a hero moment only (the QR pass reveal, the profile hero, the sport score ring, the lesson
 * in progress); dense rows keep a flat linear bar. [progress] is read in the draw phase, 0..1. With
 * [ItmoTheme.expressive] the track waves; otherwise it is today's flat indicator.
 *
 * Under the iOS style it is a plain `UIProgressView` whatever the switch says: a capsule track in `systemFill` with
 * the progress in the tint over it, [IosMetrics.progressBarHeight] thick, no wave, no gap and no stop indicator; the
 * circular form is a ring of the same track and stroke.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ItmoWavyProgress(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    shape: ItmoWavyProgressShape = ItmoWavyProgressShape.Linear,
) {
    if (ItmoTheme.platformStyle == ItmoPlatformStyle.Ios) {
        IosProgress(progress, modifier, shape)
        return
    }
    val expressive = ItmoTheme.expressive
    when (shape) {
        ItmoWavyProgressShape.Linear -> if (expressive) {
            LinearWavyProgressIndicator(progress, modifier)
        } else {
            LinearProgressIndicator(progress, modifier)
        }

        ItmoWavyProgressShape.Circular -> if (expressive) {
            CircularWavyProgressIndicator(progress, modifier)
        } else {
            CircularProgressIndicator(progress, modifier)
        }
    }
}

@Composable
private fun IosProgress(progress: () -> Float, modifier: Modifier, shape: ItmoWavyProgressShape) {
    val track = ItmoTheme.iosColors.systemFill
    val tint = ItmoTheme.colorScheme.primary
    val reported = modifier.semantics {
        progressBarRangeInfo = ProgressBarRangeInfo(progress().coerceIn(0f, 1f), 0f..1f)
    }
    when (shape) {
        ItmoWavyProgressShape.Linear -> Canvas(reported.size(IosLinearWidth, IosMetrics.progressBarHeight)) {
            drawIosBar(progress().coerceIn(0f, 1f), track, tint)
        }

        ItmoWavyProgressShape.Circular -> Canvas(reported.size(IosCircularDiameter)) {
            drawIosRing(progress().coerceIn(0f, 1f), track, tint)
        }
    }
}

private fun DrawScope.drawIosBar(value: Float, track: Color, tint: Color) {
    val radius = CornerRadius(size.height / 2)
    drawRoundRect(track, cornerRadius = radius)
    if (value > 0f) {
        // UIKit stretches a capsule image, so even a sliver of progress keeps its round ends.
        val width = maxOf(size.width * value, size.height)
        drawRoundRect(tint, size = Size(width, size.height), cornerRadius = radius)
    }
}

private fun DrawScope.drawIosRing(value: Float, track: Color, tint: Color) {
    val stroke = IosMetrics.progressBarHeight.toPx()
    val topLeft = Offset(stroke / 2, stroke / 2)
    val arcSize = Size(size.minDimension - stroke, size.minDimension - stroke)
    drawArc(track, 0f, FULL_TURN, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
    if (value > 0f) {
        drawArc(
            tint,
            startAngle = TOP,
            sweepAngle = FULL_TURN * value,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}

/** Material's default linear width, so a caller without a width gets a bar of the same length in both styles. */
private val IosLinearWidth = 240.dp

/** Material's default circular diameter, for the same reason. */
private val IosCircularDiameter = 40.dp

private const val FULL_TURN = 360f
private const val TOP = -90f
