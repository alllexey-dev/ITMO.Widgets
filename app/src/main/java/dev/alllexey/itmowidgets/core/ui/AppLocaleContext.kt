package dev.alllexey.itmowidgets.core.ui

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * The app speaks Russian on every phone: Russian plural rules and Russian library strings, whatever the system
 * language. The catalog has only the default `values`, so plain strings never change; plurals and library
 * strings follow the locale of the context that resolves them.
 */
object AppLocale {
    const val TAG = "ru"

    /**
     * Sets the per-app locale once per process. On API 33+ the framework keeps it; a call while it is already
     * set would restart the activities in a loop, so it is skipped then. AppCompat's own setter is a silent
     * no-op on API 33+ before an activity exists, hence LocaleManager. Below 33 AppCompat localizes
     * AppCompatActivity contexts only. AppCompat's `autoStoreLocales` stays off: on API 33+ its first-activity
     * sync reads an empty list before the delegate registers and clears the framework value.
     */
    fun apply(context: Context) {
        if (Build.VERSION.SDK_INT >= 33) {
            val manager = context.getSystemService(LocaleManager::class.java)
            if (manager.applicationLocales.toLanguageTags() != TAG) {
                manager.applicationLocales = LocaleList.forLanguageTags(TAG)
            }
        } else {
            val locales = LocaleListCompat.forLanguageTags(TAG)
            if (AppCompatDelegate.getApplicationLocales() != locales) AppCompatDelegate.setApplicationLocales(locales)
        }
    }
}

/**
 * A context whose resources resolve in the app locale, for text built outside an activity (notifications,
 * widgets, tiles). Needed on every API level: below 33 only AppCompat activities are localized, and on API 33+
 * the application context falls back to the system locale once the last activity is destroyed. Returns the
 * context itself when it is already Russian; otherwise built from its current configuration on every call,
 * since a context frozen once would keep a stale night mode or font scale.
 */
fun Context.withAppLocale(): Context {
    val current = resources.configuration
    if (current.locales[0].language == AppLocale.TAG) return this
    val configuration = Configuration(current).apply { setLocales(LocaleList.forLanguageTags(AppLocale.TAG)) }
    return createConfigurationContext(configuration)
}
