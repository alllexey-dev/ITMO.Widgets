package dev.alllexey.itmowidgets.ui.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import dev.alllexey.itmowidgets.R

class SportSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.sport_preferences, rootKey)
    }
}