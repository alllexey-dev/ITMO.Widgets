package dev.alllexey.itmowidgets.core.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class StableOrderTest {
    private data class Item(val id: String, val score: Int)

    private fun ranked(vararg items: Item) = items.sortedByDescending { it.score }

    @Test
    fun `the first list keeps its ranking`() {
        val order = StableOrder()

        assertEquals(listOf("b", "a"), order.arrange(ranked(Item("a", 1), Item("b", 2)), Item::id).map(Item::id))
    }

    @Test
    fun `a new score keeps every item in its place`() {
        val order = StableOrder()
        order.arrange(ranked(Item("a", 3), Item("b", 2), Item("c", 1)), Item::id)

        val afterVote = order.arrange(ranked(Item("a", 3), Item("b", 2), Item("c", 9)), Item::id)

        assertEquals(listOf("a", "b", "c"), afterVote.map(Item::id))
        assertEquals(9, afterVote.last().score)
    }

    @Test
    fun `an item seen for the first time follows the shown ones by its ranking`() {
        val order = StableOrder()
        order.arrange(ranked(Item("a", 3), Item("b", 2)), Item::id)

        val next = order.arrange(ranked(Item("a", 3), Item("b", 2), Item("new", 10), Item("other", 5)), Item::id)

        assertEquals(listOf("a", "b", "new", "other"), next.map(Item::id))
    }

    @Test
    fun `a reset ranks the next list afresh`() {
        val order = StableOrder()
        order.arrange(ranked(Item("a", 3), Item("b", 2)), Item::id)
        order.reset()

        assertEquals(listOf("b", "a"), order.arrange(ranked(Item("a", 1), Item("b", 2)), Item::id).map(Item::id))
    }
}
