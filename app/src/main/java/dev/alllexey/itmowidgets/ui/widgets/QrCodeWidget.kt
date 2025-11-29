package dev.alllexey.itmowidgets.ui.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import android.widget.Toast
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dev.alllexey.itmowidgets.ItmoWidgetsApp
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.appContainer
import dev.alllexey.itmowidgets.ui.widgets.data.QrWidgetState
import dev.alllexey.itmowidgets.workers.QrAnimationWorker
import dev.alllexey.itmowidgets.workers.QrWidgetUpdateWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QrCodeWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
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
                val appContainer = context.appContainer()
                val state = appContainer.storage.utility.getQrWidgetState(appWidgetId)
                when (state) {
                    QrWidgetState.SPOILER -> startAnimationWorker(context, appWidgetId)
                    QrWidgetState.ANIMATING -> {}
                    QrWidgetState.SHOWING_QR -> forceBackgroundUpdate(context, appWidgetId)
                    QrWidgetState.UPDATING -> showUpdatingToast(context)
                }
            }
        }
    }

    private fun startAnimationWorker(context: Context, appWidgetId: Int) {
        val inputData = workDataOf(QrAnimationWorker.KEY_APP_WIDGET_ID to appWidgetId)

        val animationWorkRequest = OneTimeWorkRequestBuilder<QrAnimationWorker>()
            .setInputData(inputData)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            QrAnimationWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            animationWorkRequest
        )

        forceBackgroundUpdate(context, appWidgetId)
        QrWidgetUpdateWorker.scheduleNextUpdate(context, 30)
    }

    private fun showUpdatingToast(context: Context) {
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(context, "QR-код уже обновляется!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun forceBackgroundUpdate(context: Context, appWidgetId: Int) {
        val appContext = context.applicationContext
        val appContainer = (appContext as ItmoWidgetsApp).appContainer
        val qrToolkit = appContainer.qrToolkit

        CoroutineScope(Dispatchers.IO).launch {
            try {
                appContainer.storage.utility.setQrWidgetState(
                    appWidgetId,
                    QrWidgetState.UPDATING
                )

                val qrHex = qrToolkit.getQrHex(allowCached = false)
                val qrBitmap = qrToolkit.generateQrBitmap(qrHex)

                val appWidgetManager = AppWidgetManager.getInstance(appContext)
                withContext(Dispatchers.Main) {
                    val state = appContainer.storage.utility.getQrWidgetState(appWidgetId)
                    if (state == QrWidgetState.SHOWING_QR) {
                        updateAppWidget(
                            appContext,
                            appWidgetManager,
                            appWidgetId,
                            qrBitmap
                        )
                    }

                    Toast.makeText(context, "QR-код обновлён", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Ошибка обновления QR: ${e.message}", Toast.LENGTH_LONG)
                        .show()
                }
            }
            appContainer.storage.utility.setQrWidgetState(appWidgetId, QrWidgetState.SHOWING_QR)
        }
    }

    companion object {

        const val ACTION_WIDGET_CLICK: String = "me.alllexey123.itmowidgets.action.QR_WIDGET_CLICK"

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            bitmap: Bitmap
        ) {
            val views = RemoteViews(context.packageName, R.layout.qr_code_widget)
            val pendingIntent = getClickIntent(context, appWidgetId)
            views.setOnClickPendingIntent(R.id.qr_code_image, pendingIntent)
            views.setImageViewBitmap(R.id.qr_code_image, bitmap)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }


        fun getClickIntent(context: Context, appWidgetId: Int): PendingIntent {

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
