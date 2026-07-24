package dev.alllexey.itmowidgets.core.debug

class DefaultSportScoreOverrideProvider(
    private val overrideStore: SportScoreOverrideStore
) : SportScoreOverrideProvider, SportScoreOverrideController {

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
