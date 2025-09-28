package me.alllexey123.itmowidgets.ui.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import androidx.work.WorkManager
import me.alllexey123.itmowidgets.ItmoWidgetsApp
import me.alllexey123.itmowidgets.R
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
            val appContainer = (context.applicationContext as ItmoWidgetsApp).appContainer
            val renderer = appContainer.qrBitmapRenderer
            val dynamicColors = appContainer.storage.getDynamicQrColorsState()
            appContainer.qrCodeRepository.clearCache() // force clear cache

            val appWidgetManager = AppWidgetManager.getInstance(context)
            val views = RemoteViews(context.packageName, R.layout.qr_code_widget)
            val bitmap = renderer.renderEmpty(21 * PIXELS_PER_MODULE, PIXELS_PER_MODULE / 2F, dynamicColors)

            views.setImageViewBitmap(R.id.qr_code_image, bitmap)

            appWidgetManager.updateAppWidget(appWidgetId, views)
            QrWidgetUpdateWorker.enqueueImmediateUpdate(context)
        }
    }

    override fun onEnabled(context: Context) {
//        QrWidgetUpdateWorker.Companion.enqueueImmediateUpdate(context)
    }

    override fun onDisabled(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(QrWidgetUpdateWorker.Companion.WIDGET_UPDATE_WORK_NAME)
    }

    companion object {

        const val PIXELS_PER_MODULE = 20

        const val ACTION_WIDGET_CLICK: String = "me.alllexey123.itmowidgets.action.QR_WIDGET_CLICK"

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, bitmap: Bitmap, bgColor: Int) {

            val views = RemoteViews(context.packageName, R.layout.qr_code_widget)

            views.setInt(
                R.id.qr_bg_image, "setColorFilter",
                bgColor
            )

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
