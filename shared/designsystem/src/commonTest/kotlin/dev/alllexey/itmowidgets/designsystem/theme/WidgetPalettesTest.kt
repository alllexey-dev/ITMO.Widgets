package dev.alllexey.itmowidgets.designsystem.theme

import androidx.compose.ui.graphics.toArgb
import dev.alllexey.itmowidgets.core.settings.AccentColor
import dev.alllexey.itmowidgets.core.settings.ThemeContrast
import dev.alllexey.itmowidgets.core.settings.ThemeSpec
import dev.alllexey.itmowidgets.core.settings.ThemeStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The widgets' roles are the roles of the scheme the app draws for the same appearance. */
class WidgetPalettesTest {

    private val teal = ThemeSpec(accent = AccentColor.TEAL)

    @Test
    fun anAppearanceGivesItsSchemeRolesInBothModes() {
        val spec = ThemeSpec(accent = AccentColor.CUSTOM, customArgb = 0xFF6A1B9A.toInt(), style = ThemeStyle.VIBRANT,
            contrast = ThemeContrast.HIGH)
        val palette = spec.widgetPalette()
        for (dark in listOf(false, true)) {
            val scheme = resolveColorScheme(spec, dark)
            val roles = palette.roles(dark)
            assertEquals(scheme.surface.toArgb() and RGB, roles.surface)
            assertEquals(scheme.surfaceContainer.toArgb() and RGB, roles.surfaceContainer)
            assertEquals(scheme.onSurface.toArgb() and RGB, roles.onSurface)
            assertEquals(scheme.onSurfaceVariant.toArgb() and RGB, roles.onSurfaceVariant)
            assertEquals(scheme.outlineVariant.toArgb() and RGB, roles.outlineVariant)
            assertEquals(scheme.primary.toArgb() and RGB, roles.primary)
        }
        assertNotEquals(palette.light, palette.dark)
        assertNotEquals(teal.widgetPalette(), palette)
    }

    @Test
    fun theBlackBackgroundStaysOutOfTheWidgets() {
        assertEquals(teal.widgetPalette(), teal.copy(pureBlack = true).widgetPalette())
        assertNotEquals(0, teal.widgetPalette().dark.surface)
    }

    @Test
    fun withoutWallpaperColoursTheDefaultIsTheBrandPalette() {
        assertEquals(ThemeSpec(accent = AccentColor.BRAND).widgetPalette(), ThemeSpec().widgetPalette())
    }

    /** `WidgetPaletteFixtures.teal` of the iOS widget snapshot tests is this palette; a change re-records them. */
    @Test
    fun theTealPaletteIsTheOneTheIosWidgetTestsDrawWith() {
        val palette = teal.widgetPalette()
        assertEquals(
            listOf(0xF4FBF8, 0xE9EFED, 0x161D1C, 0x3F4947, 0xBEC9C6, 0x006A60),
            palette.light.let { listOf(it.surface, it.surfaceContainer, it.onSurface, it.onSurfaceVariant, it.outlineVariant, it.primary) },
        )
        assertEquals(
            listOf(0x0E1513, 0x1A2120, 0xDDE4E1, 0xBEC9C6, 0x3F4947, 0x82D5C8),
            palette.dark.let { listOf(it.surface, it.surfaceContainer, it.onSurface, it.onSurfaceVariant, it.outlineVariant, it.primary) },
        )
    }

    @Test
    fun theRolesAreRgbWithoutAlpha() {
        AccentColor.entries.map { ThemeSpec(accent = it).widgetPalette() }.flatMap { listOf(it.light, it.dark) }
            .forEach { roles ->
                listOf(
                    roles.surface, roles.surfaceContainer, roles.onSurface, roles.onSurfaceVariant,
                    roles.outlineVariant, roles.primary,
                ).forEach { assertTrue(it in 0..RGB, "$roles") }
            }
    }

    private companion object {
        const val RGB = 0xFFFFFF
    }
}
