package dev.alllexey.itmowidgets.feature.auth.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DemoEntryTapsTest {

    @Test
    fun fiveTapsWithinSixSecondsOpenTheDemo() {
        val taps = DemoEntryTaps()

        val results = (0 until 5).map { taps.tap(atMillis = 10_000L + it * 1_500L) }

        assertEquals(listOf(false, false, false, false, true), results)
    }

    @Test
    fun aPauseLongerThanTheIntervalStartsTheCountOver() {
        val taps = DemoEntryTaps()
        repeat(4) { taps.tap(atMillis = it * 500L) }

        assertFalse(taps.tap(atMillis = 1_500L + 1_600L))
        repeat(3) { assertFalse(taps.tap(atMillis = 3_100L + (it + 1) * 500L)) }
        assertTrue(taps.tap(atMillis = 3_100L + 4 * 500L))
    }

    @Test
    fun theCountStartsFromZeroAfterASuccess() {
        val taps = DemoEntryTaps()
        repeat(4) { taps.tap(atMillis = it * 100L) }
        assertTrue(taps.tap(atMillis = 400L))

        val next = (1..4).map { taps.tap(atMillis = 400L + it * 100L) }

        assertEquals(listOf(false, false, false, false), next)
        assertTrue(taps.tap(atMillis = 900L))
    }
}
