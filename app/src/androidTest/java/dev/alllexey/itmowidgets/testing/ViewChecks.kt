package dev.alllexey.itmowidgets.testing

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/** Layout assertions shared by the visual tests. */
object ViewChecks {
    /** Which text views [assertTextFits] inspects. */
    enum class Visible {
        /** `isShown`: attached and every ancestor visible. */
        SHOWN,
        /** `visibility == View.VISIBLE` on the view itself, regardless of its ancestors. */
        FLAG
    }

    /** The view itself followed by every descendant, depth first. */
    fun View.descendants(): Sequence<View> = sequence {
        yield(this@descendants)
        if (this@descendants is ViewGroup) (0 until childCount).forEach { yieldAll(getChildAt(it).descendants()) }
    }

    /**
     * Every non-empty text view under [root] is fully laid out within its own bounds.
     * Ellipsis fails unless [allowEllipsis]; [checkEdges] also keeps the view inside its parent.
     */
    fun assertTextFits(
        root: View,
        allowEllipsis: Boolean = false,
        visible: Visible = Visible.SHOWN,
        checkEdges: Boolean = false
    ) {
        root.descendants().filterIsInstance<TextView>()
            .filter { if (visible == Visible.SHOWN) it.isShown else it.visibility == View.VISIBLE }
            .filter { it.text.isNotEmpty() }
            .forEach { view ->
                val layout = view.layout ?: return@forEach
                assertTrue("Height: ${view.text}", layout.height <= view.height - view.compoundPaddingTop - view.compoundPaddingBottom)
                for (line in 0 until layout.lineCount) {
                    if (!allowEllipsis) assertEquals("Ellipsis: ${view.text}", 0, layout.getEllipsisCount(line))
                    assertTrue(
                        "Width (${layout.getLineMax(line)} / ${view.width - view.compoundPaddingLeft - view.compoundPaddingRight}): ${view.text}",
                        layout.getLineMax(line) <= view.width - view.compoundPaddingLeft - view.compoundPaddingRight + 1
                    )
                }
                if (checkEdges) {
                    val parent = view.parent as? ViewGroup ?: return@forEach
                    assertTrue("Left edge: ${view.text}", view.left >= 0)
                    assertTrue("Right edge: ${view.text}", view.right <= parent.width)
                }
            }
    }

    /** Every shown clickable view under [root] is at least 48 dp tall (and wide unless [requireWidth] is off). */
    fun assertTouchTargets(root: View, requireWidth: Boolean = true) {
        val min = 48 * root.resources.displayMetrics.density - 1
        root.descendants().filter { it.isShown && it.isClickable }.forEach {
            assertTrue("Touch target: ${it.javaClass.simpleName}", it.height >= min && (!requireWidth || it.width >= min))
        }
    }
}
