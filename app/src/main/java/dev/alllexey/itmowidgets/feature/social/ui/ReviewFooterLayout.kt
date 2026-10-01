package dev.alllexey.itmowidgets.feature.social.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone

/**
 * The bottom row of a review: who wrote it (first child) and its verification (second child) at the start, the votes
 * (third child) at the end. The source comes first and wraps inside the space left of the votes; the verification
 * follows it on the same line when it fits, otherwise it takes a line of its own below. The votes always stay on the
 * first line, centred on it, so they line up from review to review. No extra gaps: the source carries its own 8 dp
 * end padding (a text button) and the vote pill is drawn 16 dp inside its arrows.
 */
class ReviewFooterLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ViewGroup(context, attrs) {
    private var sameLine = true
    private var firstLine = 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val source = getChildAt(SOURCE)
        val verification = getChildAt(VERIFICATION)
        val votes = getChildAt(VOTES)
        val unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        fun atMost(size: Int) = MeasureSpec.makeMeasureSpec(size.coerceAtLeast(0), MeasureSpec.AT_MOST)
        votes.measure(atMost(width), unspecified)
        val votesWidth = if (votes.isGone) 0 else votes.measuredWidth
        val line = width - votesWidth
        source.measure(atMost(line), unspecified)
        val sourceWidth = if (source.isGone) 0 else source.measuredWidth
        verification.measure(unspecified, unspecified)
        val beside = line - sourceWidth
        sameLine = verification.isGone || verification.measuredWidth <= beside
        if (!sameLine) verification.measure(atMost(width), unspecified)
        else if (!verification.isGone) verification.measure(atMost(beside), unspecified)
        val sourceHeight = if (source.isGone) 0 else source.measuredHeight
        val verificationHeight = if (verification.isGone) 0 else verification.measuredHeight
        val votesHeight = if (votes.isGone) 0 else votes.measuredHeight
        firstLine = maxOf(sourceHeight, if (sameLine) verificationHeight else 0, votesHeight, minimumHeight)
        val height = firstLine + if (sameLine) 0 else verificationHeight
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height + paddingTop + paddingBottom)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val source = getChildAt(SOURCE)
        val verification = getChildAt(VERIFICATION)
        val votes = getChildAt(VOTES)
        val width = right - left
        val rtl = layoutDirection == View.LAYOUT_DIRECTION_RTL
        fun place(child: View, x: Int, y: Int, atEnd: Boolean = false) {
            if (child.isGone) return
            val start = if (atEnd) width - paddingRight - x - child.measuredWidth else paddingLeft + x
            val mirrored = if (rtl) width - start - child.measuredWidth else start
            child.layout(mirrored, y, mirrored + child.measuredWidth, y + child.measuredHeight)
        }
        fun centred(child: View) = paddingTop + (firstLine - child.measuredHeight) / 2
        place(source, 0, centred(source))
        if (sameLine) {
            val x = if (source.isGone) 0 else source.measuredWidth
            place(verification, x, centred(verification))
        } else {
            place(verification, 0, paddingTop + firstLine)
        }
        place(votes, 0, centred(votes), atEnd = true)
    }

    override fun generateDefaultLayoutParams(): LayoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

    private companion object {
        const val SOURCE = 0
        const val VERIFICATION = 1
        const val VOTES = 2
    }
}
