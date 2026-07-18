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
import kotlin.math.max
import kotlin.math.min

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

    private data class RenderData(val color: Int, val startAngle: Float, val sweepAngle: Float)

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
        var currentAngle = -90f
        var remainingPercentage = 100f
        renderDataList = currentSectors.mapNotNull { sector ->
            val percentage = sector.percentage.coerceIn(0f, remainingPercentage)
            remainingPercentage -= percentage

            val rawSweepAngle = 360f * percentage / 100f
            if (rawSweepAngle <= 0f) return@mapNotNull null

            val gap = min(
                sectorGapAngle + roundedCapCompensationAngle(),
                rawSweepAngle * MAX_GAP_FRACTION
            )
            val sweepAngle = max(0f, rawSweepAngle - gap)
            val data = RenderData(
                color = sector.color,
                startAngle = currentAngle + gap / 2f,
                sweepAngle = sweepAngle
            )
            currentAngle += rawSweepAngle
            data
        }
    }

    private fun roundedCapCompensationAngle(): Float {
        val radius = bounds.width() / 2f
        if (radius <= 0f) return 0f

        val capRadius = paint.strokeWidth / 2f
        val capAngle = Math.toDegrees(asin((capRadius / radius).coerceIn(0f, 1f)).toDouble())
        return (capAngle * 2).toFloat()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawArc(bounds, 0f, 360f, false, backgroundPaint)
        for (data in renderDataList) {
            paint.color = data.color
            canvas.drawArc(bounds, data.startAngle, data.sweepAngle, false, paint)
        }
    }

    private companion object {
        const val MAX_GAP_FRACTION = 0.35f
    }
}
