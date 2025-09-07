package me.alllexey123.itmowidgets.ui

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import androidx.core.content.edit
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import me.alllexey123.itmowidgets.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        val refreshTokenPreference = findPreference<EditTextPreference>("refresh_token")

        refreshTokenPreference?.setOnPreferenceChangeListener { preference, newValue ->
            val currentValue = preferenceManager.sharedPreferences?.getString("refresh_token", "")

            if (newValue is String && newValue != currentValue) {
                resetTokenExpirationDate()
            }

            true
        }
    }

    private fun resetTokenExpirationDate() {
        val sharedPreferences = preferenceManager.sharedPreferences ?: return

        sharedPreferences.edit {
            putLong("refresh_token_expires", 0L)
        }
    }

    override fun onResume() {
        super.onResume()
        updateLastUpdateTime()
    }

    private fun updateLastUpdateTime() {
        val prefs = preferenceManager.sharedPreferences ?: return

        val lastUpdatePreference = findPreference<Preference>("last_update_status")

        val timestamp = prefs.getLong("last_update_timestamp", 0L)

        if (timestamp == 0L) {
            lastUpdatePreference?.summary = "Обновлений пока не было"
        } else {
            val date = Date(timestamp)
            val format = SimpleDateFormat("MMM dd, yyyy, HH:mm:ss", Locale.getDefault())
            val formattedDate = format.format(date)

            lastUpdatePreference?.summary = formattedDate
        }
    }
}