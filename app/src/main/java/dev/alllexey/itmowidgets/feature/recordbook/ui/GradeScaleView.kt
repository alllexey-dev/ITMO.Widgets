package dev.alllexey.itmowidgets.feature.recordbook.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import dev.alllexey.itmowidgets.core.util.color
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * A 0–100 bar with the grade thresholds as ticks and their labels under them.
 * Drawn as one view so the ticks sit exactly at their share of the width at any font scale.
 */
class GradeScaleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    data class Tick(val score: Double, val label: String)

    private val density = resources.displayMetrics.density
    private val trackHeight = 8 * density
    private val tickWidth = 2 * density
    private val tickOverhang = 3 * density
    private val labelGap = 4 * density
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.color.resolve(com.google.android.material.R.attr.colorSurfaceContainerHighest)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.color.resolve(com.google.android.material.R.attr.colorOnSurfaceVariant)
    }
    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.color.resolve(com.google.android.material.R.attr.colorOnSurfaceVariant)
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics)
    }
    private val rect = RectF()
    private var score: Double? = null
    private var ticks: List<Tick> = emptyList()

    /** [score] is clamped for drawing only; a missing score leaves the track empty. */
    fun bind(score: Double?, ticks: List<Tick>, fillColor: Int) {
        this.score = score?.takeIf { it.isFinite() }
        fillPaint.color = fillColor
        if (ticks != this.ticks) {
            this.ticks = ticks
            requestLayout()
        }
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val labels = if (ticks.any { it.label.isNotEmpty() }) labelGap + ceil(labelPaint.fontSpacing) else 0f
        val height = (tickOverhang * 2 + trackHeight + labels).roundToInt() + paddingTop + paddingBottom
        setMeasuredDimension(getDefaultSize(suggestedMinimumWidth, widthMeasureSpec), resolveSize(height, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val left = paddingLeft.toFloat()
        val right = (width - paddingRight).toFloat()
        val top = paddingTop + tickOverhang
        val radius = trackHeight / 2
        rect.set(left, top, right, top + trackHeight)
        canvas.drawRoundRect(rect, radius, radius, trackPaint)
        score?.let { value ->
            val share = (value / MAX_SCORE).coerceIn(0.0, 1.0).toFloat()
            if (share > 0f) {
                rect.set(left, top, left + maxOf(trackHeight, (right - left) * share), top + trackHeight)
                canvas.drawRoundRect(rect, radius, radius, fillPaint)
            }
        }
        val baseline = top + trackHeight + tickOverhang + labelGap - labelPaint.fontMetrics.ascent
        ticks.forEach { tick ->
            val x = xOf(tick.score, left, right)
            canvas.drawRect(x - tickWidth / 2, top - tickOverhang, x + tickWidth / 2, top + trackHeight + tickOverhang, tickPaint)
            if (tick.label.isNotEmpty()) {
                val half = labelPaint.measureText(tick.label) / 2
                canvas.drawText(tick.label, x.coerceIn(left + half, right - half), baseline, labelPaint)
            }
        }
    }

    private fun xOf(score: Double, left: Float, right: Float): Float =
        left + (right - left) * (score / MAX_SCORE).coerceIn(0.0, 1.0).toFloat()

    private companion object {
        const val MAX_SCORE = 100.0
    }
}
