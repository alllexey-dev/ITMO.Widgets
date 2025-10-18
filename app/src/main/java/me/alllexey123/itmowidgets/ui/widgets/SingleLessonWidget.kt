package me.alllexey123.itmowidgets.ui.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import me.alllexey123.itmowidgets.AppIntents
import me.alllexey123.itmowidgets.R
import me.alllexey123.itmowidgets.ui.main.MainActivity
import me.alllexey123.itmowidgets.ui.schedule.ScheduleFragment
import me.alllexey123.itmowidgets.ui.widgets.data.SingleLessonWidgetData
import me.alllexey123.itmowidgets.util.ScheduleUtils
import me.alllexey123.itmowidgets.workers.LessonWidgetUpdateWorker

open class SingleLessonWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        LessonWidgetUpdateWorker.Companion.enqueueImmediateUpdate(context)
    }

    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            widgetData: SingleLessonWidgetData
        ) {
            val views = RemoteViews(context.packageName, widgetData.layoutId)

            views.setTextViewText(R.id.title, widgetData.subject)
            views.setTextViewText(R.id.teacher, widgetData.teacher)
            val roomText = if (widgetData.building == null) widgetData.room else widgetData.room?.let { room -> "${room}, " }
            views.setTextViewText(R.id.location_room, roomText)
            views.setTextViewText(R.id.location_building, widgetData.building)
            views.setTextViewText(R.id.time, widgetData.times)
            views.setTextViewText(R.id.more_lessons_text, widgetData.moreLessonsText)
            views.setViewVisibility(R.id.teacher_layout, if (widgetData.teacher.isNullOrEmpty()) View.GONE else View.VISIBLE)
            views.setViewVisibility(R.id.location_layout, if (widgetData.room == null && widgetData.building == null) View.GONE else View.VISIBLE)
            views.setViewVisibility(R.id.time_layout, if (widgetData.times.isNullOrEmpty()) View.GONE else View.VISIBLE)
            views.setViewVisibility(R.id.more_lessons_layout, if (widgetData.moreLessonsText.isNullOrEmpty()) View.GONE else View.VISIBLE)

            val colorId = ScheduleUtils.getWorkTypeColor(widgetData.workTypeId)
            views.setInt(
                R.id.type_indicator, "setColorFilter",
                ContextCompat.getColor(context, colorId)
            )

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

            views.setOnClickPendingIntent(R.id.lesson_widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}

