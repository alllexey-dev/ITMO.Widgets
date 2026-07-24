package dev.alllexey.itmowidgets.core.debug

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
    fun getOverride(): SportScoreOverride?
}

interface SportScoreOverrideController {
    fun getOverride(): SportScoreOverride?

    fun setOverride(value: SportScoreOverride?)
}
