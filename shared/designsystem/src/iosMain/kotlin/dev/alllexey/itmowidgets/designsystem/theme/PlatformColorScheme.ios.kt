package dev.alllexey.itmowidgets.designsystem.theme

import androidx.compose.runtime.Composable

/** iOS has no wallpaper colours: the wallpaper accent reads as the brand seed. */
@Composable
internal actual fun platformWallpaperPalette(): WallpaperPalette? = null
