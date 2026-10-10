package dev.alllexey.itmowidgets.core.storage

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.alllexey.itmowidgets.core.settings.AccentColor
import dev.alllexey.itmowidgets.core.testing.InMemoryPreferencesDataStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/** `app_accent_color` on both platforms: its name and values are stable identifiers of `app_preferences`. */
class AppearancePreferencesTest {

    private val dataStore = InMemoryPreferencesDataStore()
    private val preferences = AppearancePreferences(dataStore)

    @Test
    fun anEmptyFileReadsAsTheWallpaper() = runTest {
        assertEquals(AccentColor.WALLPAPER, preferences.observeAccentColor().first())
    }

    @Test
    fun theChoiceIsStoredByNameUnderItsKey() = runTest {
        preferences.setAccentColor(AccentColor.PURPLE)

        assertEquals(AccentColor.PURPLE, preferences.observeAccentColor().first())
        assertEquals("PURPLE", dataStore.data.first()[KEY])
    }

    @Test
    fun anUnknownValueReadsAsTheWallpaper() = runTest {
        dataStore.edit { it[KEY] = "GRAPHITE" }

        assertEquals(AccentColor.WALLPAPER, preferences.observeAccentColor().first())
    }

    @Test
    fun theStoredNamesStay() {
        assertEquals(
            listOf("WALLPAPER", "BRAND", "TEAL", "GREEN", "AMBER", "RED", "PINK", "PURPLE"),
            AccentColor.entries.map { it.name },
        )
    }

    private companion object {
        val KEY = stringPreferencesKey("app_accent_color")
    }
}
