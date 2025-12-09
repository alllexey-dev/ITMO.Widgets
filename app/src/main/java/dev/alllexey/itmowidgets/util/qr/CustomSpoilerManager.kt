package dev.alllexey.itmowidgets.util.qr

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
import java.io.File
import java.io.FileOutputStream
import androidx.core.graphics.createBitmap
import dev.alllexey.itmowidgets.appContainer

class CustomSpoilerManager(private val context: Context) {

    private fun getSpoilerFile(): File {
        val spoilersDir = File(context.filesDir, "qr_spoilers")
        if (!spoilersDir.exists()) {
            spoilersDir.mkdirs()
        }
        return File(spoilersDir, "custom_spoiler.png")
    }

    fun saveCustomSpoiler(uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val sourceBitmap = BitmapFactory.decodeStream(inputStream)
                if (sourceBitmap == null) return false

                val relativeRounding = 0.06f
                val cornerRadius = sourceBitmap.width * relativeRounding

                val roundedBitmap = getRoundedCornerBitmap(sourceBitmap, cornerRadius)

                FileOutputStream(getSpoilerFile()).use { outputStream ->
                    roundedBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
            }
            true
        } catch (e: Exception) {
            context.appContainer().errorLogRepository.logThrowable(e, CustomSpoilerManager::class.java.name)
            false
        }
    }
    private fun getRoundedCornerBitmap(bitmap: Bitmap, cornerRadius: Float): Bitmap {
        val output = createBitmap(bitmap.width, bitmap.height)
        val canvas = Canvas(output)

        val paint = Paint().apply {
            color = -0x1
            isAntiAlias = true
        }

        val rect = Rect(0, 0, bitmap.width, bitmap.height)
        val rectF = RectF(rect)

        canvas.drawARGB(0, 0, 0, 0)
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)

        return output
    }

    fun getCustomSpoilerBitmap(): Bitmap? {
        val spoilerFile = getSpoilerFile()
        return if (spoilerFile.exists()) {
            BitmapFactory.decodeFile(spoilerFile.absolutePath)
        } else {
            null
        }
    }

    fun deleteCustomSpoiler(): Boolean {
        val spoilerFile = getSpoilerFile()
        if (spoilerFile.exists()) {
            return spoilerFile.delete()
        }
        return false
    }

    fun hasCustomSpoiler(): Boolean {
        return getSpoilerFile().exists()
    }
}