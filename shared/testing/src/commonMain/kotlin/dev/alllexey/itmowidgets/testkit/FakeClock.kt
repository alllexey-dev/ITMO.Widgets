package dev.alllexey.itmowidgets.testkit

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/** A [Clock] that stands still at [start] until a test moves it with [advanceBy]. */
class FakeClock(start: Instant) : Clock {
    private var current: Instant = start

    override fun now(): Instant = current

    fun advanceBy(duration: Duration) {
        current += duration
    }
}
