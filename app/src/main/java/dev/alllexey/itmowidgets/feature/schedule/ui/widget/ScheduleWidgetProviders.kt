package dev.alllexey.itmowidgets.feature.schedule.ui.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

object ScheduleWidgetProviders {

    fun singleLessonIds(context: Context): IntArray {
        return AppWidgetManager.getInstance(context).getAppWidgetIds(
            ComponentName(context, SingleLessonWidgetProvider::class.java)
        )
    }

    fun dayScheduleIds(context: Context): IntArray {
        return AppWidgetManager.getInstance(context).getAppWidgetIds(
            ComponentName(context, DayScheduleWidgetProvider::class.java)
        )
    }

    fun hasAnyWidget(context: Context): Boolean {
        return singleLessonIds(context).isNotEmpty() || dayScheduleIds(context).isNotEmpty()
    }
}
