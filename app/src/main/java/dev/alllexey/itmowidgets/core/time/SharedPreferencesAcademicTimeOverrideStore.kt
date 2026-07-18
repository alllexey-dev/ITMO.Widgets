package dev.alllexey.itmowidgets.core.time

import android.content.SharedPreferences
import androidx.core.content.edit
import dev.alllexey.itmowidgets.BuildConfig
import java.time.LocalDate

class SharedPreferencesAcademicTimeOverrideStore(
    private val preferences: SharedPreferences
) : AcademicTimeOverrideStore {

    override fun getOverrideDate(): LocalDate? {
        if (!BuildConfig.DEBUG) return null

        val value = preferences.getString(ACADEMIC_DATE_OVERRIDE_KEY, null) ?: return null
        return runCatching { LocalDate.parse(value) }.getOrNull()
    }

    override fun setOverrideDate(date: LocalDate?) {
        if (!BuildConfig.DEBUG) return

        preferences.edit(commit = true) {
            if (date == null) {
                remove(ACADEMIC_DATE_OVERRIDE_KEY)
            } else {
                putString(ACADEMIC_DATE_OVERRIDE_KEY, date.toString())
            }
        }
    }

    private companion object {
        const val ACADEMIC_DATE_OVERRIDE_KEY = "debug_academic_date_override"
    }
}
