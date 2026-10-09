package dev.alllexey.itmowidgets.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.dynamicColorScheme

/** The launcher, shortcut and `lesson_type_lecture` blue: the seed of [staticColorScheme]. */
internal const val BRAND_SEED: Int = 0xFF4984E2.toInt()

/**
 * The static scheme of API 26-30 and iOS (owner, item 14 Q1 (b)): the TonalSpot variant of the 2021 spec from
 * [BRAND_SEED], Material's default way to build a scheme from a brand colour. Android 12+ keeps the wallpaper's.
 */
internal fun staticColorScheme(dark: Boolean): ColorScheme = if (dark) BrandDark else BrandLight

/**
 * The M3 baseline (`#6750A4` family), pinned to MDC 1.13's `Theme.Material3.Light`/`Dark` values: what the View
 * screens and widgets still draw on API 26-30, and the roles MDC's DynamicColors theme leaves at baseline
 * ([withViewThemeRoles]).
 */
internal fun baselineColorScheme(dark: Boolean): ColorScheme = if (dark) BaselineDark else BaselineLight

/**
 * MDC's `DynamicColorsOptions.setContentBasedSource(seed)`: the Content variant of the 2021 spec. TonalSpot or the
 * 2025 spec give other secondary and surface roles (SP-06).
 */
internal fun seededColorScheme(argb: Int, dark: Boolean): ColorScheme = dynamicColorScheme(
    seedColor = Color(argb),
    isDark = dark,
    style = PaletteStyle.Content,
    specVersion = ColorSpec.SpecVersion.SPEC_2021,
)

private val BrandLight = brandColorScheme(dark = false)

private val BrandDark = brandColorScheme(dark = true)

private fun brandColorScheme(dark: Boolean): ColorScheme = dynamicColorScheme(
    seedColor = Color(BRAND_SEED),
    isDark = dark,
    style = PaletteStyle.TonalSpot,
    specVersion = ColorSpec.SpecVersion.SPEC_2021,
)

private val BaselineLight = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    inversePrimary = Color(0xFFD0BCFF),
    secondary = Color(0xFF625B71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    tertiary = Color(0xFF7D5260),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF31111D),
    background = Color(0xFFFEF7FF),
    onBackground = Color(0xFF1D1B20),
    surface = Color(0xFFFEF7FF),
    onSurface = Color(0xFF1D1B20),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    surfaceTint = Color(0xFF6750A4),
    inverseSurface = Color(0xFF322F35),
    inverseOnSurface = Color(0xFFF5EFF7),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFEF7FF),
    surfaceContainer = Color(0xFFF3EDF7),
    surfaceContainerHigh = Color(0xFFECE6F0),
    surfaceContainerHighest = Color(0xFFE6E0E9),
    surfaceContainerLow = Color(0xFFF7F2FA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFDED8E1),
    primaryFixed = Color(0xFFEADDFF),
    primaryFixedDim = Color(0xFFD0BCFF),
    onPrimaryFixed = Color(0xFF21005D),
    onPrimaryFixedVariant = Color(0xFF4F378B),
    secondaryFixed = Color(0xFFE8DEF8),
    secondaryFixedDim = Color(0xFFCCC2DC),
    onSecondaryFixed = Color(0xFF1D192B),
    onSecondaryFixedVariant = Color(0xFF4A4458),
    tertiaryFixed = Color(0xFFFFD8E4),
    tertiaryFixedDim = Color(0xFFEFB8C8),
    onTertiaryFixed = Color(0xFF31111D),
    onTertiaryFixedVariant = Color(0xFF633B48),
)

private val BaselineDark = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    inversePrimary = Color(0xFF6750A4),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE6E0E9),
    surface = Color(0xFF141218),
    onSurface = Color(0xFFE6E0E9),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    surfaceTint = Color(0xFFD0BCFF),
    inverseSurface = Color(0xFFE6E0E9),
    inverseOnSurface = Color(0xFF322F35),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF3B383E),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF2B2930),
    surfaceContainerHighest = Color(0xFF36343B),
    surfaceContainerLow = Color(0xFF1D1B20),
    surfaceContainerLowest = Color(0xFF0F0D13),
    surfaceDim = Color(0xFF141218),
    primaryFixed = Color(0xFFEADDFF),
    primaryFixedDim = Color(0xFFD0BCFF),
    onPrimaryFixed = Color(0xFF21005D),
    onPrimaryFixedVariant = Color(0xFF4F378B),
    secondaryFixed = Color(0xFFE8DEF8),
    secondaryFixedDim = Color(0xFFCCC2DC),
    onSecondaryFixed = Color(0xFF1D192B),
    onSecondaryFixedVariant = Color(0xFF4A4458),
    tertiaryFixed = Color(0xFFFFD8E4),
    tertiaryFixedDim = Color(0xFFEFB8C8),
    onTertiaryFixed = Color(0xFF31111D),
    onTertiaryFixedVariant = Color(0xFF633B48),
)
