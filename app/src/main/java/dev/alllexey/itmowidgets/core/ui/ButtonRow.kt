package dev.alllexey.itmowidgets.core.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.children

/**
 * Buttons side by side, as the layout declares them, or stacked at full width when their labels do not fit in one
 * row (a large font scale on a narrow screen), so no label breaks inside a word. Stacked, every button after the
 * first keeps its start margin as the gap above it. Like AppCompat's `ButtonBarLayout`, the row un-stacks only when
 * it gets wider or the set of visible buttons changes, so measuring never flips it back and forth.
 */
class ButtonRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val rowParams = mutableMapOf<View, RowParams>()
    private var stacked = false
    private var lastWidth = -1
    private var lastVisible = emptyList<View>()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val visible = children.filter { it.visibility != GONE }.toList()
        if (stacked && (width > lastWidth || visible != lastVisible)) setStacked(false)
        lastWidth = width
        lastVisible = visible

        val exact = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY
        val probe = if (exact && !stacked) MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST) else widthMeasureSpec
        super.onMeasure(probe, heightMeasureSpec)
        val tooSmall = !stacked && measuredWidthAndState and MEASURED_STATE_TOO_SMALL != 0
        if (tooSmall) setStacked(true)
        if (tooSmall || probe != widthMeasureSpec) super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun setStacked(stack: Boolean) {
        stacked = stack
        orientation = if (stack) VERTICAL else HORIZONTAL
        children.forEachIndexed { index, child ->
            val params = child.layoutParams as LayoutParams
            val row = rowParams.getOrPut(child) { RowParams(params) }
            if (stack) {
                params.width = LayoutParams.MATCH_PARENT
                params.weight = 0f
                params.marginStart = 0
                params.topMargin = if (index == 0) row.topMargin else row.marginStart
            } else {
                row.restore(params)
            }
        }
    }

    private class RowParams(params: LayoutParams) {
        private val width = params.width
        private val weight = params.weight
        val marginStart = params.marginStart
        val topMargin = params.topMargin

        fun restore(params: LayoutParams) {
            params.width = width
            params.weight = weight
            params.marginStart = marginStart
            params.topMargin = topMargin
        }
    }
}
