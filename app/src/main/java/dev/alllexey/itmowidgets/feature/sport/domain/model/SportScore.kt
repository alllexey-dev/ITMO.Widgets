package dev.alllexey.itmowidgets.feature.sport.domain.model

import dev.alllexey.itmowidgets.core.sport.SportScoreSummary

data class SportScore(
    val attendances: Int,
    val other: Int,
    val attendancesData: List<SportAttendance>
) {
    val summary get() = SportScoreSummary(attendances, other)
    val otherCapped get() = summary.creditedBonus
    val total get() = summary.total
    val totalCapped get() = summary.totalCapped
    val need get() = summary.remaining
}
