package dev.alllexey.itmowidgets.feature.auth.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoEntryTapsTest {

    @Test
    fun `five taps within six seconds open the demo`() {
        val taps = DemoEntryTaps()

        val results = (0 until 5).map { taps.tap(atMillis = 10_000L + it * 1_500L) }

        assertEquals(listOf(false, false, false, false, true), results)
    }

    @Test
    fun `a pause longer than the interval starts the count over`() {
        val taps = DemoEntryTaps()
        repeat(4) { taps.tap(atMillis = it * 500L) }

        assertFalse(taps.tap(atMillis = 1_500L + 1_600L))
        repeat(3) { assertFalse(taps.tap(atMillis = 3_100L + (it + 1) * 500L)) }
        assertTrue(taps.tap(atMillis = 3_100L + 4 * 500L))
    }

    @Test
    fun `the count starts from zero after a success`() {
        val taps = DemoEntryTaps()
        repeat(4) { taps.tap(atMillis = it * 100L) }
        assertTrue(taps.tap(atMillis = 400L))

        val next = (1..4).map { taps.tap(atMillis = 400L + it * 100L) }

        assertEquals(listOf(false, false, false, false), next)
        assertTrue(taps.tap(atMillis = 900L))
    }
}
