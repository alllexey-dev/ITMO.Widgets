package dev.alllexey.itmowidgets.core.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors
import dev.alllexey.itmowidgets.R

/**
 * Placeholder rows for a first load: the shape of the content that is about to
 * appear, pulsing gently, in the same bounded area the content will take.
 *
 * `list` rows are an avatar circle with two text bars; `card` rows are a filled
 * card with a title bar and a shorter line. The pulse stops when the system has
 * animations switched off, and the view never draws when it is hidden.
 */
class SkeletonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val rows: Int
    private val rowHeight: Float
    private val cardStyle: Boolean
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private val gap = 12 * density
    private val radius = 16 * density
    private val barRadius = 6 * density
    private val surface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceVariant)
    private val bar = ColorUtils.setAlphaComponent(
        MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant), BAR_ALPHA
    )
    private var animator: ValueAnimator? = null

    init {
        val attributes = context.obtainStyledAttributes(attrs, R.styleable.SkeletonView)
        try {
            rows = attributes.getInt(R.styleable.SkeletonView_skeletonRows, DEFAULT_ROWS)
            rowHeight = attributes.getDimension(R.styleable.SkeletonView_skeletonRowHeight, DEFAULT_ROW_HEIGHT_DP * density)
            cardStyle = attributes.getInt(R.styleable.SkeletonView_skeletonStyle, STYLE_LIST) == STYLE_CARD
        } finally {
            attributes.recycle()
        }
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        val left = paddingLeft.toFloat()
        val right = (width - paddingRight).toFloat()
        var top = paddingTop.toFloat()
        repeat(rows) {
            val bottom = top + rowHeight
            if (bottom > height - paddingBottom) return
            if (cardStyle) drawCard(canvas, left, top, right, bottom) else drawListRow(canvas, left, top, right, bottom)
            top = bottom + gap
        }
    }

    private fun drawCard(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        paint.color = surface
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint)
        val inset = 16 * density
        val barHeight = 14 * density
        paint.color = bar
        canvas.drawRoundRect(left + inset, top + inset, left + (right - left) * 0.55f, top + inset + barHeight, barRadius, barRadius, paint)
        val secondTop = top + inset + barHeight + 10 * density
        if (secondTop + barHeight * 0.8f < bottom - inset) {
            canvas.drawRoundRect(left + inset, secondTop, left + (right - left) * 0.35f, secondTop + barHeight * 0.8f, barRadius, barRadius, paint)
        }
    }

    private fun drawListRow(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        val avatar = minOf(40 * density, rowHeight - 8 * density)
        val centerY = (top + bottom) / 2
        paint.color = surface
        canvas.drawCircle(left + avatar / 2, centerY, avatar / 2, paint)
        val textLeft = left + avatar + 16 * density
        val barHeight = 12 * density
        paint.color = bar
        canvas.drawRoundRect(textLeft, centerY - barHeight - 3 * density, textLeft + (right - textLeft) * 0.6f, centerY - 3 * density, barRadius, barRadius, paint)
        canvas.drawRoundRect(textLeft, centerY + 3 * density, textLeft + (right - textLeft) * 0.35f, centerY + 3 * density + barHeight * 0.8f, barRadius, barRadius, paint)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAnimation()
    }

    override fun onDetachedFromWindow() {
        stopAnimation()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        updateAnimation()
    }

    private fun updateAnimation() {
        val shouldRun = isAttachedToWindow && isShown && ValueAnimator.areAnimatorsEnabled()
        if (!shouldRun) {
            stopAnimation()
            return
        }
        if (animator != null) return
        animator = ValueAnimator.ofFloat(PULSE_MIN, 1f).apply {
            duration = PULSE_DURATION_MS
            interpolator = LinearInterpolator()
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { alpha = it.animatedValue as Float }
            start()
        }
    }

    private fun stopAnimation() {
        animator?.cancel()
        animator = null
        alpha = 1f
    }

    private companion object {
        const val DEFAULT_ROWS = 4
        const val DEFAULT_ROW_HEIGHT_DP = 72
        const val STYLE_LIST = 0
        const val STYLE_CARD = 1
        const val BAR_ALPHA = 0x38
        const val PULSE_MIN = 0.55f
        const val PULSE_DURATION_MS = 1200L
    }
}
