package dev.alllexey.itmowidgets.app

import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MainRouteQueueTest {
    private val qrPass = MainActivityRoute(R.id.navigation_home, screen = AppScreen.QR_PASS)
    private val today = MainActivityRoute(R.id.navigation_schedule, today = true)

    @Test fun `a route waits while the app is not ready and the root is not touched`() {
        val queue = MainRouteQueue().apply { offer(qrPass) }
        val selected = mutableListOf<Int>()

        val taken = queue.take(ready = false) { selected += it; true }

        assertNull(taken)
        assertEquals(qrPass, queue.pending)
        assertEquals(emptyList<Int>(), selected)
    }

    @Test fun `a route stays queued while its root cannot be selected`() {
        val queue = MainRouteQueue().apply { offer(today) }

        val taken = queue.take(ready = true) { false }

        assertNull(taken)
        assertEquals(today, queue.pending)
    }

    @Test fun `a ready route is handed out once`() {
        val queue = MainRouteQueue().apply { offer(qrPass) }
        val selected = mutableListOf<Int>()

        val first = queue.take(ready = true) { selected += it; true }
        val second = queue.take(ready = true) { selected += it; true }

        assertEquals(qrPass, first)
        assertNull(second)
        assertNull(queue.pending)
        assertEquals(listOf(R.id.navigation_home), selected)
    }

    @Test fun `a newer route replaces the waiting one`() {
        val queue = MainRouteQueue().apply { offer(qrPass) }

        queue.offer(today)

        assertEquals(today, queue.take(ready = true) { true })
    }
}
