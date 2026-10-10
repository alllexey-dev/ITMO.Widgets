package dev.alllexey.itmowidgets.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.alllexey.itmowidgets.core.settings.AccentColor
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

/** The accent colour setting: which scheme each choice gives, and that a theme without a source follows the choice. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class AccentColorsTest {

    @AfterTest
    fun reset() {
        AppColorSource.current = ColorSource.Platform
    }

    @Test
    fun theDefaultIsThePlatformSchemeAndTheBrandIsTheStaticOne() {
        assertEquals(ColorSource.Platform, AccentColor.WALLPAPER.colorSource)
        assertEquals(ColorSource.Static, AccentColor.BRAND.colorSource)
    }

    @Test
    fun presetsUseTheBrandRecipeWithTheirOwnHue() {
        val presets = AccentColor.entries - AccentColor.WALLPAPER - AccentColor.BRAND
        val seeds = presets.map { (it.colorSource as ColorSource.Accent).argb }
        assertEquals(seeds.size, seeds.toSet().size)
        for (dark in listOf(false, true)) {
            assertEquals(staticColorScheme(dark).roles(), accentColorScheme(BRAND_SEED, dark).roles())
            val primaries = presets.map { generatedColorScheme(it.colorSource, dark).primary }
            assertEquals(primaries.size, primaries.toSet().size)
            primaries.forEach { assertNotEquals(staticColorScheme(dark).primary, it) }
        }
    }

    @Test
    fun theSourceFollowsTheStoredChoice() = runTest {
        assertEquals(ColorSource.Platform, AppColorSource.current)

        AppColorSource.follow(flowOf(AccentColor.BRAND, AccentColor.TEAL))

        assertEquals(AccentColor.TEAL.colorSource, AppColorSource.current)
    }

    @Test
    fun aThemeWithoutASourceRecoloursWithTheChoice() = runComposeUiTest {
        var primary = staticColorScheme(dark = false).primary
        AppColorSource.current = AccentColor.TEAL.colorSource
        setContent { ItmoTheme(dark = false) { primary = ItmoTheme.colorScheme.primary } }
        waitForIdle()
        assertEquals(accentColorScheme(TEAL, dark = false).primary, primary)

        AppColorSource.current = AccentColor.BRAND.colorSource
        waitForIdle()
        assertEquals(staticColorScheme(dark = false).primary, primary)
    }

    /** Some roles as a value: `ColorScheme` has no equality of its own. */
    private fun ColorScheme.roles() = listOf(primary, onPrimary, secondaryContainer, tertiary, surface, surfaceContainer)

    private companion object {
        val TEAL = (AccentColor.TEAL.colorSource as ColorSource.Accent).argb
    }
}
