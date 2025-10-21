package dev.alllexey.itmowidgets.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File
import java.io.FileOutputStream

class QrBitmapCacheImpl(context: Context) : QrBitmapCache {

    private val cacheDir: File by lazy {
        File(context.filesDir, "widget_cache").apply { mkdirs() }
    }

    private fun generateFileName(sidePixels: Int, bgColor: Int, fgColor: Int): String {
        return "noise_${sidePixels}x${bgColor}_${fgColor}.png"
    }

    override fun saveNoiseBitmap(bitmap: Bitmap, sidePixels: Int, bgColor: Int, fgColor: Int) {
        val fileName = generateFileName(sidePixels, bgColor, fgColor)
        val file = File(cacheDir, fileName)
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (e: Exception) {
            Log.e("WidgetBitmapCache", "Failed to cache bitmap", e)
        }
    }

    override fun loadNoiseBitmap(sidePixels: Int, bgColor: Int, fgColor: Int): Bitmap? {
        val fileName = generateFileName(sidePixels, bgColor, fgColor)
        val file = File(cacheDir, fileName)
        return if (file.exists()) {
            try {
                BitmapFactory.decodeFile(file.absolutePath)
            } catch (e: Exception) {
                Log.e("WidgetBitmapCache", "Failed to load cached bitmap", e)
                null
            }
        } else {
            null
        }
    }
}