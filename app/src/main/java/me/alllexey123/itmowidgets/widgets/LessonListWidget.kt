package me.alllexey123.itmowidgets.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.net.toUri
import me.alllexey123.itmowidgets.R
import me.alllexey123.itmowidgets.workers.LessonWidgetUpdateWorker

class LessonListWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager?,
        appWidgetIds: IntArray?
    ) {
        LessonWidgetUpdateWorker.Companion.enqueueImmediateUpdate(context)
    }

    companion object {

        @Suppress("DEPRECATION")
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            lessons: List<SingleLessonData>,
            layoutId: Int,
            rowLayoutId: Int,
            fullDayEmpty: Boolean,
            onlyDataChanged: Boolean
        ) {
            LessonRepository.setLessons(lessons)
            LessonRepository.rowLayoutId = rowLayoutId
            LessonRepository.bonusLayoutId = if (fullDayEmpty) R.layout.lesson_list_empty else R.layout.lesson_list_no_more

            if (!onlyDataChanged){
                val intent = Intent(context, LessonListWidgetService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    data = ("widget://${appWidgetId}-${System.currentTimeMillis()}").toUri()
                }
                val views = RemoteViews(context.packageName, layoutId)
                views.setRemoteAdapter(R.id.lesson_list, intent)

                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.lesson_list)
        }


    }
}