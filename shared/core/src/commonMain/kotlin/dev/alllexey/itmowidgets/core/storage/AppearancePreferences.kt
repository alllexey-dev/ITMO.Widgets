package dev.alllexey.itmowidgets.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.alllexey.itmowidgets.core.settings.AccentColor
import dev.alllexey.itmowidgets.core.settings.ThemeContrast
import dev.alllexey.itmowidgets.core.settings.ThemeMode
import dev.alllexey.itmowidgets.core.settings.ThemeSpec
import dev.alllexey.itmowidgets.core.settings.ThemeStyle
import kotlinx.coroutines.flow.Flow

/**
 * How the app looks: the colour scheme's choices and whether the widgets take them; choices of the device that
 * sign-out keeps.
 */
class AppearancePreferences(dataStore: DataStore<Preferences>) : DataStorePreferences(dataStore) {

    /** Absent or unknown values read as [ThemeSpec]'s defaults, the scheme the app had before the choices. */
    fun observeTheme(): Flow<ThemeSpec> = observe(::themeOf)

    /**
     * Applies [change] to the stored choices and writes every key in one edit, so a screen never sees half of a change
     * and two quick changes never undo each other.
     */
    suspend fun updateTheme(change: (ThemeSpec) -> ThemeSpec) {
        dataStore.edit { preferences ->
            val theme = change(themeOf(preferences))
            preferences[ACCENT_COLOR] = theme.accent.name
            preferences[ACCENT_CUSTOM] = theme.customArgb or OPAQUE
            preferences[THEME_STYLE] = theme.style.name
            preferences[THEME_CONTRAST] = theme.contrast.name
            preferences[DARK_BLACK] = theme.pureBlack
            preferences[THEME_MODE] = theme.mode.name
        }
    }

    /** «Виджеты в цвет темы»; absent reads as on, the app's colours. */
    fun observeWidgetsFollowTheme(): Flow<Boolean> = observe { it[WIDGETS_FOLLOW_THEME] ?: FOLLOW_THEME_DEFAULT }

    suspend fun setWidgetsFollowTheme(enabled: Boolean) {
        write(WIDGETS_FOLLOW_THEME, enabled)
    }

    /**
     * The appearance the widgets draw with, or null while they keep their own colours. Without the app's light or dark
     * mode: the widgets keep the launcher's, so a change of it redraws none.
     */
    fun observeWidgetTheme(): Flow<ThemeSpec?> = observe {
        if (it[WIDGETS_FOLLOW_THEME] ?: FOLLOW_THEME_DEFAULT) themeOf(it).copy(mode = ThemeMode.SYSTEM) else null
    }

    private fun themeOf(preferences: Preferences) = ThemeSpec(
        accent = safeEnumOf(preferences[ACCENT_COLOR], AccentColor.WALLPAPER),
        customArgb = preferences[ACCENT_CUSTOM]?.let { it or OPAQUE } ?: ThemeSpec.DEFAULT_CUSTOM_ARGB,
        style = safeEnumOf(preferences[THEME_STYLE], ThemeStyle.TONAL_SPOT),
        contrast = safeEnumOf(preferences[THEME_CONTRAST], ThemeContrast.STANDARD),
        pureBlack = preferences[DARK_BLACK] ?: false,
        mode = safeEnumOf(preferences[THEME_MODE], ThemeMode.SYSTEM),
    )

    private companion object {
        val ACCENT_COLOR = stringPreferencesKey("app_accent_color")
        val ACCENT_CUSTOM = intPreferencesKey("app_accent_custom")
        val THEME_STYLE = stringPreferencesKey("app_theme_style")
        val THEME_CONTRAST = stringPreferencesKey("app_theme_contrast")
        val DARK_BLACK = booleanPreferencesKey("app_dark_black")
        val THEME_MODE = stringPreferencesKey("app_theme_mode")
        val WIDGETS_FOLLOW_THEME = booleanPreferencesKey("widgets_follow_app_theme")
        const val OPAQUE = 0xFF000000.toInt()

        /** The widgets take the app's colours until the user turns it off; a stored `false` stays off. */
        const val FOLLOW_THEME_DEFAULT = true
    }
}
