package me.alllexey123.itmowidgets.ui.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.net.toUri
import me.alllexey123.itmowidgets.ItmoWidgetsApp
import me.alllexey123.itmowidgets.R
import me.alllexey123.itmowidgets.ui.schedule.ScheduleFragment
import me.alllexey123.itmowidgets.workers.LessonWidgetUpdateWorker


class LessonListWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager?,
        appWidgetIds: IntArray?
    ) {
        val appContainer = (context.applicationContext as ItmoWidgetsApp).appContainer
        appContainer.storage.setLessonWidgetStyleChanged(true)
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
                val intent = Intent(context, LessonListWidgetService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    data = ("widget://${appWidgetId}-${System.currentTimeMillis()}").toUri()
                }

//                val pendingIntent = ScheduleFragment.getOnClickPendingIntent(context)

                val views = RemoteViews(context.packageName, layoutId)
//                views.setPendingIntentTemplate(R.id.lesson_list, pendingIntent)
                views.setRemoteAdapter(R.id.lesson_list, intent)

                appWidgetManager.updateAppWidget(appWidgetId, views)
            } else {
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.lesson_list)
            }
        }


    }
}