package dev.alllexey.itmowidgets.core.debug

import android.content.SharedPreferences
import androidx.core.content.edit
import dev.alllexey.itmowidgets.BuildConfig

class SharedPreferencesSportLessonTemplateStore(
    private val preferences: SharedPreferences
) : SportLessonTemplateStore {

    override fun isEnabled(): Boolean {
        return BuildConfig.DEBUG && preferences.getBoolean(SPORT_LESSON_TEMPLATES_KEY, false)
    }

    override fun setEnabled(enabled: Boolean) {
        if (!BuildConfig.DEBUG) return
        preferences.edit(commit = true) {
            putBoolean(SPORT_LESSON_TEMPLATES_KEY, enabled)
        }
    }

    private companion object {
        const val SPORT_LESSON_TEMPLATES_KEY = "debug_sport_lesson_templates"
    }
}
