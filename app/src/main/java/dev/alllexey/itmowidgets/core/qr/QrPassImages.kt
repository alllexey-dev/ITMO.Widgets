package dev.alllexey.itmowidgets.core.qr

import android.graphics.Bitmap

/**
 * Renders the pass the way the QR screen and widget do, in the colours of the
 * current theme, without touching the network. The spoiler is the user's own
 * picture when they set one, otherwise generated noise.
 */
interface QrPassImages {
    suspend fun qr(hex: String): Bitmap

    suspend fun spoiler(): Bitmap
}
