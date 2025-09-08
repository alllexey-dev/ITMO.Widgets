package me.alllexey123.itmowidgets.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.graphics.Bitmap
import android.widget.RemoteViews
import androidx.work.WorkManager
import me.alllexey123.itmowidgets.QrWidgetUpdateWorker
import me.alllexey123.itmowidgets.R

class QrCodeWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        QrWidgetUpdateWorker.Companion.enqueueImmediateUpdate(context)
    }

    override fun onEnabled(context: Context) {
        QrWidgetUpdateWorker.Companion.enqueueImmediateUpdate(context)
    }

    override fun onDisabled(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(QrWidgetUpdateWorker.Companion.WIDGET_UPDATE_WORK_NAME)
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, bitmap: Bitmap) {

            val views = RemoteViews(context.packageName, R.layout.qr_code_widget)

            views.setImageViewBitmap(R.id.qr_code_image, bitmap)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
