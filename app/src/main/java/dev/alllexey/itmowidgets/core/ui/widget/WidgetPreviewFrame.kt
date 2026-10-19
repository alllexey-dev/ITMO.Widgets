package dev.alllexey.itmowidgets.core.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.util.SizeF
import android.widget.FrameLayout
import kotlin.math.roundToInt

/**
 * A widget preview at the launcher's [size] (dp): its child is laid out at that size, then shrunk (never grown) as a
 * whole to the width the frame gets and centred, so a card shows the entire widget in proportion.
 */
class WidgetPreviewFrame @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {

    var size: SizeF = SizeF(0f, 0f)
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    private val naturalWidth get() = (size.width * resources.displayMetrics.density).roundToInt()
    private val naturalHeight get() = (size.height * resources.displayMetrics.density).roundToInt()
    private var scale = 1f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = resolveSize(naturalWidth, widthMeasureSpec)
        scale = if (naturalWidth > 0) minOf(1f, width.toFloat() / naturalWidth) else 1f
        val exactWidth = MeasureSpec.makeMeasureSpec(naturalWidth, MeasureSpec.EXACTLY)
        val exactHeight = MeasureSpec.makeMeasureSpec(naturalHeight, MeasureSpec.EXACTLY)
        for (index in 0 until childCount) getChildAt(index).measure(exactWidth, exactHeight)
        setMeasuredDimension(width, (naturalHeight * scale).roundToInt())
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        for (index in 0 until childCount) {
            getChildAt(index).apply {
                layout(0, 0, naturalWidth, naturalHeight)
                pivotX = 0f
                pivotY = 0f
                scaleX = scale
                scaleY = scale
                translationX = (right - left - naturalWidth * scale) / 2
            }
        }
    }
}
