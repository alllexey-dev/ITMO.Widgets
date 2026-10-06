package dev.alllexey.itmowidgets.designsystem.gesture

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScrollModifierNode
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputEventHandler
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.requireLayoutDirection
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import kotlin.coroutines.cancellation.CancellationException

/**
 * For every horizontal scroller on a tab root (`LazyRow`, `HorizontalPager`, `horizontalScroll`, a chip row), with
 * the scroller's own [state]: the scroller consumes first, and in a gesture that started while it could scroll in the
 * drag direction no leftover drag and no fling velocity reach an outer horizontal scroller, so the content never
 * flies over into the next tab. A new gesture that starts at the scroller's edge, towards that edge, goes to the outer
 * scroller and switches the tab. Inside sheets and overlays it changes nothing. For a scroller in reading direction
 * (no `reverseLayout`).
 */
fun Modifier.tabSwipeHandover(state: ScrollableState): Modifier = this then TabSwipeHandoverElement(state)

private data class TabSwipeHandoverElement(val state: ScrollableState) : ModifierNodeElement<TabSwipeHandoverNode>() {
    override fun create() = TabSwipeHandoverNode(state)

    override fun update(node: TabSwipeHandoverNode) {
        node.state = state
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "tabSwipeHandover"
        properties["state"] = state
    }
}

private class TabSwipeHandoverNode(var state: ScrollableState) : TabSwipeZoneNode() {

    init {
        // The Initial pass sees the down before the scroller under it, without consuming anything.
        delegate(
            SuspendingPointerInputModifierNode(
                PointerInputEventHandler {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        startGesture()
                    }
                },
            ),
        )
    }

    override fun addZone(registry: TabSwipeRegistry): TabSwipeZone = registry.addHandover(
        canScrollTowardsNext = { state.canScrollForward },
        canScrollTowardsPrevious = { state.canScrollBackward },
    )

    override fun ownsGestureStartingWith(delta: Offset): Boolean {
        // A finger moving towards the start of the reading direction scrolls the content forward.
        val forward = (delta.x < 0f) == (requireLayoutDirection() == LayoutDirection.Ltr)
        return if (forward) state.canScrollForward else state.canScrollBackward
    }
}

/**
 * The part of [tabSwipeHandover] and [tabSwipeBlocked] that keeps a gesture's horizontal leftovers from outer
 * scrollers and keeps the node's zone in [LocalTabSwipeRegistry] while it is attached.
 */
internal abstract class TabSwipeZoneNode :
    DelegatingNode(),
    NestedScrollConnection,
    CompositionLocalConsumerModifierNode,
    GlobalPositionAwareModifierNode {

    /** Whether the current gesture's leftovers stay here; null until its first horizontal scroll. */
    private var ownsGesture: Boolean? = null
    private var zone: TabSwipeZone? = null
    private var zoneRegistry: TabSwipeRegistry? = null

    init {
        delegate(nestedScrollModifierNode(this, null))
    }

    protected abstract fun addZone(registry: TabSwipeRegistry): TabSwipeZone

    /** Decided once per gesture, from its first horizontal [delta] (pointer direction) before anything consumed it. */
    protected abstract fun ownsGestureStartingWith(delta: Offset): Boolean

    /** A new finger is down: the next scroll decides again. */
    protected fun startGesture() {
        ownsGesture = null
    }

    override fun onDetach() {
        zone?.remove()
        zone = null
        zoneRegistry = null
        ownsGesture = null
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        // Read on every placement rather than once on attach, so a registry provided later is followed.
        val registry = currentValueOf(LocalTabSwipeRegistry)
        if (registry !== zoneRegistry) {
            zone?.remove()
            zone = registry?.let(::addZone)
            zoneRegistry = registry
        }
        zone?.bounds = coordinates.boundsInRoot()
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (ownsGesture == null && source == NestedScrollSource.UserInput && available.x != 0f) {
            ownsGesture = ownsGestureStartingWith(available)
        }
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (ownsGesture != true || available.x == 0f) return Offset.Zero
        // A fling that reached the edge stops there, as the pager's own connection stops its pages' flings;
        // swallowing its leftovers instead would keep an invisible fling running.
        if (source == NestedScrollSource.SideEffect) throw CancellationException("The scroller reached its edge")
        return Offset(available.x, 0f)
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        val kept = if (ownsGesture == true) Velocity(available.x, 0f) else Velocity.Zero
        ownsGesture = null
        return kept
    }
}
