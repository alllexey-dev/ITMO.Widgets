package dev.alllexey.itmowidgets.designsystem.components.groups

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GroupPositionTest {
    @Test
    fun aLoneRowOwnsBothOuterCorners() {
        val position = GroupPosition.of(0, 1)

        assertEquals(GroupPosition.Single, position)
        assertTrue(position.isFirst)
        assertTrue(position.isLast)
    }

    @Test
    fun rowsOfALongerGroupTakeTheOuterCornersOnlyAtTheEnds() {
        assertEquals(
            listOf(GroupPosition.First, GroupPosition.Middle, GroupPosition.Middle, GroupPosition.Last),
            (0 until 4).map { GroupPosition.of(it, 4) },
        )
        assertFalse(GroupPosition.Middle.isFirst || GroupPosition.Middle.isLast)
        assertEquals(listOf(GroupPosition.First, GroupPosition.Last), (0 until 2).map { GroupPosition.of(it, 2) })
    }
}
