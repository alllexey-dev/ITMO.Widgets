package dev.alllexey.itmowidgets.designsystem.gesture

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/**
 * Where on a tab root a horizontal drag belongs to the content, for a shell whose tab swipe sits outside Compose's
 * nested scrolling and would ask it before its pan begins; none does yet, since iOS has no tab swipe.
 * [tabSwipeHandover] and [tabSwipeBlocked] keep their zones here in root pixels while they are attached. Android's tab
 * pager never reads it: nested scrolling already does the job. Main thread only, like the composition that fills it.
 */
class TabSwipeRegistry {

    private val zones = mutableListOf<TabSwipeZone>()

    /**
     * True when a horizontal drag that starts at [point] (root pixels) towards the next tab ([towardsNext]) or the
     * previous one belongs to the content: the point lies in a blocked zone, or in a handover zone that can still
     * scroll that way. Nested zones count independently, so an outer scroller with room wins over an inner one at its
     * end.
     */
    fun wantsDrag(point: Offset, towardsNext: Boolean): Boolean =
        zones.any { point in it.bounds && it.wants(towardsNext) }

    /** Adds a zone of a scroller that keeps a drag while it can scroll towards the next or the previous tab. */
    fun addHandover(canScrollTowardsNext: () -> Boolean, canScrollTowardsPrevious: () -> Boolean): TabSwipeZone =
        TabSwipeZone(this) { towardsNext -> if (towardsNext) canScrollTowardsNext() else canScrollTowardsPrevious() }
            .also(zones::add)

    /** Adds a zone that keeps every horizontal drag from the tabs. */
    fun addBlocked(): TabSwipeZone = TabSwipeZone(this) { true }.also(zones::add)

    internal fun remove(zone: TabSwipeZone) {
        zones.remove(zone)
    }
}

/** One zone of a [TabSwipeRegistry]; it covers nothing until [bounds] is set and nothing again after [remove]. */
class TabSwipeZone internal constructor(
    private val registry: TabSwipeRegistry,
    internal val wants: (towardsNext: Boolean) -> Boolean,
) {
    /** The zone in root pixels; the right and bottom edges are outside. */
    var bounds: Rect = Rect.Zero

    /** Takes the zone out of its registry, when its content leaves the composition. */
    fun remove() {
        registry.remove(this)
    }
}

/** The registry of the tab root the content sits on; null on Android and wherever no shell asks. */
val LocalTabSwipeRegistry = staticCompositionLocalOf<TabSwipeRegistry?> { null }
