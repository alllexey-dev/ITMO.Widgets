package dev.alllexey.itmowidgets.ui.settings

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import dev.alllexey.itmowidgets.ItmoWidgetsApp
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.data.UserSettingsStorage.KEYS.DYNAMIC_QR_COLORS_KEY
import dev.alllexey.itmowidgets.data.UserSettingsStorage.KEYS.QR_SPOILER_ANIMATION_TYPE_KEY
import dev.alllexey.itmowidgets.data.UserSettingsStorage.KEYS.QR_SPOILER_KEY
import dev.alllexey.itmowidgets.ui.widgets.WidgetUtils
import kotlin.concurrent.thread

class QrWidgetSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.qr_widget_preferences, rootKey)

        val appContainer = (requireContext().applicationContext as ItmoWidgetsApp).appContainer
        val storage = appContainer.storage

        val dynamicQrColors = findPreference<SwitchPreference>(DYNAMIC_QR_COLORS_KEY)
        dynamicQrColors?.setOnPreferenceChangeListener { preference, newValue ->
            updateAllWidgets()
            true
        }

        val qrSpoiler = findPreference<SwitchPreference>(QR_SPOILER_KEY)
        qrSpoiler?.setOnPreferenceChangeListener { preference, newValue ->
            onQrSpoilerStateChanged(newValue as Boolean)
            updateAllWidgets()
            true
        }

        onQrSpoilerStateChanged(storage.settings.getQrSpoilerState())
    }

    fun onQrSpoilerStateChanged(newValue: Boolean) {
        findPreference<ListPreference>(QR_SPOILER_ANIMATION_TYPE_KEY)?.isEnabled = newValue
    }

    private fun updateAllWidgets() {
        thread {
            WidgetUtils.updateAllWidgets(preferenceManager.context)
        }
    }
}