package me.alllexey123.itmowidgets.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import me.alllexey123.itmowidgets.AppContainer
import me.alllexey123.itmowidgets.ItmoWidgetsApp
import me.alllexey123.itmowidgets.R
import me.alllexey123.itmowidgets.data.ACCESS_TOKEN_EXPIRES_KEY
import me.alllexey123.itmowidgets.data.ACCESS_TOKEN_KEY
import me.alllexey123.itmowidgets.data.BACKEND_ALLOW_KEY
import me.alllexey123.itmowidgets.data.BEFOREHAND_SCHEDULING_KEY
import me.alllexey123.itmowidgets.data.DYNAMIC_QR_COLORS_KEY
import me.alllexey123.itmowidgets.data.HIDE_PREVIOUS_LESSONS_KEY
import me.alllexey123.itmowidgets.data.HIDE_TEACHER_KEY
import me.alllexey123.itmowidgets.data.ID_TOKEN_KEY
import me.alllexey123.itmowidgets.data.LAST_UPDATE_TIMESTAMP_KEY
import me.alllexey123.itmowidgets.data.LESSON_WIDGET_STYLE_CHANGED_KEY
import me.alllexey123.itmowidgets.data.REFRESH_TOKEN_EXPIRES_KEY
import me.alllexey123.itmowidgets.data.REFRESH_TOKEN_KEY
import me.alllexey123.itmowidgets.data.SMART_SCHEDULING_KEY
import me.alllexey123.itmowidgets.ui.login.LoginActivity
import me.alllexey123.itmowidgets.ui.widgets.WidgetUtils
import java.lang.Thread.sleep
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        val appContainer = (requireContext().applicationContext as ItmoWidgetsApp).appContainer

        val storage = appContainer.storage

        val refreshTokenPreference = findPreference<EditTextPreference>(REFRESH_TOKEN_KEY)
        refreshTokenPreference?.setOnPreferenceChangeListener { preference, newValue ->
            val currentValue = preferenceManager.sharedPreferences?.getString(REFRESH_TOKEN_KEY, "")

            if (newValue is String && newValue != currentValue) {
                onRefreshTokenChange(appContainer)
            }

            true
        }

        val smartSchedulingPreference = findPreference<SwitchPreference>(SMART_SCHEDULING_KEY)
        smartSchedulingPreference?.setOnPreferenceChangeListener { preference, newValue ->
            updateAllWidgets()
            true
        }

        val beforehandSchedulingPreference = findPreference<SwitchPreference>(BEFOREHAND_SCHEDULING_KEY)
        beforehandSchedulingPreference?.setOnPreferenceChangeListener { preference, newValue ->
            updateAllWidgets()
            true
        }

        val triggerUpdateButton = findPreference<Preference>("trigger_update_button")
        triggerUpdateButton?.setOnPreferenceClickListener { preference ->
            storage.setLessonWidgetStyleChanged(true)
            updateAllWidgets()
            true
        }

        val refreshTokenHelp = findPreference<Preference>("refresh_token_help")
        refreshTokenHelp?.setOnPreferenceClickListener { preference ->
            val url = ContextCompat.getString(preferenceManager.context, R.string.refresh_token_help_url)
            val linkIntent = Intent(Intent.ACTION_VIEW, url.toUri())
            startActivity(linkIntent)
            true
        }

        listOf("single_lesson_widget_style", "list_lesson_widget_style")
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

        val dynamicQrColors = findPreference<SwitchPreference>(DYNAMIC_QR_COLORS_KEY)
        dynamicQrColors?.setOnPreferenceChangeListener { preference, newValue ->
            updateAllWidgets()
            true
        }

        val loginItmoId = findPreference<Preference>("login_itmoid")
        loginItmoId?.setOnPreferenceClickListener { preference ->
            val intent = Intent(context, LoginActivity::class.java)
            startActivity(intent)
            true
        }

        val allowBackend = findPreference<SwitchPreference>(BACKEND_ALLOW_KEY)
        allowBackend?.setOnPreferenceChangeListener { preference, newValue ->
            val firebaseToken = appContainer.storage.getFirebaseToken()
            if (firebaseToken != null && newValue as Boolean) {
                appContainer.backend.sendFirebaseToken(firebaseToken)
            }
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

    private fun onRefreshTokenChange(appContainer: AppContainer) {
        val prefs = preferenceManager.sharedPreferences ?: return

        prefs.edit {
            remove(REFRESH_TOKEN_EXPIRES_KEY)
            remove(ACCESS_TOKEN_KEY)
            remove(ACCESS_TOKEN_EXPIRES_KEY)
            remove(ID_TOKEN_KEY)
            remove(LESSON_WIDGET_STYLE_CHANGED_KEY)
        }

        appContainer.scheduleRepository.clearCache()

        updateAllWidgets()
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