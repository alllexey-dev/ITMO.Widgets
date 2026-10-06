package dev.alllexey.itmowidgets.feature.sport.ui.my

import dev.alllexey.itmowidgets.feature.sport.domain.model.SportScore

/**
 * The ring's two sectors in percent of the full turn. Up to the goal they are the points themselves; above it they
 * are shares of the total, so the ring stays closed with both colours in proportion (`SportMyFragment.updateScoreUi`).
 */
internal data class SportScoreRingShares(val attendance: Float, val bonus: Float)

/** Null without any points: the ring shows only its track. */
internal fun SportScore.ringShares(): SportScoreRingShares? {
    val total = total
    if (total <= 0) return null
    return if (total > SPORT_SCORE_GOAL) {
        SportScoreRingShares(attendances * 100f / total, otherCapped * 100f / total)
    } else {
        SportScoreRingShares(attendances.toFloat(), otherCapped.toFloat())
    }
}

/** The three numbers the card counts: attendance, the credited bonus (at most 40) and their sum. */
internal data class SportScoreCounts(val attendances: Int, val bonus: Int, val total: Int) {

    /** The frame [fraction] of the way to [target]; truncated like the View counters, so a frame never overshoots. */
    fun towards(target: SportScoreCounts, fraction: Float): SportScoreCounts = SportScoreCounts(
        attendances = countAt(attendances, target.attendances, fraction),
        bonus = countAt(bonus, target.bonus, fraction),
        total = countAt(total, target.total, fraction),
    )

    companion object {
        val Zero = SportScoreCounts(0, 0, 0)
    }
}

internal fun SportScore.counts(): SportScoreCounts = SportScoreCounts(attendances, otherCapped, total)

internal fun countAt(start: Int, end: Int, fraction: Float): Int = (start + (end - start) * fraction).toInt()

/**
 * The bonus line shows the raw bonus beside the credited one (`40 (52)`) only once the counters have settled: while
 * they run, every number is a plain count.
 */
internal fun SportScore.showsBonusOverLimit(settled: Boolean): Boolean = settled && other > otherCapped

/** The status chip reads `Зачёт` once nothing is missing, otherwise how many points are left. */
internal val SportScore.passed: Boolean get() = need == 0

internal const val SPORT_SCORE_GOAL = 100
