package dev.alllexey.itmowidgets.core.testing

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** A wall clock a test moves by hand; Moscow by default, like the app's academic time. */
class MutableClock(var now: Instant, private val zone: ZoneId = ZoneId.of("Europe/Moscow")) : Clock() {
    fun advance(duration: Duration) {
        now = now.plus(duration)
    }

    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)

    override fun instant(): Instant = now
}
