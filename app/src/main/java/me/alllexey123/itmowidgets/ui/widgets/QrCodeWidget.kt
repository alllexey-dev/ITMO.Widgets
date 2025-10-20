package me.alllexey123.itmowidgets.ui.widgets

import android.R.attr.action
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import me.alllexey123.itmowidgets.ItmoWidgetsApp
import me.alllexey123.itmowidgets.R
import me.alllexey123.itmowidgets.workers.QrAnimationWorker
import me.alllexey123.itmowidgets.workers.QrWidgetUpdateWorker

class QrCodeWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.qr_code_widget)
            val pendingIntent = getClickIntent(context, appWidgetId)
            views.setOnClickPendingIntent(R.id.qr_code_image, pendingIntent)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        QrWidgetUpdateWorker.Companion.enqueueImmediateUpdate(context)
    }

    // on qr code click
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (ACTION_WIDGET_CLICK == intent.action) {
            val appWidgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )

            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                startAnimationWorker(context, appWidgetId)
            }
        }
    }

    private fun startAnimationWorker(context: Context, appWidgetId: Int) {
        val inputData = workDataOf(QrAnimationWorker.KEY_APP_WIDGET_ID to appWidgetId)

        val animationWorkRequest = OneTimeWorkRequestBuilder<QrAnimationWorker>()
            .setInputData(inputData)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(QrAnimationWorker.WORK_NAME, ExistingWorkPolicy.KEEP, animationWorkRequest)
    }

    companion object {

        const val ACTION_WIDGET_CLICK: String = "me.alllexey123.itmowidgets.action.QR_WIDGET_CLICK"

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, bitmap: Bitmap) {
            val views = RemoteViews(context.packageName, R.layout.qr_code_widget)
            val pendingIntent = getClickIntent(context, appWidgetId)
            views.setOnClickPendingIntent(R.id.qr_code_image, pendingIntent)
            views.setImageViewBitmap(R.id.qr_code_image, bitmap)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }


        fun getClickIntent(context: Context, appWidgetId: Int):  PendingIntent {

            val intent = Intent(context, QrCodeWidget::class.java)
            intent.setAction(ACTION_WIDGET_CLICK)
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT + PendingIntent.FLAG_IMMUTABLE
            )

            return pendingIntent
        }
    }
}
