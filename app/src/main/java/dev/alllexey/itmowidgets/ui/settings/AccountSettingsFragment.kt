package dev.alllexey.itmowidgets.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import dev.alllexey.itmowidgets.AppContainer
import dev.alllexey.itmowidgets.ItmoWidgetsApp
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.data.MyItmoStorage.KEYS.REFRESH_TOKEN_KEY
import dev.alllexey.itmowidgets.data.UserSettingsStorage.KEYS.CUSTOM_SERVICES_ALLOW_KEY
import dev.alllexey.itmowidgets.data.UtilityStorage.KEYS.LESSON_WIDGET_STYLE_CHANGED_KEY
import dev.alllexey.itmowidgets.ui.login.LoginActivity
import dev.alllexey.itmowidgets.ui.widgets.WidgetUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.concurrent.thread

class AccountSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.account_preferences, rootKey)

        val appContainer = (requireContext().applicationContext as ItmoWidgetsApp).appContainer

        val loginItmoId = findPreference<Preference>("login_itmoid")
        loginItmoId?.setOnPreferenceClickListener { preference ->
            val intent = Intent(context, LoginActivity::class.java)
            startActivity(intent)
            true
        }

        val refreshTokenPreference = findPreference<EditTextPreference>(REFRESH_TOKEN_KEY)
        refreshTokenPreference?.setOnPreferenceChangeListener { preference, newValue ->
            val currentValue = preferenceManager.sharedPreferences?.getString(REFRESH_TOKEN_KEY, "")

            if (newValue is String && newValue != currentValue) {
                onRefreshTokenChange(appContainer)
            }

            true
        }

        val refreshTokenHelp = findPreference<Preference>("refresh_token_help")
        refreshTokenHelp?.setOnPreferenceClickListener { preference ->
            val url =
                ContextCompat.getString(preferenceManager.context, R.string.refresh_token_help_url)
            val linkIntent = Intent(Intent.ACTION_VIEW, url.toUri())
            startActivity(linkIntent)
            true
        }

        val allowBackend = findPreference<SwitchPreference>(CUSTOM_SERVICES_ALLOW_KEY)
        allowBackend?.setOnPreferenceChangeListener { preference, newValue ->
            val firebaseToken = appContainer.storage.utility.getFirebaseToken()
            if (firebaseToken != null && newValue as Boolean) {
                CoroutineScope(Dispatchers.IO).launch {
                    appContainer.itmoWidgets.sendFirebaseToken(firebaseToken)
                }
            }
            true
        }
    }

    private fun onRefreshTokenChange(appContainer: AppContainer) {
        val prefs = preferenceManager.sharedPreferences ?: return

        prefs.edit {
            remove(LESSON_WIDGET_STYLE_CHANGED_KEY)
        }

        appContainer.scheduleRepository.clearCache()
        appContainer.qrToolkit.repository.clearCache()
        appContainer.storage.itmoWidgets.clearTokens()
        appContainer.storage.myItmo.clearTokens()

        updateAllWidgets()
    }

    private fun updateAllWidgets() {
        thread {
            WidgetUtils.updateAllWidgets(preferenceManager.context)
        }
    }
}