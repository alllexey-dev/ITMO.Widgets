package dev.alllexey.itmowidgets.ui.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.data.UserSettingsStorage.KEYS.DYNAMIC_QR_COLORS_KEY
import dev.alllexey.itmowidgets.ui.widgets.WidgetUtils
import kotlin.concurrent.thread

class QrWidgetSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.qr_widget_preferences, rootKey)

        val dynamicQrColors = findPreference<SwitchPreference>(DYNAMIC_QR_COLORS_KEY)
        dynamicQrColors?.setOnPreferenceChangeListener { preference, newValue ->
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