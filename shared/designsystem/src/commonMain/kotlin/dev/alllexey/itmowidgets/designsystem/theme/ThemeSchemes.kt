package dev.alllexey.itmowidgets.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.dynamicColorScheme
import dev.alllexey.itmowidgets.core.settings.AccentColor
import dev.alllexey.itmowidgets.core.settings.ThemeContrast
import dev.alllexey.itmowidgets.core.settings.ThemeSpec
import dev.alllexey.itmowidgets.core.settings.ThemeStyle

/**
 * The wallpaper's colours where the platform has them (Android 12+): the system's own scheme of each mode, as the
 * View theme draws it, and the seed of its palette (`system_accent1_500`, the wallpaper hue the user picked in the
 * system's style settings).
 */
class WallpaperPalette(val light: ColorScheme, val dark: ColorScheme, val seedArgb: Int)

/**
 * The one way from the stored appearance ([ThemeSpec]) to the scheme in [dark] or light mode: every [ItmoTheme]
 * without an explicit source and the widgets draw with it. [wallpaper] is the platform's, null where there is none
 * (Android below 12, iOS, a test).
 *
 * [AccentColor.WALLPAPER] at the default style and contrast is the system's scheme unchanged, the app's look before
 * the choices. Any other style or contrast rebuilds the scheme from the wallpaper's seed with MaterialKolor, because
 * the system's scheme has neither; without a wallpaper it falls back to the brand seed. Every other accent is built
 * from its seed with the picked style and contrast (2021 spec, which has every style). [ThemeSpec.pureBlack] turns the
 * dark background and surfaces black and keeps the containers, so cards stay apart from the page.
 */
fun resolveColorScheme(spec: ThemeSpec, dark: Boolean, wallpaper: WallpaperPalette? = null): ColorScheme {
    val system = wallpaper?.takeIf { spec.followsSystemScheme }?.let { if (dark) it.dark else it.light }
    val scheme = system ?: themedColorScheme(spec.seedArgb(wallpaper), spec.style, spec.contrast, dark)
    return if (dark && spec.pureBlack) scheme.withPureBlack() else scheme
}

/** The seed [spec] builds from: the preset's, the user's own, the wallpaper's or the brand blue. */
internal fun ThemeSpec.seedArgb(wallpaper: WallpaperPalette?): Int = when (accent) {
    AccentColor.WALLPAPER -> wallpaper?.seedArgb ?: BRAND_SEED
    AccentColor.BRAND -> BRAND_SEED
    AccentColor.CUSTOM -> customArgb
    else -> PresetSeeds.getValue(accent)
}

/**
 * The preset seeds: hues around the brand blue. TonalSpot keeps only the hue, so a grey seed would come out tinted and
 * none is offered; Монохром is the grey look.
 */
internal val PresetSeeds: Map<AccentColor, Int> = mapOf(
    AccentColor.TEAL to 0xFF009688.toInt(),
    AccentColor.GREEN to 0xFF43A047.toInt(),
    AccentColor.AMBER to 0xFFFFA000.toInt(),
    AccentColor.RED to 0xFFE53935.toInt(),
    AccentColor.PINK to 0xFFD81B60.toInt(),
    AccentColor.PURPLE to 0xFF8E24AA.toInt(),
)

private val ThemeSpec.followsSystemScheme: Boolean
    get() = accent == AccentColor.WALLPAPER && style == ThemeStyle.TONAL_SPOT && contrast == ThemeContrast.STANDARD

/** MaterialKolor's scheme from [argb]; TonalSpot at standard contrast is the brand scheme's recipe. */
internal fun themedColorScheme(argb: Int, style: ThemeStyle, contrast: ThemeContrast, dark: Boolean): ColorScheme =
    dynamicColorScheme(
        seedColor = Color(argb),
        isDark = dark,
        style = style.paletteStyle,
        contrastLevel = contrast.level,
        specVersion = ColorSpec.SpecVersion.SPEC_2021,
    )

/** Like MaterialKolor's `isAmoled`, plus the dim and lowest surfaces, which sit under the containers. */
internal fun ColorScheme.withPureBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
)

private val ThemeStyle.paletteStyle: PaletteStyle
    get() = when (this) {
        ThemeStyle.TONAL_SPOT -> PaletteStyle.TonalSpot
        ThemeStyle.VIBRANT -> PaletteStyle.Vibrant
        ThemeStyle.EXPRESSIVE -> PaletteStyle.Expressive
        ThemeStyle.FIDELITY -> PaletteStyle.Fidelity
        ThemeStyle.CONTENT -> PaletteStyle.Content
        ThemeStyle.NEUTRAL -> PaletteStyle.Neutral
        ThemeStyle.MONOCHROME -> PaletteStyle.Monochrome
    }

private val ThemeContrast.level: Double
    get() = when (this) {
        ThemeContrast.STANDARD -> 0.0
        ThemeContrast.MEDIUM -> 0.5
        ThemeContrast.HIGH -> 1.0
    }
