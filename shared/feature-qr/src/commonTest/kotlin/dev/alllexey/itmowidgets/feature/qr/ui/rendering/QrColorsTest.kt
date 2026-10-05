package dev.alllexey.itmowidgets.feature.qr.ui.rendering

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class QrColorsTest {

    @Test
    fun withoutDynamicColoursTheCodeIsBlackOnWhite() {
        val colors = QrColors.resolve(dynamic = false, surface = DARK, onSurfaceVariant = LIGHT, onSurface = LIGHTER)

        assertEquals(QrColors(Color.White, Color.Black), colors)
    }

    @Test
    fun aLightSurfaceIsTheBackgroundUnderTheDarkerModuleColour() {
        val colors = QrColors.resolve(dynamic = true, surface = LIGHTER, onSurfaceVariant = MID_DARK, onSurface = DARK)

        assertEquals(QrColors(LIGHTER, DARK), colors)
    }

    @Test
    fun aDarkSurfaceSwapsWithTheVariantSoTheBackgroundStaysLight() {
        val colors = QrColors.resolve(dynamic = true, surface = DARK, onSurfaceVariant = LIGHTER, onSurface = LIGHT)

        assertEquals(QrColors(LIGHTER, DARK), colors)
    }

    @Test
    fun theVariantStaysWhenItIsTheDarkerOne() {
        val colors = QrColors.resolve(dynamic = true, surface = LIGHTER, onSurfaceVariant = DARK, onSurface = MID_DARK)

        assertEquals(QrColors(LIGHTER, DARK), colors)
    }

    @Test
    fun aTranslucentColourFallsBackToBlackOnWhite() {
        val halfDark = DARK.copy(alpha = 0.5f)
        val translucentModule =
            QrColors.resolve(dynamic = true, surface = LIGHTER, onSurfaceVariant = halfDark, onSurface = halfDark)
        val halfLight = LIGHTER.copy(alpha = 0.5f)
        val translucentBackground =
            QrColors.resolve(dynamic = true, surface = halfLight, onSurfaceVariant = DARK, onSurface = DARK)

        assertEquals(QrColors.Static, translucentModule)
        assertEquals(QrColors.Static, translucentBackground)
    }

    private companion object {
        val LIGHTER = Color(0xFFFDF8FD)
        val LIGHT = Color(0xFFE6E0E9)
        val MID_DARK = Color(0xFF49454F)
        val DARK = Color(0xFF1D1B20)
    }
}
