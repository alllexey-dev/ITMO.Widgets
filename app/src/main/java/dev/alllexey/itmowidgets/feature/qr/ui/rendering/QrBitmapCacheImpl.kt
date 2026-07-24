package dev.alllexey.itmowidgets.feature.qr.ui.rendering

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject

class QrBitmapCacheImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : QrBitmapCache {

    private val cacheDir: File by lazy {
        File(context.cacheDir, "bitmap_cache")
    }

    override fun getBitmap(type: String, meta: String): Bitmap? {
        val fileName = generateFileName(meta)
        val typeDir = File(cacheDir, type)
        val file = File(typeDir, fileName)
        return if (file.exists()) {
            try {
                BitmapFactory.decodeFile(file.absolutePath)
            } catch (e: Exception) {
                Log.e("QrBitmapCache", "Failed to load cached bitmap", e)
                null
            }
        } else {
            null
        }
    }

    override fun saveBitmap(type: String, meta: String, bitmap: Bitmap) {
        val fileName = generateFileName(meta)
        val typeDir = File(cacheDir, type)
        if (!typeDir.exists()) typeDir.mkdirs()
        val file = File(typeDir, fileName)
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (e: Exception) {
            Log.e("WidgetBitmapCache", "Failed to cache bitmap", e)
        }
    }

    internal fun generateFileName(meta: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(meta.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
        return "$digest.png"
    }

    override fun clearCache(type: String?) {
        if (type == null) {
            cacheDir.deleteRecursively()
        } else {
            File(cacheDir, type).deleteRecursively()
        }
    }
}
