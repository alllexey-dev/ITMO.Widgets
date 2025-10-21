package dev.alllexey.itmowidgets.workers

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.widget.RemoteViews
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withSave
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay
import dev.alllexey.itmowidgets.ItmoWidgetsApp
import dev.alllexey.itmowidgets.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot

class QrAnimationWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    companion object {

        const val WORK_NAME = "me.alllexey123.itmowidgets.QrWidgetAnimation"
        const val KEY_APP_WIDGET_ID = "app_widget_id"
    }

    override suspend fun doWork(): Result {
        val appWidgetId = inputData.getInt(KEY_APP_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            return Result.failure()
        }

        runAnimation(appWidgetId)

        return Result.success()
    }

    private suspend fun runAnimation(appWidgetId: Int) {
        val appContainer = (applicationContext as ItmoWidgetsApp).appContainer
        val renderer = appContainer.qrBitmapRenderer
        val repository = appContainer.qrCodeRepository
        val generator = appContainer.qrCodeGenerator
        val qrBitmapCache = appContainer.qrBitmapCache
        val storage = appContainer.storage

        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
        val remoteViews = RemoteViews(applicationContext.packageName, R.layout.qr_code_widget)

        val durationMs = 600L
        val frameRate = 30
        val frameDelay = 1000L / frameRate
        val totalFrames = (durationMs / frameDelay).toInt()

        val qrSidePixels = renderer.defaultSidePixels()
        val dynamicColors = storage.getDynamicQrColorsState()

        val qrHex = repository.getQrHex()
        val qrCode = generator.generate(qrHex)
        val qrCodeBooleans = generator.toBooleans(qrCode)

        val qrBitmap = renderer.render(qrCode = qrCodeBooleans, dynamic = dynamicColors)
        val (bgColor, fgColor) = renderer.getQrColors(dynamicColors)
        val noiseBitmap = qrBitmapCache.loadNoiseBitmap(renderer.defaultSidePixels(), bgColor, fgColor)
            ?: renderer.renderNoise(dynamic = dynamicColors)

        val maxRadius = hypot(qrSidePixels / 2.0, qrSidePixels / 2.0).toFloat()

        for (frame in 0..totalFrames) {
            val startTime = System.currentTimeMillis()
            val progress = frame.toFloat() / totalFrames

            val easedProgress = easeInOut(progress)
            val currentRadius = maxRadius * easedProgress

            val frameBitmap = createBitmap(qrSidePixels, qrSidePixels)
            val canvas = Canvas(frameBitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            canvas.drawBitmap(qrBitmap, 0f, 0f, paint)

            canvas.withSave {
                val noiseClipPath = Path().apply {
                    addRect(0f, 0f, qrSidePixels.toFloat(), qrSidePixels.toFloat(), Path.Direction.CW)
                    addCircle(qrSidePixels / 2f, qrSidePixels / 2f, currentRadius, Path.Direction.CCW)
                }
                clipPath(noiseClipPath)
                drawBitmap(noiseBitmap, 0f, 0f, paint)
            }

            remoteViews.setImageViewBitmap(R.id.qr_code_image, frameBitmap)
            appWidgetManager.updateAppWidget(appWidgetId, remoteViews)

            val workTime = System.currentTimeMillis() - startTime
            val delayTime = (frameDelay - workTime).coerceAtLeast(0)
            delay(delayTime)
        }

        remoteViews.setImageViewBitmap(R.id.qr_code_image, qrBitmap)
        appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
    }

    private fun easeInOut(fraction: Float): Float {
        return (1f - cos(fraction * PI.toFloat())) / 2f
    }
}