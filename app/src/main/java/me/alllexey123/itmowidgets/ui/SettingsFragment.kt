package me.alllexey123.itmowidgets.ui

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import androidx.core.content.edit
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import me.alllexey123.itmowidgets.utils.LAST_UPDATE_TIMESTAMP_KEY
import me.alllexey123.itmowidgets.R
import me.alllexey123.itmowidgets.utils.ACCESS_TOKEN_EXPIRES_KEY
import me.alllexey123.itmowidgets.utils.ACCESS_TOKEN_KEY
import me.alllexey123.itmowidgets.utils.ID_TOKEN_KEY
import me.alllexey123.itmowidgets.utils.REFRESH_TOKEN_EXPIRES_KEY
import me.alllexey123.itmowidgets.utils.REFRESH_TOKEN_KEY
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        val refreshTokenPreference = findPreference<EditTextPreference>(REFRESH_TOKEN_KEY)

        refreshTokenPreference?.setOnPreferenceChangeListener { preference, newValue ->
            val currentValue = preferenceManager.sharedPreferences?.getString(REFRESH_TOKEN_KEY, "")

            if (newValue is String && newValue != currentValue) {
                onRefreshTokenUpdate()
            }

            true
        }
    }

    private fun onRefreshTokenUpdate() {
        val sharedPreferences = preferenceManager.sharedPreferences ?: return

        sharedPreferences.edit {
            remove(REFRESH_TOKEN_EXPIRES_KEY)
            remove(ACCESS_TOKEN_KEY)
            remove(ACCESS_TOKEN_EXPIRES_KEY)
            remove(ID_TOKEN_KEY)
        }
    }

    override fun onResume() {
        super.onResume()
        updateLastUpdateTime()
    }

    private fun updateLastUpdateTime() {
        val prefs = preferenceManager.sharedPreferences ?: return

        val lastUpdatePreference = findPreference<Preference>(LAST_UPDATE_TIMESTAMP_KEY)

        val timestamp = prefs.getLong(LAST_UPDATE_TIMESTAMP_KEY, 0L)

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