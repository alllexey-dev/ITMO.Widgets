package dev.alllexey.itmowidgets.data.local

import android.graphics.Bitmap

interface QrBitmapCache {

    fun saveNoiseBitmap(bitmap: Bitmap, sidePixels: Int, bgColor: Int, fgColor: Int)

    fun loadNoiseBitmap(sidePixels: Int, bgColor: Int, fgColor: Int): Bitmap?

    fun clearCache()
}