package dev.alllexey.itmowidgets.feature.sport.ui.my

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.PathInterpolator
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import dev.alllexey.itmowidgets.core.util.color
import kotlin.math.roundToInt

/**
 * Collapses the sport score card into a compact bar while the bookings list scrolls under it.
 *
 * The list owns its geometry: it fills the screen and reserves the expanded card height as top
 * padding, so a drag always moves list content one to one. Letting the card push the list instead
 * would move that content twice per drag — once for the scroll, once for the shrinking header.
 *
 * All views retain their expanded measurement. Only drawing bounds and translations change
 * during scrolling; changing LayoutParams or padding here would remeasure the ConstraintLayout
 * and its RecyclerView every frame. The details slide up inside a shrinking clip window, while
 * the title and status remain in the first row. The card's drawable follows its drawing bounds,
 * preserving rounded corners without scaling the text or the background.
 */
class SportScoreCollapseController(
    private val card: MaterialCardView,
    private val content: ViewGroup,
    private val details: View,
    private val detailsContent: View,
    private val scrim: View,
    private val recycler: RecyclerView
) {

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(view: RecyclerView, dx: Int, dy: Int) = applyScrollOffset()

        override fun onScrollStateChanged(view: RecyclerView, state: Int) {
            when {
                // A new drag supersedes a snap in flight, so the release can snap again.
                state == RecyclerView.SCROLL_STATE_DRAGGING -> snapping = false
                state != RecyclerView.SCROLL_STATE_IDLE -> Unit
                snapping -> snapping = false
                else -> settleToNearestEdge()
            }
        }
    }

    private val preDrawListener = ViewTreeObserver.OnPreDrawListener { prepareFrame() }

    private var fraction = 0f
    private var snapping = false
    private var geometryReady = false
    private val restingColor =
        card.context.color.resolve(com.google.android.material.R.attr.colorSurfaceContainerLow)
    private val raisedColor =
        card.context.color.resolve(com.google.android.material.R.attr.colorSurfaceContainerHigh)

    private val expandedPadding get() = EXPANDED_PADDING_DP.dp()
    private val collapsedPadding get() = COLLAPSED_PADDING_DP.dp()

    fun attach() {
        recycler.addOnScrollListener(scrollListener)
        card.viewTreeObserver.addOnPreDrawListener(preDrawListener)
    }

    fun detach() {
        recycler.removeOnScrollListener(scrollListener)
        card.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
    }

    /** Re-reads the scroll position after the list content changed under the card. */
    fun refresh() = applyScrollOffset()

    private fun prepareFrame(): Boolean {
        scrim.isVisible = card.isVisible
        if (!card.isShown || card.width == 0) return true
        val natural = details.measuredHeight
        if (natural == 0) return true

        // Reserve the natural card size, including a potentially wrapped status/header, even
        // when restoring a collapsed list. Do this outside the parent's layout pass: changing
        // padding from the card's onLayout can leave already-laid-out rows at their old position.
        val expandedBottom = card.top + card.measuredHeight
        val paddingTop = expandedBottom + LIST_GAP_DP.dp()
        if (recycler.paddingTop != paddingTop) {
            geometryReady = false
            val manager = recycler.layoutManager as? LinearLayoutManager
            val anchor = manager?.getChildAt(0)
            val position = anchor?.let { manager.getPosition(it) }
            val offset = anchor?.let { manager.getDecoratedTop(it) - recycler.paddingTop }
            recycler.updatePadding(top = paddingTop)
            if (position != null && position != RecyclerView.NO_POSITION && offset != null) {
                manager.scrollToPositionWithOffset(position, offset)
            }
            // The new padding and the rows must agree before the first visible content frame.
            return false
        }

        geometryReady = true
        applyScrollOffset()
        val scrimBounds = Rect(0, 0, scrim.width, card.bottom + LIST_GAP_DP.dp())
        if (scrim.clipBounds != scrimBounds) {
            // Clipping is draw-only: resizing this backdrop would request another layout on
            // every animation frame and could postpone drawing until the fling ends.
            scrim.clipBounds = scrimBounds
        }
        return true
    }

    /**
     * Finishes a half-collapsed header once the list stops moving.
     *
     * The snap scrolls the list rather than animating the fraction on its own: the fraction is
     * derived from the scroll offset, so moving it alone would leave the two disagreeing and the
     * card would jump back on the next scroll event. A list too short to reach the target simply
     * stays where it is — the idle pass that follows sees [snapping] and does not retry.
     */
    fun settleToNearestEdge() {
        if (!geometryReady) return
        val range = collapseRange()
        if (range <= 0 || fraction <= 0f || fraction >= 1f) return
        val target = if (fraction < SNAP_THRESHOLD) 0 else range
        val delta = target - recycler.computeVerticalScrollOffset()
        if (delta == 0) return
        snapping = true
        recycler.smoothScrollBy(0, delta, SNAP_INTERPOLATOR)
    }

    private fun applyScrollOffset() {
        if (!geometryReady) return
        if (!recycler.isVisible) {
            apply(0f)
            return
        }
        if (recycler.isLayoutRequested) return
        val range = collapseRange()
        // computeVerticalScrollOffset is exact while the first item is visible, which covers the
        // whole collapse range; past it the estimate only has to stay large enough to clamp at 1.
        val offset = recycler.computeVerticalScrollOffset().toFloat()
        apply(if (range <= 0) 0f else (offset / range).coerceIn(0f, 1f))
    }

    private fun collapseRange(): Int =
        details.measuredHeight + (expandedPadding - collapsedPadding) * 2

    private fun apply(target: Float) {
        val natural = details.measuredHeight
        if (natural == 0) return
        fraction = target
        // setBottom changes drawing bounds, not LayoutParams or measuredHeight. Reapply after
        // ordinary layout passes too, since they restore the natural expanded bounds.
        card.bottom = card.top + card.measuredHeight - (collapseRange() * target).roundToInt()
        details.bottom = details.top + (natural * (1f - target)).roundToInt()
        content.translationY = (collapsedPadding - expandedPadding) * target
        detailsContent.translationY = -natural * target
        detailsContent.alpha = (1f - target / FADE_COMPLETED_AT).coerceIn(0f, 1f)
        scrim.alpha = target
        // List items share the card's surface, so a collapsed card needs its own step to stay
        // readable once rows slide underneath it.
        val color = ColorUtils.blendARGB(restingColor, raisedColor, target)
        if (card.cardBackgroundColor.defaultColor != color) card.setCardBackgroundColor(color)
    }

    private fun Int.dp(): Int = (this * card.resources.displayMetrics.density).toInt()

    private companion object {
        const val EXPANDED_PADDING_DP = 20
        const val COLLAPSED_PADDING_DP = 12
        const val LIST_GAP_DP = 8
        /** Detail text is gone before the block finishes closing, so nothing fades out mid-clip. */
        const val FADE_COMPLETED_AT = 0.7f
        const val SNAP_THRESHOLD = 0.5f
        val SNAP_INTERPOLATOR = PathInterpolator(0.2f, 0f, 0f, 1f)
    }
}
