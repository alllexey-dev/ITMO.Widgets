package dev.alllexey.itmowidgets.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import dev.alllexey.itmowidgets.designsystem.tokens.ItmoEmphasizedTypography
import dev.alllexey.itmowidgets.designsystem.tokens.ItmoMaterialTypography
import dev.alllexey.itmowidgets.designsystem.tokens.ItmoMotion
import dev.alllexey.itmowidgets.designsystem.tokens.ItmoShapes
import dev.alllexey.itmowidgets.designsystem.tokens.ItmoSpacing
import dev.alllexey.itmowidgets.designsystem.tokens.LocalItmoEmphasizedTypography
import dev.alllexey.itmowidgets.designsystem.tokens.LocalItmoMotion
import dev.alllexey.itmowidgets.designsystem.tokens.LocalItmoShapes
import dev.alllexey.itmowidgets.designsystem.tokens.LocalItmoSpacing
import dev.alllexey.itmowidgets.designsystem.tokens.emphasizedTypographyOf
import dev.alllexey.itmowidgets.designsystem.tokens.toMaterialShapes

/**
 * The app's Material theme for Compose: the scheme from [colorSource] in [dark] or light mode, the app's own colours,
 * shapes, spacing, type and motion. Until the M3E token change the values equal what the View screens get from
 * `Theme.Material3.DynamicColors.DayNight` and `res/values/dimens.xml`.
 */
@Composable
fun ItmoTheme(
    dark: Boolean = isSystemInDarkTheme(),
    colorSource: ColorSource = ColorSource.Platform,
    content: @Composable () -> Unit,
) {
    val platform = if (colorSource == ColorSource.Platform) platformColorScheme(dark) else null
    val scheme = platform ?: remember(colorSource, dark) { generatedColorScheme(colorSource, dark) }
    val extended = remember(scheme, dark) { ExtendedColorTokens.of(dark).resolve(scheme) }
    CompositionLocalProvider(
        LocalItmoExtendedColors provides extended,
        LocalItmoShapes provides ItmoShapes.Default,
        LocalItmoSpacing provides ItmoSpacing.Default,
        LocalItmoEmphasizedTypography provides EmphasizedTypography,
        LocalItmoMotion provides ItmoMotion.Default,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            motionScheme = ItmoMotion.Default.scheme,
            shapes = MaterialShapes,
            typography = ItmoMaterialTypography,
            content = content,
        )
    }
}

/** The current theme's tokens; components read them only through here. */
object ItmoTheme {
    val colorScheme: ColorScheme
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme

    /** The app's own colours under the current scheme; outside an [ItmoTheme] the static light ones. */
    val extendedColors: ItmoExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalItmoExtendedColors.current

    /** The M3 type roles. */
    val typography: Typography
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography

    /** The emphasized twin of every M3 type role. */
    val emphasizedTypography: ItmoEmphasizedTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalItmoEmphasizedTypography.current

    /** The corner scale and the card family. */
    val shapes: ItmoShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalItmoShapes.current

    val spacing: ItmoSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalItmoSpacing.current

    val motion: ItmoMotion
        @Composable
        @ReadOnlyComposable
        get() = LocalItmoMotion.current
}

private val MaterialShapes = ItmoShapes.Default.toMaterialShapes()
private val EmphasizedTypography = emphasizedTypographyOf(ItmoMaterialTypography)

private fun generatedColorScheme(colorSource: ColorSource, dark: Boolean): ColorScheme = when (colorSource) {
    is ColorSource.Seed -> seededColorScheme(colorSource.argb, dark)
    ColorSource.Platform, ColorSource.Static -> staticColorScheme(dark)
}

/** The platform's dynamic scheme, or null where there is none (Android below 12, iOS). */
@Composable
internal expect fun platformColorScheme(dark: Boolean): ColorScheme?
