package dev.alllexey.itmowidgets.ui.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.net.toUri
import dev.alllexey.itmowidgets.AppIntents
import dev.alllexey.itmowidgets.ItmoWidgetsApp
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.ui.main.MainActivity
import dev.alllexey.itmowidgets.workers.LessonWidgetUpdateWorker


class LessonListWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager?,
        appWidgetIds: IntArray?
    ) {
        val appContainer = (context.applicationContext as ItmoWidgetsApp).appContainer
        appContainer.storage.utility.setLessonWidgetStyleChanged(true)
        LessonWidgetUpdateWorker.Companion.enqueueImmediateUpdate(context)
    }

    companion object {

        @Suppress("DEPRECATION")
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            layoutId: Int,
            onlyDataChanged: Boolean
        ) {
            if (!onlyDataChanged){
                val intent = Intent(context, LessonListWidgetViewsFactory::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    data = ("widget://${appWidgetId}-${System.currentTimeMillis()}").toUri()
                }

                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    putExtra(AppIntents.EXTRA_TARGET_FRAGMENT, AppIntents.TARGET_SCHEDULE_FRAGMENT)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }

                val pendingIntent = PendingIntent.getActivity(
                    context,
                    appWidgetId,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val views = RemoteViews(context.packageName, layoutId)
                views.setPendingIntentTemplate(R.id.lesson_list, pendingIntent)
                views.setRemoteAdapter(R.id.lesson_list, intent)

                appWidgetManager.updateAppWidget(appWidgetId, views)
            } else {
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.lesson_list)
            }
        }


    }
}