package dev.alllexey.itmowidgets.feature.qr.ui.widget

import android.graphics.Bitmap
import androidx.core.graphics.scale
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrToolkit
import javax.inject.Inject

/**
 * Bitmaps for the home-screen widget.
 *
 * They are rendered smaller than the in-app code on purpose: `RemoteViews` travel to
 * the launcher over Binder, whose per-process transaction budget is about one
 * megabyte, and the full-size code alone is close to it. The config stays
 * `ARGB_8888` because the renderer draws a rounded background — `RGB_565` has no
 * alpha and would paint the corners instead of leaving them transparent.
 */
class QrWidgetImages @Inject constructor(
    private val toolkit: QrToolkit
) {

    suspend fun qrCode(qrHex: String): Bitmap = toolkit.generateQrBitmap(qrHex).forWidget()

    suspend fun spoiler(): Bitmap = toolkit.generateSpoilerBitmap().forWidget()

    suspend fun placeholder(): Bitmap = toolkit.generateEmptyQrBitmap().forWidget()

    /** Readable against [placeholder]; the widget has no theme of its own. */
    suspend fun messageColor(): Int = toolkit.colorResolver.getQrColors().second

    private fun Bitmap.forWidget(): Bitmap {
        if (width == WIDGET_SIZE_PX && height == WIDGET_SIZE_PX) return this
        return scale(WIDGET_SIZE_PX, WIDGET_SIZE_PX)
    }

    companion object {
        const val WIDGET_SIZE_PX = 256
    }
}
