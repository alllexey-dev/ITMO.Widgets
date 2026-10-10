package dev.alllexey.itmowidgets.designsystem.preview

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ColorSource
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.tokens.ProvideM3eCandidate

/**
 * The frame of every `@Preview`: [ItmoTheme] in the night mode and colour seed of [LocalPreviewAppearance], over the
 * scheme's background. Without a seed the scheme is the platform one, as in the app: the wallpaper's on Android 12+,
 * which under Robolectric is its fixed system palette, the one the XML references get from
 * `Theme.Material3.DynamicColors` there. An iOS appearance draws the kit's iOS style on the static scheme, the one iOS
 * has. Font scale and window width come from the host configuration (the screenshot harness sets both), so a preview
 * reads them as a screen does. An M3E candidate the harness selects (`LocalM3eCandidate`, M3-02a) replaces the
 * theme's values in the Material style only (the M3E look is Material's); none is selected outside the owner's
 * contact sheets. A [colorSource] replaces the appearance's own, for a preview of an accent colour preset.
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
        if (ios) {
            Surface(color = MaterialTheme.colorScheme.background, content = content)
        } else {
            ProvideM3eCandidate(dark) {
                Surface(color = MaterialTheme.colorScheme.background, content = content)
            }
        }
    }
}
