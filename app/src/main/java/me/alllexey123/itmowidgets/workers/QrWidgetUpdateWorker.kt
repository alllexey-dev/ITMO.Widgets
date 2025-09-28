package me.alllexey123.itmowidgets.workers

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import me.alllexey123.itmowidgets.ItmoWidgetsApp
import me.alllexey123.itmowidgets.ui.widgets.QrCodeWidget
import me.alllexey123.itmowidgets.ui.widgets.QrCodeWidget.Companion.PIXELS_PER_MODULE
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class QrWidgetUpdateWorker(val appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val appContainer = (applicationContext as ItmoWidgetsApp).appContainer
        val repository = appContainer.qrCodeRepository
        val generator = appContainer.qrCodeGenerator
        val renderer = appContainer.qrBitmapRenderer
        val storage = appContainer.storage

        val appWidgetManager = AppWidgetManager.getInstance(appContext)
        val appWidgetIds =
            appWidgetManager.getAppWidgetIds(ComponentName(appContext, QrCodeWidget::class.java))

        if (appWidgetIds.isEmpty()) {
            return Result.success()
        }

        val dynamicColors = storage.getDynamicQrColorsState()

        val bitmap: Bitmap = try {
            val qrHex = repository.getQrHex()
            val qrCode = generator.generate(qrHex)
            renderer.render(qrCode, PIXELS_PER_MODULE, dynamicColors)
        } catch (e: Exception) {
            storage.setErrorLog("[${javaClass.name}] at ${LocalDateTime.now()}: ${e.stackTraceToString()}")
            renderer.renderEmpty(21 * PIXELS_PER_MODULE, PIXELS_PER_MODULE / 2F, dynamicColors)
        }

        val colors = renderer.getQrColors(dynamicColors)

        for (appWidgetId in appWidgetIds) {
            QrCodeWidget.updateAppWidget(
                appContext,
                appWidgetManager,
                appWidgetId,
                bitmap,
                colors.first
            )
        }

        scheduleNextUpdate(appContext)
        return Result.success()
    }

    companion object {
        const val WIDGET_UPDATE_WORK_NAME = "me.alllexey123.itmowidgets.QrWidgetUpdate"

        fun enqueueImmediateUpdate(context: Context) {
            val immediateWorkRequest = OneTimeWorkRequestBuilder<QrWidgetUpdateWorker>()
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WIDGET_UPDATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                immediateWorkRequest
            )
        }

        fun scheduleNextUpdate(context: Context) {
            val duration = 60L
            val updateWorkRequest = OneTimeWorkRequestBuilder<QrWidgetUpdateWorker>()
                .setInitialDelay(duration, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WIDGET_UPDATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                updateWorkRequest
            )
        }
    }
}