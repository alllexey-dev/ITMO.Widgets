package dev.alllexey.itmowidgets.workers

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.widget.RemoteViews
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withSave
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.alllexey.itmowidgets.ItmoWidgetsApp
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.ui.widgets.data.QrAnimationType
import dev.alllexey.itmowidgets.ui.widgets.data.QrWidgetState
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class QrAnimationWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "me.alllexey123.itmowidgets.QrWidgetAnimation"
        const val KEY_APP_WIDGET_ID = "app_widget_id"

        fun easeInOut(fraction: Float): Float {
            return (1f - cos(fraction * PI.toFloat())) / 2f
        }

        fun easeOut(fraction: Float): Float {
            return sin(fraction * PI.toFloat() / 2f)
        }

        fun easeIn(fraction: Float): Float {
            return fraction * fraction
        }
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
        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
        val appContainer = (applicationContext as ItmoWidgetsApp).appContainer
        val qrToolkit = appContainer.qrToolkit

        val durationMs = 800L
        val frameRate = 60
        val frameDelay = 1000L / frameRate
        val totalFrames = (durationMs / frameDelay).toInt()

        val qrHex = qrToolkit.getQrHex()
        val qrBitmap = qrToolkit.generateQrBitmap(qrHex)
        val spoilerBitmap = qrToolkit.generateSpoilerBitmap()

        val animationType = appContainer.storage.settings.getQrSpoilerAnimationType()
        val animation: QrAnimation = when (animationType) {
            QrAnimationType.CIRCLE -> CircleAnimation(
                qrCodeBitmap = qrBitmap,
                spoilerBitmap = spoilerBitmap
            )

            QrAnimationType.FADE -> FadeAnimation(
                qrCodeBitmap = qrBitmap,
                spoilerBitmap = spoilerBitmap
            )
        }

        appContainer.storage.utility.setQrWidgetState(appWidgetId, QrWidgetState.ANIMATING)

        var remoteViews = RemoteViews(applicationContext.packageName, R.layout.qr_code_widget)
        for (frame in 0..totalFrames) {
            val startTime = System.currentTimeMillis()
            val progress = frame.toFloat() / totalFrames
            val easedProgress = easeInOut(progress)

            val frameBitmap = animation.getBitmap(easedProgress)

            if (frame % 10 == 0) {
                remoteViews = RemoteViews(applicationContext.packageName, R.layout.qr_code_widget)
            }

            remoteViews.setImageViewBitmap(R.id.qr_code_image, frameBitmap)
            appWidgetManager.updateAppWidget(appWidgetId, remoteViews)

            val workTime = System.currentTimeMillis() - startTime
            val delayTime = (frameDelay - workTime).coerceAtLeast(0)
            delay(delayTime)
        }

        appContainer.storage.utility.setQrWidgetState(appWidgetId, QrWidgetState.SHOWING_QR)

        remoteViews.setImageViewBitmap(R.id.qr_code_image, qrBitmap)
        appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
    }


    interface QrAnimation {
        fun getBitmap(progress: Float): Bitmap
    }

    class FadeAnimation(
        private val qrCodeBitmap: Bitmap,
        private val spoilerBitmap: Bitmap,
        private val qrSidePixels: Int = qrCodeBitmap.height
    ) : QrAnimation {
        override fun getBitmap(progress: Float): Bitmap {
            val frameBitmap = createBitmap(qrSidePixels, qrSidePixels)
            val canvas = Canvas(frameBitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            val alphaQr = (progress * 255).toInt().coerceIn(0, 255)
            val alphaNoise = 255 - alphaQr

            paint.alpha = alphaNoise
            canvas.drawBitmap(spoilerBitmap, 0f, 0f, paint)

            paint.alpha = alphaQr
            canvas.drawBitmap(qrCodeBitmap, 0f, 0f, paint)

            return frameBitmap
        }
    }

    class CircleAnimation(
        val qrCodeBitmap: Bitmap,
        val spoilerBitmap: Bitmap,
        val qrSidePixels: Int = qrCodeBitmap.height,
        val maxRadius: Float = hypot(qrSidePixels / 2.0, qrSidePixels / 2.0).toFloat()
    ) : QrAnimation {
        override fun getBitmap(
            progress: Float
        ): Bitmap {
            val currentRadius = maxRadius * progress

            val frameBitmap = createBitmap(qrSidePixels, qrSidePixels)
            val canvas = Canvas(frameBitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            canvas.drawBitmap(qrCodeBitmap, 0f, 0f, paint)

            canvas.withSave {
                val noiseClipPath = Path().apply {
                    addRect(
                        0f,
                        0f,
                        qrSidePixels.toFloat(),
                        qrSidePixels.toFloat(),
                        Path.Direction.CW
                    )
                    addCircle(
                        qrSidePixels / 2f,
                        qrSidePixels / 2f,
                        currentRadius,
                        Path.Direction.CCW
                    )
                }
                clipPath(noiseClipPath)
                drawBitmap(spoilerBitmap, 0f, 0f, paint)
            }

            return frameBitmap
        }
    }
}