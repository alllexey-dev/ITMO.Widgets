package dev.alllexey.itmowidgets.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember

/**
 * The app's Material theme for Compose: the scheme from [colorSource] in [dark] or light mode, and the app's own
 * colours in [ItmoTheme.extendedColors]. Until the M3E token change the values equal what the View screens get from
 * `Theme.Material3.DynamicColors.DayNight`.
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
    CompositionLocalProvider(LocalItmoExtendedColors provides extended) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

object ItmoTheme {
    /** The app's own colours under the current scheme; outside an [ItmoTheme] the static light ones. */
    val extendedColors: ItmoExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalItmoExtendedColors.current
}

private fun generatedColorScheme(colorSource: ColorSource, dark: Boolean): ColorScheme = when (colorSource) {
    is ColorSource.Seed -> seededColorScheme(colorSource.argb, dark)
    ColorSource.Platform, ColorSource.Static -> staticColorScheme(dark)
}

/** The platform's dynamic scheme, or null where there is none (Android below 12, iOS). */
@Composable
internal expect fun platformColorScheme(dark: Boolean): ColorScheme?
