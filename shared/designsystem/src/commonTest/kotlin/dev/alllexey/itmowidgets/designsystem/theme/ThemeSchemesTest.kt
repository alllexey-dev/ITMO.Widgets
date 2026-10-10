package dev.alllexey.itmowidgets.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.alllexey.itmowidgets.core.settings.AccentColor
import dev.alllexey.itmowidgets.core.settings.ThemeContrast
import dev.alllexey.itmowidgets.core.settings.ThemeSpec
import dev.alllexey.itmowidgets.core.settings.ThemeStyle
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

/**
 * [resolveColorScheme], the one way from the stored appearance to a scheme: the defaults keep today's look, every
 * style and contrast gives a readable scheme, the black background is the dark theme's only, and a theme without a
 * source follows the stored choices.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class ThemeSchemesTest {

    @AfterTest
    fun reset() {
        AppColorSource.current = ColorSource.Platform
    }

    @Test
    fun theDefaultsAreTheBrandSchemeWithoutAWallpaper() {
        for (dark in listOf(false, true)) {
            assertEquals(staticColorScheme(dark).roles(), resolveColorScheme(ThemeSpec(), dark).roles())
            assertEquals(staticColorScheme(dark).roles(), resolveColorScheme(BRAND, dark).roles())
        }
    }

    @Test
    fun theWallpaperAtTheDefaultsIsTheSystemSchemeUnchanged() {
        assertSame(WALLPAPER.light, resolveColorScheme(ThemeSpec(), dark = false, WALLPAPER))
        assertSame(WALLPAPER.dark, resolveColorScheme(ThemeSpec(), dark = true, WALLPAPER))
    }

    @Test
    fun aStyleOrContrastRebuildsTheWallpaperFromItsSeed() {
        val vibrant = resolveColorScheme(ThemeSpec(style = ThemeStyle.VIBRANT), dark = false, WALLPAPER)
        val high = resolveColorScheme(ThemeSpec(contrast = ThemeContrast.HIGH), dark = true, WALLPAPER)

        val seed = WALLPAPER.seedArgb
        assertEquals(themedColorScheme(seed, ThemeStyle.VIBRANT, ThemeContrast.STANDARD, false).roles(), vibrant.roles())
        assertEquals(themedColorScheme(seed, ThemeStyle.TONAL_SPOT, ThemeContrast.HIGH, true).roles(), high.roles())
    }

    @Test
    fun presetsKeepTheirOwnHue() {
        val presets = AccentColor.entries - AccentColor.WALLPAPER - AccentColor.BRAND - AccentColor.CUSTOM
        assertEquals(presets.toSet(), PresetSeeds.keys)
        for (dark in listOf(false, true)) {
            val primaries = presets.map { resolveColorScheme(ThemeSpec(accent = it), dark).primary }
            assertEquals(primaries.size, primaries.toSet().size)
            primaries.forEach { assertNotEquals(staticColorScheme(dark).primary, it) }
        }
    }

    @Test
    fun theCustomColourIsTheSeed() {
        val custom = ThemeSpec(accent = AccentColor.CUSTOM, customArgb = GREEN)

        assertEquals(
            themedColorScheme(GREEN, ThemeStyle.TONAL_SPOT, ThemeContrast.STANDARD, dark = false).roles(),
            resolveColorScheme(custom, dark = false).roles(),
        )
        assertEquals(
            resolveColorScheme(BRAND, dark = false).roles(),
            resolveColorScheme(custom.copy(accent = AccentColor.BRAND), dark = false).roles(),
        )
    }

    @Test
    fun everyStyleAndContrastIsReadable() {
        for (style in ThemeStyle.entries) for (level in ThemeContrast.entries) for (dark in listOf(false, true)) {
            val scheme = resolveColorScheme(ThemeSpec(AccentColor.AMBER, style = style, contrast = level), dark)
            val minimum = if (level == ThemeContrast.HIGH) 7.0 else 4.5
            val case = "$style $level dark=$dark"
            assertTrue(contrast(scheme.onSurface, scheme.surface) >= minimum, case)
            assertTrue(contrast(scheme.onPrimary, scheme.primary) >= 3.0, case)
            assertTrue(contrast(scheme.onPrimaryContainer, scheme.primaryContainer) >= 3.0, case)
        }
    }

    @Test
    fun theStylesLookDifferent() {
        val looks = ThemeStyle.entries.map { style ->
            resolveColorScheme(ThemeSpec(AccentColor.TEAL, style = style), dark = false).roles()
        }
        assertEquals(looks.size, looks.toSet().size)
    }

    @Test
    fun aHigherContrastSetsTheTextFurtherApart() {
        for (dark in listOf(false, true)) {
            val ratios = ThemeContrast.entries.map { level ->
                val scheme = resolveColorScheme(ThemeSpec(contrast = level), dark)
                contrast(scheme.onSurfaceVariant, scheme.surface)
            }
            assertEquals(ratios.sorted(), ratios)
            assertTrue(ratios.first() < ratios.last())
        }
    }

    @Test
    fun theBlackBackgroundIsTheDarkThemesOnlyAndKeepsTheContainers() {
        val black = ThemeSpec(pureBlack = true)

        val dark = resolveColorScheme(black, dark = true)
        val darkWallpaper = resolveColorScheme(black, dark = true, WALLPAPER)
        val light = resolveColorScheme(black, dark = false)

        for (scheme in listOf(dark, darkWallpaper)) {
            assertEquals(Color.Black, scheme.background)
            assertEquals(Color.Black, scheme.surface)
            assertNotEquals(Color.Black, scheme.surfaceContainer)
            assertNotEquals(Color.Black, scheme.surfaceContainerLow)
        }
        assertEquals(resolveColorScheme(ThemeSpec(), dark = false).roles(), light.roles())
    }

    @Test
    fun theSourceFollowsTheStoredChoices() = runTest {
        assertEquals(ColorSource.Platform, AppColorSource.current)

        AppColorSource.follow(flowOf(BRAND, TEAL))

        assertEquals(ColorSource.Theme(TEAL), AppColorSource.current)
    }

    @Test
    fun aThemeWithoutASourceRecoloursWithTheChoice() = runComposeUiTest {
        var primary = Color.Unspecified
        AppColorSource.current = ColorSource.Theme(TEAL)
        setContent { ItmoTheme(dark = false) { primary = ItmoTheme.colorScheme.primary } }
        waitForIdle()
        assertEquals(resolveColorScheme(TEAL, dark = false).primary, primary)

        AppColorSource.current = ColorSource.Theme(BRAND)
        waitForIdle()
        assertEquals(staticColorScheme(dark = false).primary, primary)
    }

    /** Some roles as a value: `ColorScheme` has no equality of its own. */
    private fun ColorScheme.roles() = listOf(
        primary, onPrimary, primaryContainer, secondaryContainer, tertiary, surface, surfaceContainer, onSurfaceVariant,
    )

    /** The WCAG contrast ratio of two opaque colours. */
    private fun contrast(a: Color, b: Color): Double {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    private companion object {
        val BRAND = ThemeSpec(accent = AccentColor.BRAND)
        val TEAL = ThemeSpec(accent = AccentColor.TEAL)
        const val GREEN = 0xFF2E7D32.toInt()

        /** A stand-in for the system's schemes: the baseline ones, with a purple seed. */
        val WALLPAPER = WallpaperPalette(
            light = baselineColorScheme(dark = false),
            dark = baselineColorScheme(dark = true),
            seedArgb = 0xFF6750A4.toInt(),
        )
    }
}
