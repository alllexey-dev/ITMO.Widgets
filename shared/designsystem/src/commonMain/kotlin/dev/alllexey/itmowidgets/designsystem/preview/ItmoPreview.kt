package dev.alllexey.itmowidgets.designsystem.preview

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ColorSource
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/**
 * The frame of every `@Preview`: [ItmoTheme] in the night mode and colour seed of [LocalPreviewAppearance], over the
 * scheme's background. Without a seed the scheme is the platform one, as in the app: the wallpaper's on Android 12+,
 * which under Robolectric is its fixed system palette, the one the XML references get from
 * `Theme.Material3.DynamicColors` there. An iOS appearance draws the kit's iOS style on the static scheme, the one iOS
 * has. Font scale and window width come from the host configuration (the screenshot harness sets both), so a preview
 * reads them as a screen does. A [colorSource] replaces the appearance's own, for a preview of an accent colour preset.
 */
@Composable
fun ItmoPreview(colorSource: ColorSource? = null, content: @Composable () -> Unit) {
    val appearance = LocalPreviewAppearance.current
    val ios = appearance.platformStyle == ItmoPlatformStyle.Ios
    val source = colorSource
        ?: appearance.colorSeed?.let(ColorSource::Seed)
        ?: if (ios) ColorSource.Static else ColorSource.Platform
    val dark = appearance.dark || isSystemInDarkTheme()
    ItmoTheme(dark = dark, colorSource = source, platformStyle = appearance.platformStyle) {
        Surface(color = MaterialTheme.colorScheme.background, content = content)
    }
}
