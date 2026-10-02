package dev.alllexey.itmowidgets.feature.auth.presentation

/**
 * The hidden demo entry: five taps on the sign-in logo, each within
 * [MAX_INTERVAL_MILLIS] of the previous one. Times come from a monotonic clock.
 */
class DemoEntryTaps {

    private var count = 0
    private var lastTapMillis = 0L

    /** True on the tap that completes the sequence; the count starts over afterwards. */
    fun tap(atMillis: Long): Boolean {
        count = if (count > 0 && atMillis - lastTapMillis <= MAX_INTERVAL_MILLIS) count + 1 else 1
        lastTapMillis = atMillis
        if (count < REQUIRED_TAPS) return false
        count = 0
        return true
    }

    companion object {
        const val REQUIRED_TAPS = 5
        const val MAX_INTERVAL_MILLIS = 1_500L
    }
}
