package dev.alllexey.itmowidgets.core.debug

import dev.alllexey.itmowidgets.domain.model.sport.SportScore

class DefaultSportScoreOverrideProvider(
    private val overrideStore: SportScoreOverrideStore
) : SportScoreOverrideProvider, SportScoreOverrideController {

    override fun apply(score: SportScore): SportScore {
        val override = overrideStore.getOverride() ?: return score
        return score.copy(
            attendances = override.attendances,
            other = override.bonus
        )
    }

    override fun getOverride(): SportScoreOverride? {
        return overrideStore.getOverride()
    }

    override fun setOverride(value: SportScoreOverride?) {
        overrideStore.setOverride(value)
    }
}

interface SportScoreOverrideStore {
    fun getOverride(): SportScoreOverride?

    fun setOverride(value: SportScoreOverride?)
}
