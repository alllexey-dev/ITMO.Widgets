package dev.alllexey.itmowidgets.designsystem.gesture

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputEventHandler
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import kotlin.math.abs

/**
 * A zone on a tab root where a horizontal drag switches no tab: an arrow-only week strip, a slider, any control whose
 * swipe would be ambiguous. Taps and vertical drags pass through, and the zone's own controls keep their gestures
 * (a slider still slides); a horizontal drag nothing inside takes moves nothing, and no leftover of an inner scroller
 * reaches the tabs.
 */
fun Modifier.tabSwipeBlocked(): Modifier = this then TabSwipeBlockedElement

private data object TabSwipeBlockedElement : ModifierNodeElement<TabSwipeBlockedNode>() {
    override fun create() = TabSwipeBlockedNode()

    override fun update(node: TabSwipeBlockedNode) = Unit

    override fun InspectorInfo.inspectableProperties() {
        name = "tabSwipeBlocked"
    }
}

private class TabSwipeBlockedNode : TabSwipeZoneNode() {

    init {
        // The Main pass runs after the zone's children and before the tab pager around it: what a child left
        // unconsumed past the touch slop, and more horizontal than vertical, is consumed here, so the pager's own
        // slop check sees it taken.
        delegate(
            SuspendingPointerInputModifierNode(
                PointerInputEventHandler {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        startGesture()
                        val drag = awaitTouchSlopOrCancellation(down.id) { change, overSlop ->
                            if (abs(overSlop.x) > abs(overSlop.y)) change.consume()
                        } ?: return@awaitEachGesture
                        drag(drag.id) { it.consume() }
                    }
                },
            ),
        )
    }

    override fun addZone(registry: TabSwipeRegistry): TabSwipeZone = registry.addBlocked()

    override fun ownsGestureStartingWith(delta: Offset): Boolean = true
}
