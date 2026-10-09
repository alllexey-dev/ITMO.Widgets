package dev.alllexey.itmowidgets.feature.sport.ui.my

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.tokens.rememberReducedMotion
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * How far the `Мой спорт` score card has collapsed into its compact bar, derived from the bookings list's scroll
 * position, never kept on its own: the list fills the screen and reserves the expanded card as top padding
 * ([SportScoreCollapsingLayout]), so a drag moves the rows one to one and the card follows the same offset. Over the
 * [range] (the details' height plus the padding the bar gives up) the [fraction] goes from 0 to 1; the card applies it
 * through `graphicsLayer` and clip only, so scrolling never measures anything again.
 *
 * When a drag or a fling ends half-way, [nestedScrollConnection] settles the card to the nearer edge by scrolling the
 * list, so the list and the card cannot disagree. A list too short to reach that edge stays where it is, and a new
 * drag takes over from a settle in flight.
 */
@Stable
class SportScoreCollapseState internal constructor(
    private val scrollable: ScrollableState?,
    private val scrollOffset: () -> Int,
) {
    /** The natural height of the card's details block, reported by the card's layout. */
    internal var detailsHeight by mutableIntStateOf(0)

    /** What the bar gives up of the card's padding, top and bottom together; reported with [detailsHeight]. */
    internal var paddingSpan by mutableIntStateOf(0)

    /** The settle animation; null under reduced motion, where the list jumps to the edge. */
    internal var settleSpec: AnimationSpec<Float>? = null

    /** The scroll distance in pixels over which the card collapses; 0 until the card has been measured. */
    val range: Int get() = if (detailsHeight == 0) 0 else detailsHeight + paddingSpan

    /** 0 expanded, 1 the compact bar. Read it in a draw or layer block only. */
    val fraction: Float
        get() {
            val range = range
            if (range <= 0) return 0f
            return (scrollOffset().toFloat() / range).coerceIn(0f, 1f)
        }

    /** The card's visible height for a measured [cardHeight]: the measurement itself never changes. */
    internal fun visibleCardHeight(cardHeight: Float): Float = cardHeight - range * fraction

    /** The visible part of the details block, which retreats under the header. */
    internal fun visibleDetailsHeight(): Float = detailsHeight * (1f - fraction)

    /** The details are gone before the block finishes closing, so nothing fades out mid-clip. */
    internal fun detailsAlpha(): Float = (1f - fraction / FADE_COMPLETED_AT).coerceIn(0f, 1f)

    /** The header moves up from the card's padding to the bar's. */
    internal fun contentShift(): Float = -paddingSpan / 2f * fraction

    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        // Called after every drag release, with or without velocity, once the fling has run out.
        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            settleToNearestEdge()
            return Velocity.Zero
        }
    }

    /**
     * Finishes a half-collapsed card by scrolling the list to the nearer edge. A list that cannot scroll that far stops
     * where it is and is not asked again. The scroll runs below user-input priority, so a new drag cancels it.
     */
    suspend fun settleToNearestEdge() {
        val list = scrollable ?: return
        val range = range
        val fraction = fraction
        if (range <= 0 || fraction <= 0f || fraction >= 1f) return
        val offset = scrollOffset()
        val target = if (fraction < SNAP_THRESHOLD) 0 else range
        val delta = (target - offset).toFloat()
        if (delta == 0f) return
        val spec = settleSpec
        list.scroll {
            if (spec == null) {
                scrollBy(delta)
                return@scroll
            }
            var scrolled = 0f
            AnimationState(0f).animateTo(delta, spec) {
                val step = value - scrolled
                val consumed = scrollBy(step)
                scrolled += consumed
                if (abs(consumed - step) > SCROLL_EPSILON) cancelAnimation()
            }
        }
    }

    internal companion object {
        const val FADE_COMPLETED_AT = 0.7f
        const val SNAP_THRESHOLD = 0.5f
        private const val SCROLL_EPSILON = 0.5f

        /** A card that stays as it is: previews, and the collapsed bar's golden. */
        fun fixed(collapsed: Boolean) = SportScoreCollapseState(null) { if (collapsed) Int.MAX_VALUE else 0 }
    }
}

/** A collapse state that follows [listState], the bookings list under the card. */
@Composable
fun rememberSportScoreCollapseState(listState: LazyListState): SportScoreCollapseState {
    val state = remember(listState) { SportScoreCollapseState(listState) { listState.firstItemScrollOffset() } }
    val motion = ItmoTheme.motion
    val reducedMotion = rememberReducedMotion()
    SideEffect {
        state.settleSpec = if (reducedMotion) null else motion.scheme.defaultSpatialSpec()
    }
    return state
}

/**
 * How far the list's first item has scrolled under the reserved padding; past the first item the card is fully
 * collapsed anyway. Item offsets start after the content padding, and items inside the padding are still visible.
 */
internal fun LazyListState.firstItemScrollOffset(): Int {
    val first = layoutInfo.visibleItemsInfo.firstOrNull() ?: return 0
    return if (first.index == 0) -first.offset else Int.MAX_VALUE
}

/**
 * The score [card] over the bookings [list]: the list fills the area and receives the expanded card, its margin and
 * the gap below it as top content padding, measured in the same pass, so no frame shows a row under the card. Rows
 * scroll under the card, so a backdrop in the screen's background fills the margins around it as it collapses; it
 * only draws, it never resizes. The card is placed at the screen margin and measured once at its expanded size.
 */
@Composable
fun SportScoreCollapsingLayout(
    state: SportScoreCollapseState,
    card: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    list: @Composable (contentPadding: PaddingValues) -> Unit,
) {
    val margin = ItmoTheme.spacing.screenMargin
    val gap = ItmoTheme.spacing.compact
    val backdrop = ItmoTheme.colorScheme.background
    SubcomposeLayout(modifier.nestedScroll(state.nestedScrollConnection)) { constraints ->
        val marginPx = margin.roundToPx()
        val gapPx = gap.roundToPx()
        val width = constraints.maxWidth
        val cardWidth = (width - marginPx * 2).coerceAtLeast(0)
        val cards = subcompose(Slot.Card, card).map { it.measure(Constraints.fixedWidth(cardWidth)) }
        val cardHeight = cards.maxOfOrNull { it.height } ?: 0
        val reserved = marginPx + cardHeight + gapPx
        val lists = subcompose(Slot.List) { list(PaddingValues(top = reserved.toDp())) }
            .map { it.measure(constraints) }
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else lists.maxOfOrNull { it.height } ?: 0
        val backdrops = subcompose(Slot.Backdrop) {
            Backdrop(state, backdrop, cardTop = marginPx, cardHeight = cardHeight, gap = gapPx)
        }.map { it.measure(Constraints.fixed(width, reserved.coerceAtMost(height))) }
        layout(width, height) {
            lists.forEach { it.place(0, 0) }
            backdrops.forEach { it.place(0, 0) }
            cards.forEach { it.place(marginPx, marginPx) }
        }
    }
}

@Composable
private fun Backdrop(state: SportScoreCollapseState, color: Color, cardTop: Int, cardHeight: Int, gap: Int) {
    Spacer(
        Modifier
            .fillMaxSize()
            .testTag(SportScoreCardTestTags.BACKDROP)
            .drawBehind {
                val fraction = state.fraction
                if (fraction <= 0f) return@drawBehind
                val bottom = cardTop + state.visibleCardHeight(cardHeight.toFloat()) + gap
                drawRect(color, size = Size(size.width, bottom.coerceAtMost(size.height)), alpha = fraction)
            },
    )
}

private enum class Slot { Card, List, Backdrop }

/** [base] cut to its top [visibleHeight] pixels, the outline included, so a card keeps its rounded bottom. */
internal class TopPartShape(private val base: Shape, private val visibleHeight: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        base.createOutline(Size(size.width, visibleHeight.coerceIn(0f, size.height)), layoutDirection, density)

    override fun equals(other: Any?): Boolean =
        other is TopPartShape && other.base == base && other.visibleHeight.roundToInt() == visibleHeight.roundToInt()

    override fun hashCode(): Int = 31 * base.hashCode() + visibleHeight.roundToInt()
}
