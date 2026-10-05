package dev.alllexey.itmowidgets.core.presentation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EventQueueTest {

    @Test
    fun `an event sent between collectors arrives once on the next collection`() = runTest {
        val queue = EventQueue<String>()
        val first = mutableListOf<String>()
        val second = mutableListOf<String>()

        val view = backgroundScope.launch { queue.events.collect { first += it } }
        queue.send("shown")
        runCurrent()
        view.cancel()
        runCurrent()

        // Sent while the view is recreated: nobody collects.
        queue.send("after rotation")
        backgroundScope.launch { queue.events.collect { second += it } }
        runCurrent()

        assertEquals(listOf("shown"), first)
        assertEquals(listOf("after rotation"), second)
    }
}
