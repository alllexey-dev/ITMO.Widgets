package dev.alllexey.itmowidgets.feature.auth.presentation

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * The hidden demo entry: five taps on the sign-in logo, each within [MAX_INTERVAL] of the previous one. Times come
 * from [timeSource], a monotonic source (`TimeSource.Monotonic` in the app, a `TestTimeSource` in tests).
 */
class DemoEntryTaps(private val timeSource: TimeSource) {

    private var count = 0
    private var lastTap: TimeMark? = null

    /** True on the tap that completes the sequence; the count starts over afterwards. */
    fun tap(): Boolean {
        val sinceLast = lastTap?.elapsedNow()
        lastTap = timeSource.markNow()
        count = if (count > 0 && sinceLast != null && sinceLast <= MAX_INTERVAL) count + 1 else 1
        if (count < REQUIRED_TAPS) return false
        count = 0
        return true
    }

    companion object {
        const val REQUIRED_TAPS = 5
        val MAX_INTERVAL: Duration = 1_500.milliseconds
    }
}
