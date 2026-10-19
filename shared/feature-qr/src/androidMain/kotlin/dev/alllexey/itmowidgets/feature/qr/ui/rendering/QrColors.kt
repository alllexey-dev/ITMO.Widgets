package dev.alllexey.itmowidgets.feature.qr.ui.rendering

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * The background and module colours of a QR code. The math is `QrColorResolver.getQrColors(themeContext, dynamic)`
 * of `:app`, which the widget keeps: a scanner needs a light background and dark, opaque modules.
 */
@Immutable
data class QrColors(val background: Color, val foreground: Color) {

    companion object {
        /** Plain black on white: the colours without dynamic colours and the fallback that always scans. */
        val Static = QrColors(background = Color.White, foreground = Color.Black)

        /**
         * [surface] becomes the background and the darker of [onSurfaceVariant] and [onSurface] the modules; a dark
         * [surface] swaps places with [onSurfaceVariant] first. A translucent result falls back to [Static].
         */
        fun resolve(dynamic: Boolean, surface: Color, onSurfaceVariant: Color, onSurface: Color): QrColors {
            if (!dynamic) return Static

            var background = surface
            var foreground = onSurfaceVariant
            if (background.isDark()) {
                background = foreground.also { foreground = background }
            }
            // A tie keeps the first, as `maxOf(a, b, comparator)` does in the resolver.
            if (onSurface.darkness() > foreground.darkness()) foreground = onSurface

            if (!background.isOpaque() || !foreground.isOpaque()) return Static
            return QrColors(background, foreground)
        }

        private fun Color.isDark(): Boolean = darkness() >= DARK_THRESHOLD

        /** On the 0..255 channels, so the comparison matches the resolver's `android.graphics.Color` ints. */
        private fun Color.darkness(): Double {
            val argb = toArgb()
            val red = argb ushr RED_SHIFT and CHANNEL
            val green = argb ushr GREEN_SHIFT and CHANNEL
            val blue = argb and CHANNEL
            return 1 - (RED_WEIGHT * red + GREEN_WEIGHT * green + BLUE_WEIGHT * blue) / CHANNEL
        }

        private fun Color.isOpaque(): Boolean = toArgb() ushr ALPHA_SHIFT == CHANNEL

        private const val DARK_THRESHOLD = 0.5
        private const val RED_WEIGHT = 0.299
        private const val GREEN_WEIGHT = 0.587
        private const val BLUE_WEIGHT = 0.114
        private const val CHANNEL = 0xFF
        private const val ALPHA_SHIFT = 24
        private const val RED_SHIFT = 16
        private const val GREEN_SHIFT = 8
    }
}

/** [QrColors.resolve] over the current [MaterialTheme] scheme. */
@Composable
fun qrColors(dynamic: Boolean): QrColors {
    val scheme = MaterialTheme.colorScheme
    return QrColors.resolve(dynamic, scheme.surface, scheme.onSurfaceVariant, scheme.onSurface)
}
