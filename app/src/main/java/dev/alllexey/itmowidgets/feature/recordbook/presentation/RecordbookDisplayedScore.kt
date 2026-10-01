package dev.alllexey.itmowidgets.feature.recordbook.presentation

import dev.alllexey.itmowidgets.core.sport.SportScoreSummary
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportState
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookRate
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject

/** Sports progress and the official result are independent, including after 100 points. */
fun RecordbookSubject.displayedScore(sport: RecordbookSportState?): RecordbookProgress =
    RecordbookProgress(if (isPhysicalEducation) {
        (sport as? RecordbookSportState.Content)?.score?.total?.toDouble()
    } else score)

/**
 * The own total from a connected sheet stands in for the score only while My ITMO and BARS have none: not for physical
 * education, an absence or a final grade.
 */
fun RecordbookSubject.sheetFallback(total: String?): String? =
    total.takeIf {
        !isPhysicalEducation && !absent && normalizedRate == RecordbookRate.InProgress && displayedScore(null).value == null
    }

/** Match My Sport's proportions above 100 without clipping away the bonus sector. */
data class RecordbookSportProgress(val score: SportScoreSummary) {
    private val scale = maxOf(100, score.total).toFloat()
    val attendancePercentage: Float get() = score.attendances / scale * 100
    val bonusPercentage: Float get() = score.creditedBonus / scale * 100
}
