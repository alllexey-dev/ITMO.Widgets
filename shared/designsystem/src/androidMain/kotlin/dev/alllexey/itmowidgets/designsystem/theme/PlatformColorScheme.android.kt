package dev.alllexey.itmowidgets.designsystem.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/**
 * The wallpaper's colours, re-read after a wallpaper or theme change recreates the configuration (the configuration
 * is the key).
 */
@Composable
internal actual fun platformWallpaperPalette(): WallpaperPalette? {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration) { wallpaperPalette(context) }
}

/**
 * The wallpaper's colours on API 31+, null below: both schemes from the same `android.R.color.system_*` the MDC
 * DynamicColors theme reads, and `system_accent1_500` as the seed a style or contrast rebuilds from. Outside
 * composition (widgets) pass it to [resolveColorScheme].
 */
fun wallpaperPalette(context: Context): WallpaperPalette? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val sdk = Build.VERSION.SDK_INT
    return WallpaperPalette(
        light = dynamicLightColorScheme(context).withViewThemeRoles(dark = false, sdk),
        dark = dynamicDarkColorScheme(context).withViewThemeRoles(dark = true, sdk),
        seedArgb = context.getColor(android.R.color.system_accent1_500),
    )
}

/**
 * MDC 1.13's `Theme.Material3.DynamicColors` leaves `colorOutlineVariant` and the error roles at the baseline values,
 * and on API 35 its baseline light `colorOnErrorContainer` is error tone 30. The View screens and widgets show exactly
 * that, so the Compose scheme takes the same values and a Compose screen next to a View one shows no seam.
 */
internal fun ColorScheme.withViewThemeRoles(dark: Boolean, sdk: Int): ColorScheme {
    val baseline = baselineColorScheme(dark)
    val onErrorContainer = if (!dark && sdk >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        ERROR_30
    } else {
        baseline.onErrorContainer
    }
    return copy(
        outlineVariant = baseline.outlineVariant,
        error = baseline.error,
        onError = baseline.onError,
        errorContainer = baseline.errorContainer,
        onErrorContainer = onErrorContainer,
    )
}

private val ERROR_30 = Color(0xFF8C1D18)
