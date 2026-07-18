package dev.alllexey.itmowidgets.core.debug

import android.content.SharedPreferences
import androidx.core.content.edit
import dev.alllexey.itmowidgets.BuildConfig

class SharedPreferencesSportScoreOverrideStore(
    private val preferences: SharedPreferences
) : SportScoreOverrideStore {

    override fun getOverride(): SportScoreOverride? {
        if (!BuildConfig.DEBUG) return null

        val serialized = preferences.getString(SPORT_SCORE_OVERRIDE_KEY, null) ?: return null
        return runCatching {
            val (attendances, bonus) = serialized.split(SEPARATOR, limit = 2)
            SportScoreOverride(
                attendances = attendances.toInt(),
                bonus = bonus.toInt()
            )
        }.getOrNull()
    }

    override fun setOverride(value: SportScoreOverride?) {
        if (!BuildConfig.DEBUG) return

        preferences.edit(commit = true) {
            if (value == null) {
                remove(SPORT_SCORE_OVERRIDE_KEY)
            } else {
                putString(
                    SPORT_SCORE_OVERRIDE_KEY,
                    "${value.attendances}$SEPARATOR${value.bonus}"
                )
            }
        }
    }

    private companion object {
        const val SPORT_SCORE_OVERRIDE_KEY = "debug_sport_score_override"
        const val SEPARATOR = ","
    }
}
