package dev.alllexey.itmowidgets.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/** iOS has no wallpaper scheme: [ColorSource.Platform] falls back to the static one. */
@Composable
internal actual fun platformColorScheme(dark: Boolean): ColorScheme? = null
