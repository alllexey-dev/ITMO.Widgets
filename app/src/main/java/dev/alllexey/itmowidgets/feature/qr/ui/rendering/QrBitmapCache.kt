package dev.alllexey.itmowidgets.feature.qr.ui.rendering

import android.graphics.Bitmap
import androidx.annotation.ColorInt

interface QrBitmapCache {

    fun getBitmap(type: String, meta: String): Bitmap?

    fun saveBitmap(type: String, meta: String, bitmap: Bitmap)

    fun clearCache(type: String?)

    companion object {
        fun bitmapMeta(
            data: String,
            size: Int,
            @ColorInt bgColor: Int,
            @ColorInt fgColor: Int
        ): String {
            return "$data-$size-$bgColor-$fgColor"
        }
    }
}
