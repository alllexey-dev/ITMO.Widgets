package dev.alllexey.itmowidgets.feature.recordbook.domain

import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

private val SPORT_WARNING_PERIOD = 28.days

/**
 * Early in the semester a shortfall is normal; it becomes a problem in the last four weeks
 * or once the period is over. An unknown end of the current period never raises an alarm.
 */
fun isSportBehind(state: RecordbookSportState.Content, now: Instant): Boolean {
    if (state.score.remaining <= 0) return false
    if (!state.current) return true
    val endsAt = state.endsAt ?: return false
    return now >= endsAt - SPORT_WARNING_PERIOD
}
