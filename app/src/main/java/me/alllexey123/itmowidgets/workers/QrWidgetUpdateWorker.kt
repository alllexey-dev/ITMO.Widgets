package me.alllexey123.itmowidgets.workers

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import me.alllexey123.itmowidgets.providers.QrCodeProvider
import me.alllexey123.itmowidgets.providers.StorageProvider
import me.alllexey123.itmowidgets.widgets.QrCodeWidget
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class QrWidgetUpdateWorker(val appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val appWidgetManager = AppWidgetManager.getInstance(appContext)
        val widgetProvider = ComponentName(appContext, QrCodeWidget::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(widgetProvider)

        val storage = StorageProvider.getStorage(appContext)

        if (appWidgetIds.isEmpty()) {
            return Result.success()
        }

        val colors = QrCodeProvider.getQrColors(appContext, storage.getDynamicQrColorsState())
        val whiteColor = colors.first
        val blackColor = colors.second

        val bitmap: Bitmap = try {
            val qrCode = QrCodeProvider.getQrCode(appContext)
            QrCodeProvider.qrCodeToBitmap(qrCode, 21, 20, whiteColor, blackColor)
        } catch (e: Exception) {
            storage.setErrorLog("[${javaClass.name}] at ${LocalDateTime.now()}: ${e.stackTraceToString()}")
            QrCodeProvider.emptyQrCode(400, 20F, Color.DKGRAY)
        }

        for (appWidgetId in appWidgetIds) {
            QrCodeWidget.updateAppWidget(
                appContext,
                appWidgetManager,
                appWidgetId,
                bitmap,
                whiteColor
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