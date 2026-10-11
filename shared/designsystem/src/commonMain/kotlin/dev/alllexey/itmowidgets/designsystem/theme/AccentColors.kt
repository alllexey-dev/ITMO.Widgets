package dev.alllexey.itmowidgets.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.alllexey.itmowidgets.core.settings.ThemeSpec
import dev.alllexey.itmowidgets.core.settings.ThemeStyle
import dev.alllexey.itmowidgets.designsystem.components.dialogs.Swatch
import kotlinx.coroutines.flow.Flow

/**
 * The app's appearance setting: the source every [ItmoTheme] draws with unless its caller passes one. The app hosts
 * feed it from the stored choices once at start ([follow]); until the first value it is [ColorSource.Platform], the
 * scheme the app had before the choices existed. A snapshot state, so every open screen recolours at once.
 */
object AppColorSource {

    var current: ColorSource by mutableStateOf(ColorSource.Platform)
        internal set

    /** Follows [themes] until the caller's scope ends. */
    suspend fun follow(themes: Flow<ThemeSpec>) {
        themes.collect { current = ColorSource.Theme(it) }
    }
}

/** The scheme [ItmoTheme] draws for [source] in [dark] mode; a colour picker shows its swatches from it. */
@Composable
fun colorSchemeOf(source: ColorSource, dark: Boolean): ColorScheme {
    val wallpaper = platformWallpaperPalette()
    return remember(source, dark, wallpaper) { source.colorScheme(dark, wallpaper) }
}

internal fun ColorSource.colorScheme(dark: Boolean, wallpaper: WallpaperPalette?): ColorScheme = when (this) {
    ColorSource.Platform -> resolveColorScheme(ThemeSpec(), dark, wallpaper)
    ColorSource.Static -> staticColorScheme(dark)
    is ColorSource.Seed -> seededColorScheme(argb, dark)
    is ColorSource.Theme -> resolveColorScheme(spec, dark, wallpaper)
}

/**
 * The accent of [spec] as a swatch named [label]: the primary of its scheme, checked in its on-primary. Under
 * Монохром every accent is grey, so the swatches show the hues of the default style to stay told apart.
 */
@Composable
fun accentSwatch(spec: ThemeSpec, label: String, dark: Boolean = isSystemInDarkTheme()): Swatch {
    val shown = if (spec.style == ThemeStyle.MONOCHROME) spec.copy(style = ThemeStyle.TONAL_SPOT) else spec
    val scheme = colorSchemeOf(ColorSource.Theme(shown), dark)
    return Swatch(label, scheme.primary, scheme.onPrimary)
}
