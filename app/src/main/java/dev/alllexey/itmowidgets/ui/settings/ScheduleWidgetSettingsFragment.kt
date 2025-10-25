package dev.alllexey.itmowidgets.ui.settings

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.data.UserSettingsStorage.KEYS.BEFOREHAND_SCHEDULING_KEY
import dev.alllexey.itmowidgets.data.UserSettingsStorage.KEYS.HIDE_PREVIOUS_LESSONS_KEY
import dev.alllexey.itmowidgets.data.UserSettingsStorage.KEYS.HIDE_TEACHER_KEY
import dev.alllexey.itmowidgets.data.UserSettingsStorage.KEYS.LIST_LESSON_WIDGET_STYLE_KEY
import dev.alllexey.itmowidgets.data.UserSettingsStorage.KEYS.SINGLE_LESSON_WIDGET_STYLE_KEY
import dev.alllexey.itmowidgets.data.UserSettingsStorage.KEYS.SMART_SCHEDULING_KEY
import dev.alllexey.itmowidgets.ui.widgets.WidgetUtils
import kotlin.concurrent.thread

class ScheduleWidgetSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.schedule_widget_preferences, rootKey)

        val smartSchedulingPreference = findPreference<SwitchPreference>(SMART_SCHEDULING_KEY)
        smartSchedulingPreference?.setOnPreferenceChangeListener { preference, newValue ->
            updateAllWidgets()
            true
        }

        val beforehandSchedulingPreference =
            findPreference<SwitchPreference>(BEFOREHAND_SCHEDULING_KEY)
        beforehandSchedulingPreference?.setOnPreferenceChangeListener { preference, newValue ->
            updateAllWidgets()
            true
        }

        listOf(SINGLE_LESSON_WIDGET_STYLE_KEY, LIST_LESSON_WIDGET_STYLE_KEY)
            .map { s -> findPreference<ListPreference>(s) }.forEach { preference ->
                preference?.setOnPreferenceChangeListener { pref, newValue ->
                    updateAllWidgets()
                    true
                }
            }

        val hideTeacherPreference = findPreference<SwitchPreference>(HIDE_TEACHER_KEY)
        hideTeacherPreference?.setOnPreferenceChangeListener { preference, newValue ->
            updateAllWidgets()
            true
        }

        val hidePreviousLessons = findPreference<SwitchPreference>(HIDE_PREVIOUS_LESSONS_KEY)
        hidePreviousLessons?.setOnPreferenceChangeListener { preference, newValue ->
            updateAllWidgets()
            true
        }
    }

    private fun updateAllWidgets() {
        thread {
            WidgetUtils.updateAllWidgets(preferenceManager.context)
        }
    }
}