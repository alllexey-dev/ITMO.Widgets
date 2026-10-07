package dev.alllexey.itmowidgets.feature.sport.data.debug

/**
 * The debug tools' points for the current sport period, which replace MyITMO's attendance and bonus sums. Only a debug
 * build stores one; every other build answers `null`. The platform binds it: on Android `SportBridge` adapts the
 * app's `core/debug` override store.
 */
fun interface SportScoreOverrideSource {
    fun getOverride(): SportScoreOverridePoints?
}

/** Attendance and bonus points that stand in for MyITMO's sums of the current period. */
data class SportScoreOverridePoints(val attendances: Int, val bonus: Int)
