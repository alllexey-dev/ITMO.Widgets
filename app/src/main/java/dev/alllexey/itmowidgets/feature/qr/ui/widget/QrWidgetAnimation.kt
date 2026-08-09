package dev.alllexey.itmowidgets.feature.qr.ui.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.createBitmap
import dev.alllexey.itmowidgets.core.settings.QrAnimationType
import kotlin.math.PI
import kotlin.math.cos

/**
 * Blends [from] into [to] as `progress` goes from 0 to 1.
 *
 * Reveal and hide use the same implementation with the two bitmaps swapped.
 */
interface QrWidgetAnimation {

    fun frame(progress: Float): Bitmap

    companion object {

        fun of(type: QrAnimationType, from: Bitmap, to: Bitmap): QrWidgetAnimation? {
            return when (type) {
                QrAnimationType.FADE -> FadeAnimation(from, to)
                QrAnimationType.CIRCLE -> CircleAnimation(from, to)
                QrAnimationType.NONE -> null
            }
        }

        /** Slow in, slow out; keeps the first and last frames calm. */
        fun ease(fraction: Float): Float = (1f - cos(fraction * PI.toFloat())) / 2f
    }
}

private class FadeAnimation(
    private val from: Bitmap,
    private val to: Bitmap
) : QrWidgetAnimation {

    private val paint = Paint().apply { isAntiAlias = true }

    override fun frame(progress: Float): Bitmap {
        val bitmap = createBitmap(from.width, from.height)
        val canvas = Canvas(bitmap)

        paint.alpha = 255
        canvas.drawBitmap(from, 0f, 0f, paint)
        paint.alpha = (progress.coerceIn(0f, 1f) * 255).toInt()
        canvas.drawBitmap(to, 0f, 0f, paint)

        return bitmap
    }
}

private class CircleAnimation(
    private val from: Bitmap,
    private val to: Bitmap
) : QrWidgetAnimation {

    private val paint = Paint().apply { isAntiAlias = true }

    override fun frame(progress: Float): Bitmap {
        val bitmap = createBitmap(from.width, from.height)
        val canvas = Canvas(bitmap)
        val size = from.width.toFloat()

        canvas.drawBitmap(from, 0f, 0f, paint)

        val maxRadius = size * MAX_RADIUS_FACTOR
        val radius = progress.coerceIn(0f, 1f) * maxRadius
        if (radius > 0f) {
            canvas.save()
            canvas.clipPath(
                Path().apply { addCircle(size / 2f, size / 2f, radius, Path.Direction.CW) }
            )
            canvas.drawBitmap(to, 0f, 0f, paint)
            canvas.restore()
        }

        return bitmap
    }

    private companion object {
        /** Half the diagonal, so the circle covers the corners. */
        const val MAX_RADIUS_FACTOR = 0.71f
    }
}
