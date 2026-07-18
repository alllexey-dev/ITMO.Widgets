package dev.alllexey.itmowidgets.core.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.PathInterpolator
import dev.alllexey.itmowidgets.R
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class CircularProgressBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply { isAntiAlias = true }
    private val backgroundPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
    }
    private val bounds = RectF()
    private val sectorGapAngle: Float

    private var currentSectors = emptyList<Sector>()
    private var renderAsClosedRing = false

    private data class RenderData(
        val color: Int,
        val startAngle: Float,
        val sweepAngle: Float,
        val dotAngle: Float? = null
    )

    private var renderDataList = emptyList<RenderData>()

    private var animator: ValueAnimator? = null

    init {
        val typedArray = context.obtainStyledAttributes(
            attrs, R.styleable.CircularProgressBar, defStyleAttr, 0
        )
        val thickness = typedArray.getDimension(
            R.styleable.CircularProgressBar_progressBarThickness, 20f
        )
        val bgColor = typedArray.getColor(
            R.styleable.CircularProgressBar_bgColor, Color.LTGRAY
        )
        sectorGapAngle = typedArray.getFloat(
            R.styleable.CircularProgressBar_sectorGapAngle, 0f
        ).coerceAtLeast(0f)
        paint.strokeWidth = thickness
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        backgroundPaint.strokeWidth = thickness
        backgroundPaint.color = bgColor
        typedArray.recycle()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val diameter = min(w, h).toFloat()
        val strokeWidth = paint.strokeWidth
        val radius = (diameter - strokeWidth) / 2f
        bounds.set((w / 2f) - radius, (h / 2f) - radius, (w / 2f) + radius, (h / 2f) + radius)
        prepareRenderData()
    }

    data class Sector(val color: Int, val percentage: Float)

    fun setSectors(newSectors: List<Sector>) {
        animator?.cancel()
        renderAsClosedRing = newSectors.isClosedRingTarget()
        currentSectors = newSectors
        prepareRenderData()
        invalidate()
    }

    fun animateSectors(
        newSectors: List<Sector>,
        duration: Long = 1000L,
        startDelay: Long = 0L
    ) {
        animator?.cancel()
        renderAsClosedRing = newSectors.isClosedRingTarget()

        val oldSectors = currentSectors

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            this.startDelay = startDelay
            interpolator = PathInterpolator(.31F, .09F, .2F, .99F)

            addUpdateListener { animation ->
                val fraction = animation.animatedValue as Float
                val intermediateSectors = newSectors.mapIndexed { index, targetSector ->
                    val startPercentage = oldSectors.getOrNull(index)?.percentage ?: 0f
                    val currentPercentage =
                        startPercentage + (targetSector.percentage - startPercentage) * fraction
                    Sector(targetSector.color, currentPercentage)
                }
                currentSectors = intermediateSectors
                prepareRenderData()
                invalidate()
            }
        }
        animator?.start()
    }

    private fun prepareRenderData() {
        val sanitizedSectors = mutableListOf<Pair<Sector, Float>>()
        var remainingPercentage = 100f
        currentSectors.forEach { sector ->
            val percentage = sector.percentage.coerceIn(0f, remainingPercentage)
            remainingPercentage -= percentage
            if (percentage > 0f) sanitizedSectors += sector to percentage
        }

        val isClosedRing = renderAsClosedRing &&
            sanitizedSectors.size > 1
        val capAngle = roundedCapAngle()
        val dotVisibleAngle = capAngle * 2f
        val visibleAngles = sanitizedSectors.mapIndexed { index, (_, percentage) ->
            val rawSweepAngle = 360f * percentage / 100f
            val hasGapBefore = index > 0 || isClosedRing
            val hasGapAfter = index < sanitizedSectors.lastIndex || isClosedRing
            val gapShare = (if (hasGapBefore) sectorGapAngle / 2f else 0f) +
                (if (hasGapAfter) sectorGapAngle / 2f else 0f)
            (rawSweepAngle - gapShare).coerceAtLeast(dotVisibleAngle)
        }.toMutableList()
        if (isClosedRing) {
            fitClosedRing(visibleAngles, dotVisibleAngle)
        }

        var visibleCursor = -90f
        renderDataList = sanitizedSectors.mapIndexed { index, (sector, _) ->
            val visibleAngle = visibleAngles[index]
            val drawableSweep = visibleAngle - dotVisibleAngle

            val data = if (drawableSweep > MIN_ARC_SWEEP_ANGLE) {
                RenderData(
                    color = sector.color,
                    startAngle = visibleCursor + capAngle,
                    sweepAngle = drawableSweep
                )
            } else {
                RenderData(
                    color = sector.color,
                    startAngle = visibleCursor,
                    sweepAngle = 0f,
                    dotAngle = visibleCursor + visibleAngle / 2f
                )
            }
            visibleCursor += visibleAngle
            if (index < sanitizedSectors.lastIndex || isClosedRing) {
                visibleCursor += sectorGapAngle
            }
            data
        }
    }

    private fun fitClosedRing(visibleAngles: MutableList<Float>, minimumVisibleAngle: Float) {
        val availableAngle = 360f - sectorGapAngle * visibleAngles.size
        val excessAngle = (visibleAngles.sum() - availableAngle).coerceAtLeast(0f)
        if (excessAngle <= CLOSED_RING_EPSILON) return

        val reducibleAngles = visibleAngles.map {
            (it - minimumVisibleAngle).coerceAtLeast(0f)
        }
        val totalReducibleAngle = reducibleAngles.sum()
        if (totalReducibleAngle <= 0f) return

        visibleAngles.indices.forEach { index ->
            val reduction = excessAngle * reducibleAngles[index] / totalReducibleAngle
            visibleAngles[index] =
                (visibleAngles[index] - reduction).coerceAtLeast(minimumVisibleAngle)
        }
    }

    private fun roundedCapAngle(): Float {
        val radius = bounds.width() / 2f
        if (radius <= 0f) return 0f

        val capRadius = paint.strokeWidth / 2f
        return Math.toDegrees(
            asin((capRadius / radius).coerceIn(0f, 1f)).toDouble()
        ).toFloat()
    }

    private fun List<Sector>.isClosedRingTarget(): Boolean {
        return sumOf { it.percentage.coerceAtLeast(0f).toDouble() } >=
            100.0 - CLOSED_RING_TARGET_EPSILON
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawArc(bounds, 0f, 360f, false, backgroundPaint)
        for (data in renderDataList) {
            paint.color = data.color
            val dotAngle = data.dotAngle
            if (dotAngle == null) {
                canvas.drawArc(bounds, data.startAngle, data.sweepAngle, false, paint)
            } else {
                val angleRadians = Math.toRadians(dotAngle.toDouble())
                val radius = bounds.width() / 2f
                val x = bounds.centerX() + radius * cos(angleRadians).toFloat()
                val y = bounds.centerY() + radius * sin(angleRadians).toFloat()
                paint.style = Paint.Style.FILL
                canvas.drawCircle(x, y, paint.strokeWidth / 2f, paint)
                paint.style = Paint.Style.STROKE
            }
        }
    }

    private companion object {
        const val CLOSED_RING_EPSILON = 0.001f
        const val CLOSED_RING_TARGET_EPSILON = 0.01
        const val MIN_ARC_SWEEP_ANGLE = 0.5f
    }
}
