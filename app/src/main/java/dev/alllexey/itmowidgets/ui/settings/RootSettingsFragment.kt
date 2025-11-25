package dev.alllexey.itmowidgets.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import dev.alllexey.itmowidgets.ItmoWidgetsApp
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.data.UtilityStorage.KEYS.LAST_UPDATE_TIMESTAMP_KEY
import dev.alllexey.itmowidgets.ui.error.ErrorLogActivity
import dev.alllexey.itmowidgets.ui.widgets.WidgetUtils
import java.lang.Thread.sleep
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class RootSettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        val appContainer = (requireContext().applicationContext as ItmoWidgetsApp).appContainer
        val storage = appContainer.storage

        val triggerUpdateButton = findPreference<Preference>("trigger_update_button")
        triggerUpdateButton?.setOnPreferenceClickListener { preference ->
            storage.utility.setLessonWidgetStyleChanged(true)
            updateAllWidgets()
            true
        }

        val errorLogButton = findPreference<Preference>("error_log")
        errorLogButton?.setOnPreferenceClickListener { preference ->
            val intent = Intent(context, ErrorLogActivity::class.java)
            startActivity(intent)
            true
        }

    }

    private fun updateAllWidgets() {
        thread {
            WidgetUtils.updateAllWidgets(preferenceManager.context)
            sleep(500L)
            activity?.runOnUiThread {
                updateLastUpdateTime()
            }
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