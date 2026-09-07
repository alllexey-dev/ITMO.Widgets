package dev.alllexey.itmowidgets.core.qr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.util.AtomicFile
import androidx.core.graphics.createBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomSpoilerManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    private val imageRevision = AtomicLong()
    val revision: Long get() = imageRevision.get()

    private val spoilerFile: File
        get() = File(context.filesDir, "qr_custom_spoiler/custom_spoiler.png")

    /** The previous image remains readable if decoding or writing the replacement fails. */
    @Synchronized
    fun saveCustomSpoiler(uri: Uri): Boolean {
        var source: Bitmap? = null
        var rounded: Bitmap? = null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
            val options = BitmapFactory.Options().apply { inSampleSize = 1 }
            while (maxOf(bounds.outWidth, bounds.outHeight) / options.inSampleSize > SIZE * 2) {
                options.inSampleSize *= 2
            }
            source = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return false
            rounded = roundedSquare(source)
            spoilerFile.parentFile?.mkdirs()
            val file = AtomicFile(spoilerFile)
            val output = file.startWrite()
            try {
                check(rounded.compress(Bitmap.CompressFormat.PNG, 100, output))
                file.finishWrite(output)
                imageRevision.incrementAndGet()
            } catch (error: Exception) {
                file.failWrite(output)
                throw error
            }
            true
        } catch (_: Exception) {
            false
        } finally {
            source?.recycle()
            rounded?.recycle()
        }
    }

    private fun roundedSquare(bitmap: Bitmap): Bitmap {
        val output = createBitmap(SIZE, SIZE)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val target = RectF(0f, 0f, SIZE.toFloat(), SIZE.toFloat())
        canvas.drawRoundRect(target, SIZE * 0.06f, SIZE * 0.06f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        val side = minOf(bitmap.width, bitmap.height)
        val left = (bitmap.width - side) / 2
        val top = (bitmap.height - side) / 2
        canvas.drawBitmap(bitmap, Rect(left, top, left + side, top + side), target, paint)
        return output
    }

    @Synchronized
    fun getCustomSpoilerBitmap(): Bitmap? = try {
        AtomicFile(spoilerFile).openRead().use { BitmapFactory.decodeStream(it) }
    } catch (_: Exception) {
        null
    }

    @Synchronized
    fun deleteCustomSpoiler(): Boolean = try {
        AtomicFile(spoilerFile).delete()
        (!spoilerFile.exists() && !File(spoilerFile.path + ".bak").exists()).also { deleted ->
            if (deleted) imageRevision.incrementAndGet()
        }
    } catch (_: Exception) {
        false
    }

    @Synchronized
    fun hasCustomSpoiler(): Boolean =
        spoilerFile.exists() || File(spoilerFile.path + ".bak").exists()

    private companion object {
        const val SIZE = 420
    }
}
