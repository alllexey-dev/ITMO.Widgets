package dev.alllexey.itmowidgets.testkit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class FakeClockTest {
    private val start = Instant.parse("2026-09-01T09:00:00Z")

    @Test
    fun standsStillUntilAdvanced() {
        val clock = FakeClock(start)

        assertEquals(start, clock.now())
        clock.advanceBy(90.minutes)
        assertEquals(Instant.parse("2026-09-01T10:30:00Z"), clock.now())
    }
}
