package dev.alllexey.itmowidgets.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.alllexey.itmowidgets.core.settings.AccentColor
import kotlinx.coroutines.flow.Flow

/** How the app looks: the colour scheme's seed; a choice of the device that sign-out keeps. */
class AppearancePreferences(dataStore: DataStore<Preferences>) : DataStorePreferences(dataStore) {

    /** An absent or unknown value reads as [AccentColor.WALLPAPER], the scheme the app had before the choice. */
    fun observeAccentColor(): Flow<AccentColor> = observe { safeEnumOf(it[ACCENT_COLOR], AccentColor.WALLPAPER) }

    suspend fun setAccentColor(color: AccentColor) {
        write(ACCENT_COLOR, color.name)
    }

    private companion object {
        val ACCENT_COLOR = stringPreferencesKey("app_accent_color")
    }
}
