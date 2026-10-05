package dev.alllexey.itmowidgets.designsystem.gesture

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TabSwipeRegistryTest {
    private val registry = TabSwipeRegistry()

    @Test
    fun aScrollerAtItsStartKeepsOnlyDragsTowardsTheNextTab() {
        handover(ROW, next = true, previous = false)

        assertEquals(listOf(true, false), bothDirections(IN_ROW))
    }

    @Test
    fun aScrollerInTheMiddleKeepsDragsBothWays() {
        handover(ROW, next = true, previous = true)

        assertEquals(listOf(true, true), bothDirections(IN_ROW))
    }

    @Test
    fun aScrollerAtItsEndKeepsOnlyDragsTowardsThePreviousTab() {
        handover(ROW, next = false, previous = true)

        assertEquals(listOf(false, true), bothDirections(IN_ROW))
    }

    @Test
    fun aScrollerReadsItsRoomWhenTheDragStarts() {
        var atEnd = false
        registry.addHandover(canScrollTowardsNext = { !atEnd }, canScrollTowardsPrevious = { true }).bounds = ROW
        assertTrue(registry.wantsDrag(IN_ROW, towardsNext = true))

        atEnd = true

        assertFalse(registry.wantsDrag(IN_ROW, towardsNext = true))
    }

    @Test
    fun aBlockedZoneKeepsEveryDrag() {
        registry.addBlocked().bounds = ROW

        assertEquals(listOf(true, true), bothDirections(IN_ROW))
    }

    @Test
    fun dragsOutsideEveryZoneAndOnItsFarEdgesGoToTheTabs() {
        handover(ROW, next = true, previous = true)
        registry.addBlocked().bounds = STRIP

        listOf(OUTSIDE, Offset(ROW.right, IN_ROW.y), Offset(IN_ROW.x, ROW.bottom)).forEach { point ->
            assertEquals(listOf(false, false), bothDirections(point), "at $point")
        }
        assertEquals(listOf(true, true), bothDirections(Offset(ROW.left, ROW.top)))
    }

    @Test
    fun anOuterScrollerWithRoomKeepsADragThatAnInnerOneAtItsEndCannot() {
        handover(PAGE, next = true, previous = false)
        handover(ROW, next = false, previous = true)

        assertEquals(listOf(true, true), bothDirections(IN_ROW))
        assertEquals(listOf(true, false), bothDirections(IN_PAGE_ONLY))
    }

    @Test
    fun nestedScrollersBothAtTheirEndsLetTheTabsHaveTheDrag() {
        handover(PAGE, next = false, previous = true)
        handover(ROW, next = false, previous = true)

        assertEquals(listOf(false, true), bothDirections(IN_ROW))
    }

    @Test
    fun aBlockedZoneInsideAScrollerAtItsEndStillKeepsTheDrag() {
        handover(PAGE, next = false, previous = false)
        registry.addBlocked().bounds = ROW

        assertEquals(listOf(true, true), bothDirections(IN_ROW))
        assertEquals(listOf(false, false), bothDirections(IN_PAGE_ONLY))
    }

    @Test
    fun aRemovedZoneCoversNothing() {
        val row = handover(ROW, next = true, previous = true)
        val strip = registry.addBlocked().apply { bounds = STRIP }

        row.remove()
        strip.remove()

        assertEquals(listOf(false, false), bothDirections(IN_ROW))
        assertEquals(listOf(false, false), bothDirections(STRIP.center))
    }

    @Test
    fun aZoneWithoutBoundsCoversNothing() {
        registry.addBlocked()

        assertEquals(listOf(false, false), bothDirections(Offset.Zero))
    }

    private fun handover(bounds: Rect, next: Boolean, previous: Boolean): TabSwipeZone =
        registry.addHandover(canScrollTowardsNext = { next }, canScrollTowardsPrevious = { previous })
            .apply { this.bounds = bounds }

    /** `wantsDrag` towards the next tab, then towards the previous one. */
    private fun bothDirections(point: Offset): List<Boolean> =
        listOf(true, false).map { registry.wantsDrag(point, towardsNext = it) }

    private companion object {
        val PAGE = Rect(0f, 0f, 1080f, 2000f)
        val ROW = Rect(0f, 400f, 1080f, 600f)
        val STRIP = Rect(0f, 100f, 1080f, 250f)
        val IN_ROW = Offset(540f, 500f)
        val IN_PAGE_ONLY = Offset(540f, 1000f)
        val OUTSIDE = Offset(540f, 2100f)
    }
}
