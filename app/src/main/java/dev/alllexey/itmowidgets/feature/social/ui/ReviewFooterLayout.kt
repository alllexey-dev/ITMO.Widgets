package dev.alllexey.itmowidgets.feature.social.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import kotlin.math.max

/**
 * The bottom row of a review: the origin (first child) at the start and the actions (second child) at the end.
 * They share one line, centred, when the origin fits beside the actions in one line of its own; otherwise the
 * origin takes the full width above and the actions follow at the end, so long names never break inside words.
 */
class ReviewFooterLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ViewGroup(context, attrs) {
    private val gap = (GAP_DP * resources.displayMetrics.density).toInt()
    private var stacked = false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val origin = getChildAt(0)
        val actions = getChildAt(1)
        val unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        actions.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), unspecified)
        val actionsWidth = if (actions.visibility == View.GONE) 0 else actions.measuredWidth + gap
        val beside = width - actionsWidth
        origin.measure(unspecified, unspecified)
        stacked = origin.visibility != View.GONE && actions.visibility != View.GONE && origin.measuredWidth > beside
        origin.measure(MeasureSpec.makeMeasureSpec(if (stacked) width else beside, MeasureSpec.AT_MOST), unspecified)
        val originHeight = if (origin.visibility == View.GONE) 0 else origin.measuredHeight
        val actionsHeight = if (actions.visibility == View.GONE) 0 else actions.measuredHeight
        val content = if (stacked) gap / 2 + originHeight + actionsHeight else max(max(originHeight, actionsHeight), minimumHeight)
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), content + paddingTop + paddingBottom)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val origin = getChildAt(0)
        val actions = getChildAt(1)
        val width = right - left
        val rtl = layoutDirection == View.LAYOUT_DIRECTION_RTL
        val rowHeight = bottom - top - paddingTop - paddingBottom
        fun place(child: View, atEnd: Boolean, y: Int) {
            val x = if (atEnd != rtl) width - paddingRight - child.measuredWidth else paddingLeft
            child.layout(x, y, x + child.measuredWidth, y + child.measuredHeight)
        }
        if (stacked) {
            // A little air between the text above and an origin that no longer sits in a 48 dp row.
            place(origin, atEnd = false, y = paddingTop + gap / 2)
            place(actions, atEnd = true, y = paddingTop + gap / 2 + origin.measuredHeight)
        } else {
            place(origin, atEnd = false, y = paddingTop + (rowHeight - origin.measuredHeight) / 2)
            place(actions, atEnd = true, y = paddingTop + (rowHeight - actions.measuredHeight) / 2)
        }
    }

    override fun generateDefaultLayoutParams(): LayoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

    private companion object {
        const val GAP_DP = 8
    }
}
