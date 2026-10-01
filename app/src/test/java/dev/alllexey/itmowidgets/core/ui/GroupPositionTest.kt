package dev.alllexey.itmowidgets.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupPositionTest {

    @Test
    fun `a lone row owns both outer corners`() {
        val position = GroupPosition.of(0, 1)
        assertEquals(GroupPosition.SINGLE, position)
        assertTrue(position.isFirst)
        assertTrue(position.isLast)
    }

    @Test
    fun `rows of a longer group take the outer corners only at the ends`() {
        assertEquals(
            listOf(GroupPosition.FIRST, GroupPosition.MIDDLE, GroupPosition.MIDDLE, GroupPosition.LAST),
            (0 until 4).map { GroupPosition.of(it, 4) }
        )
        assertFalse(GroupPosition.MIDDLE.isFirst || GroupPosition.MIDDLE.isLast)
        assertEquals(listOf(GroupPosition.FIRST, GroupPosition.LAST), (0 until 2).map { GroupPosition.of(it, 2) })
    }
}
