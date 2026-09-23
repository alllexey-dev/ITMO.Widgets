package dev.alllexey.itmowidgets.feature.recordbook.domain

import java.time.OffsetDateTime

private const val SPORT_WARNING_DAYS = 28L

/**
 * Early in the semester a shortfall is normal; it becomes a problem in the last four weeks
 * or once the period is over. An unknown end of the current period never raises an alarm.
 */
fun isSportBehind(state: RecordbookSportState.Content, now: OffsetDateTime): Boolean {
    if (state.score.remaining <= 0) return false
    if (!state.current) return true
    val endsAt = state.endsAt ?: return false
    return !now.isBefore(endsAt.minusDays(SPORT_WARNING_DAYS))
}
