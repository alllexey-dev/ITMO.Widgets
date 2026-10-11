package dev.alllexey.itmowidgets.core.storage

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.alllexey.itmowidgets.core.settings.AccentColor
import dev.alllexey.itmowidgets.core.settings.HexColor
import dev.alllexey.itmowidgets.core.settings.ThemeContrast
import dev.alllexey.itmowidgets.core.settings.ThemeSpec
import dev.alllexey.itmowidgets.core.settings.ThemeStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import dev.alllexey.itmowidgets.core.testing.InMemoryPreferencesDataStore

/** The appearance keys on both platforms: their names and values are stable identifiers of `app_preferences`. */
class AppearancePreferencesTest {

    private val dataStore = InMemoryPreferencesDataStore()
    private val preferences = AppearancePreferences(dataStore)

    @Test
    fun anEmptyFileReadsAsTodaysLook() = runTest {
        val theme = preferences.observeTheme().first()

        assertEquals(ThemeSpec(), theme)
        assertEquals(AccentColor.WALLPAPER, theme.accent)
        assertEquals(0xFF4984E2.toInt(), theme.customArgb)
        assertEquals(ThemeStyle.TONAL_SPOT, theme.style)
        assertEquals(ThemeContrast.STANDARD, theme.contrast)
        assertEquals(false, theme.pureBlack)
    }

    @Test
    fun everyChoiceIsStoredUnderItsKey() = runTest {
        val theme = ThemeSpec(AccentColor.CUSTOM, 0xFF123456.toInt(), ThemeStyle.VIBRANT, ThemeContrast.HIGH, true)

        preferences.updateTheme { theme }

        assertEquals(theme, preferences.observeTheme().first())
        val stored = dataStore.data.first()
        assertEquals("CUSTOM", stored[ACCENT])
        assertEquals(0xFF123456.toInt(), stored[CUSTOM])
        assertEquals("VIBRANT", stored[STYLE])
        assertEquals("HIGH", stored[CONTRAST])
        assertEquals(true, stored[BLACK])
    }

    @Test
    fun theCustomColourRoundTripsOpaque() = runTest {
        preferences.updateTheme { it.copy(accent = AccentColor.CUSTOM, customArgb = 0x00ABCDEF) }

        assertEquals(0xFFABCDEF.toInt(), preferences.observeTheme().first().customArgb)
    }

    @Test
    fun aChangeKeepsTheOtherChoices() = runTest {
        preferences.updateTheme { it.copy(style = ThemeStyle.NEUTRAL) }
        preferences.updateTheme { it.copy(pureBlack = true) }

        assertEquals(ThemeSpec(style = ThemeStyle.NEUTRAL, pureBlack = true), preferences.observeTheme().first())
    }

    @Test
    fun unknownValuesReadAsTheDefaults() = runTest {
        dataStore.edit {
            it[ACCENT] = "GRAPHITE"
            it[STYLE] = "RAINBOW"
            it[CONTRAST] = "LOW"
        }

        assertEquals(ThemeSpec(), preferences.observeTheme().first())
    }

    @Test
    fun theStoredNamesStay() {
        assertEquals(
            listOf("WALLPAPER", "BRAND", "TEAL", "GREEN", "AMBER", "RED", "PINK", "PURPLE", "CUSTOM"),
            AccentColor.entries.map { it.name },
        )
        assertEquals(
            listOf("TONAL_SPOT", "VIBRANT", "EXPRESSIVE", "FIDELITY", "CONTENT", "NEUTRAL", "MONOCHROME"),
            ThemeStyle.entries.map { it.name },
        )
        assertEquals(listOf("STANDARD", "MEDIUM", "HIGH"), ThemeContrast.entries.map { it.name })
    }

    @Test
    fun hexReadsSixDigitsWithOrWithoutTheHash() {
        assertEquals(0xFF4984E2.toInt(), HexColor.parse("#4984e2"))
        assertEquals(0xFF4984E2.toInt(), HexColor.parse(" 4984E2 "))
        assertNull(HexColor.parse("#4984E"))
        assertNull(HexColor.parse("#4984E2FF"))
        assertNull(HexColor.parse("#GG84E2"))
        assertEquals("#4984E2", HexColor.format(0xFF4984E2.toInt()))
        assertEquals("#00000A", HexColor.format(0xFF00000A.toInt()))
    }

    @Test
    fun theWidgetsKeepTheirOwnColoursByDefault() = runTest {
        preferences.updateTheme { it.copy(accent = AccentColor.TEAL) }

        assertFalse(preferences.observeWidgetsFollowTheme().first())
        assertNull(preferences.observeWidgetTheme().first())
    }

    @Test
    fun theWidgetsFollowTheStoredThemeWhenOn() = runTest {
        preferences.updateTheme { it.copy(accent = AccentColor.TEAL, style = ThemeStyle.VIBRANT) }
        preferences.setWidgetsFollowTheme(true)

        assertEquals(ThemeSpec(accent = AccentColor.TEAL, style = ThemeStyle.VIBRANT), preferences.observeWidgetTheme().first())
        assertEquals(true, dataStore.data.first()[WIDGETS])

        preferences.setWidgetsFollowTheme(false)
        assertNull(preferences.observeWidgetTheme().first())
    }

    private companion object {
        val WIDGETS = booleanPreferencesKey("widgets_follow_app_theme")
        val ACCENT = stringPreferencesKey("app_accent_color")
        val CUSTOM = intPreferencesKey("app_accent_custom")
        val STYLE = stringPreferencesKey("app_theme_style")
        val CONTRAST = stringPreferencesKey("app_theme_contrast")
        val BLACK = booleanPreferencesKey("app_dark_black")
    }
}
