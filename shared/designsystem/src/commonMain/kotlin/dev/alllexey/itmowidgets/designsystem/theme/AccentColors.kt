package dev.alllexey.itmowidgets.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.alllexey.itmowidgets.core.settings.AccentColor
import dev.alllexey.itmowidgets.designsystem.components.dialogs.Swatch
import kotlinx.coroutines.flow.Flow

/**
 * Where [accent] takes its scheme from. The presets are hues around the brand blue, built with the brand scheme's
 * recipe ([accentColorScheme]), so each keeps its contrast; a grey seed would come out tinted there, so none is
 * offered.
 */
val AccentColor.colorSource: ColorSource
    get() = when (this) {
        AccentColor.WALLPAPER -> ColorSource.Platform
        AccentColor.BRAND -> ColorSource.Static
        AccentColor.TEAL -> ColorSource.Accent(0xFF009688.toInt())
        AccentColor.GREEN -> ColorSource.Accent(0xFF43A047.toInt())
        AccentColor.AMBER -> ColorSource.Accent(0xFFFFA000.toInt())
        AccentColor.RED -> ColorSource.Accent(0xFFE53935.toInt())
        AccentColor.PINK -> ColorSource.Accent(0xFFD81B60.toInt())
        AccentColor.PURPLE -> ColorSource.Accent(0xFF8E24AA.toInt())
    }

/**
 * The app's accent colour setting: the source every [ItmoTheme] draws with unless its caller passes one. The app hosts
 * feed it from the stored choice once at start ([follow]); until the first value it is [ColorSource.Platform], the
 * scheme the app had before the choice existed. A snapshot state, so every open screen recolours at once.
 */
object AppColorSource {

    var current: ColorSource by mutableStateOf(ColorSource.Platform)
        internal set

    /** Follows [accents] until the caller's scope ends. */
    suspend fun follow(accents: Flow<AccentColor>) {
        accents.collect { current = it.colorSource }
    }
}

/** The scheme [ItmoTheme] draws for [source] in [dark] mode; a colour picker shows its swatches from it. */
@Composable
fun colorSchemeOf(source: ColorSource, dark: Boolean): ColorScheme {
    val platform = if (source == ColorSource.Platform) platformColorScheme(dark) else null
    return platform ?: remember(source, dark) { generatedColorScheme(source, dark) }
}

/** [accent] as a swatch named [label]: the primary of its scheme, checked in its on-primary. */
@Composable
fun accentSwatch(accent: AccentColor, label: String, dark: Boolean = isSystemInDarkTheme()): Swatch {
    val scheme = colorSchemeOf(accent.colorSource, dark)
    return Swatch(label, scheme.primary, scheme.onPrimary)
}
