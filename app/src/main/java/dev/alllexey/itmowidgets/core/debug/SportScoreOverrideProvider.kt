package dev.alllexey.itmowidgets.core.debug

import dev.alllexey.itmowidgets.domain.model.sport.SportScore

data class SportScoreOverride(
    val attendances: Int,
    val bonus: Int
) {
    init {
        require(attendances >= 0) { "Attendance points must be non-negative" }
        require(bonus >= 0) { "Bonus points must be non-negative" }
    }
}

interface SportScoreOverrideProvider {
    fun apply(score: SportScore): SportScore
}

interface SportScoreOverrideController {
    fun getOverride(): SportScoreOverride?

    fun setOverride(value: SportScoreOverride?)
}
