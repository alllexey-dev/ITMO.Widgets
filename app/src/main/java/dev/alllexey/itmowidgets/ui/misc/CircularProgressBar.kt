package dev.alllexey.itmowidgets.ui.misc

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
        paint.strokeWidth = thickness
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
        renderDataList = currentSectors.map { sector ->
            val sweepAngle = 360 * sector.percentage / 100
            val data = RenderData(sector.color, currentAngle, sweepAngle)
            currentAngle += sweepAngle
            data
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawArc(bounds, 0f, 360f, false, backgroundPaint)
        if (renderDataList.isEmpty()) return
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.BUTT
        for (data in renderDataList) {
            paint.color = data.color
            canvas.drawArc(bounds, data.startAngle, data.sweepAngle, false, paint)
        }
        paint.style = Paint.Style.FILL
        val radius = bounds.width() / 2f
        val capRadius = paint.strokeWidth / 2f
        for (data in renderDataList) {
            paint.color = data.color
            val startAngleRad = Math.toRadians(data.startAngle.toDouble())
            val startX = bounds.centerX() + radius * cos(startAngleRad).toFloat()
            val startY = bounds.centerY() + radius * sin(startAngleRad).toFloat()
            canvas.drawCircle(startX, startY, capRadius, paint)
        }
        for (data in renderDataList) {
            paint.color = data.color
            val endAngleRad = Math.toRadians((data.startAngle + data.sweepAngle).toDouble())
            val endX = bounds.centerX() + radius * cos(endAngleRad).toFloat()
            val endY = bounds.centerY() + radius * sin(endAngleRad).toFloat()
            canvas.drawCircle(endX, endY, capRadius, paint)
        }
    }
}