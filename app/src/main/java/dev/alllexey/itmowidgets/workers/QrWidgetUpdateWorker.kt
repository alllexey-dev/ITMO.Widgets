package dev.alllexey.itmowidgets.workers

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.alllexey.itmowidgets.ItmoWidgetsApp
import dev.alllexey.itmowidgets.ui.widgets.QrCodeWidget
import dev.alllexey.itmowidgets.ui.widgets.data.QrWidgetState
import java.util.concurrent.TimeUnit

class QrWidgetUpdateWorker(val appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val appContainer = (applicationContext as ItmoWidgetsApp).appContainer
        val storage = appContainer.storage
        val qrToolkit = appContainer.qrToolkit

        val appWidgetManager = AppWidgetManager.getInstance(appContext)
        val appWidgetIds =
            appWidgetManager.getAppWidgetIds(ComponentName(appContext, QrCodeWidget::class.java))

        if (appWidgetIds.isEmpty()) {
            return Result.success()
        }

        val bitmap: Bitmap = if (storage.settings.getQrSpoilerState()) {
            qrToolkit.generateSpoilerBitmap()
        } else {
            val qrHex = qrToolkit.getQrHex(allowCached = false)
            qrToolkit.generateQrBitmap(qrHex)
        }

        for (appWidgetId in appWidgetIds) {
            if (storage.settings.getQrSpoilerState()) {
                storage.utility.setQrWidgetState(appWidgetId, QrWidgetState.SPOILER)
            } else {
                storage.utility.setQrWidgetState(appWidgetId, QrWidgetState.SHOWING_QR)
            }

            QrCodeWidget.updateAppWidget(
                appContext,
                appWidgetManager,
                appWidgetId,
                bitmap
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

        fun scheduleNextUpdate(context: Context, durationSeconds: Long = 60 * 60L) {
            val updateWorkRequest = OneTimeWorkRequestBuilder<QrWidgetUpdateWorker>()
                .setInitialDelay(durationSeconds, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WIDGET_UPDATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                updateWorkRequest
            )
        }
    }
}