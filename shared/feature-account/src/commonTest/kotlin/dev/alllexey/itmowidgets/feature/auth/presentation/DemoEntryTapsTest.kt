package dev.alllexey.itmowidgets.feature.auth.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

class DemoEntryTapsTest {

    private val time = TestTimeSource()
    private val taps = DemoEntryTaps(time)

    @Test
    fun fiveTapsWithinSixSecondsOpenTheDemo() {
        val results = (0 until 5).map {
            if (it > 0) time += 1_500.milliseconds
            taps.tap()
        }

        assertEquals(listOf(false, false, false, false, true), results)
    }

    @Test
    fun aPauseLongerThanTheIntervalStartsTheCountOver() {
        repeat(4) {
            taps.tap()
            time += 500.milliseconds
        }

        time += 1_100.milliseconds
        assertFalse(taps.tap())
        repeat(3) {
            time += 500.milliseconds
            assertFalse(taps.tap())
        }
        time += 500.milliseconds
        assertTrue(taps.tap())
    }

    @Test
    fun theCountStartsFromZeroAfterASuccess() {
        repeat(4) {
            taps.tap()
            time += 100.milliseconds
        }
        assertTrue(taps.tap())

        val next = (1..4).map {
            time += 100.milliseconds
            taps.tap()
        }

        assertEquals(listOf(false, false, false, false), next)
        time += 100.milliseconds
        assertTrue(taps.tap())
    }
}
