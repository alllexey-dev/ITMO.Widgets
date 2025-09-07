package me.alllexey123.itmowidgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.work.WorkManager

class LessonWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        WidgetUpdateWorker.enqueueImmediateUpdate(context)
    }

    override fun onEnabled(context: Context) {
        WidgetUpdateWorker.enqueueImmediateUpdate(context)
    }

    override fun onDisabled(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WidgetUpdateWorker.WIDGET_UPDATE_WORK_NAME)
    }
}

class LessonData(
    val subject: String, val times: String, val teacher: String,
    val workTypeId: Int, val room: String, val building: String,
    val hideTeacher: Boolean, val hideLocation: Boolean, val hideTime: Boolean
)

fun getShortBuildingName(buildingName: String): String {
    if (buildingName.contains("Кронв")) return "Кронва"
    if (buildingName.contains("Ломо")) return "Ломо"
    return buildingName.substring(0, 6)
}

internal fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    lessonData: LessonData
) {
    val views = RemoteViews(context.packageName, R.layout.lesson_widget)

    views.setTextViewText(R.id.title, lessonData.subject)
    views.setTextViewText(R.id.teacher, lessonData.teacher)
    views.setTextViewText(R.id.location_room, lessonData.room)
    views.setTextViewText(R.id.location_building, lessonData.building)
    views.setViewVisibility(R.id.teacher_layout, if (lessonData.hideTeacher) View.GONE else View.VISIBLE)
    views.setViewVisibility(R.id.location_layout, if (lessonData.hideLocation) View.GONE else View.VISIBLE)
    views.setViewVisibility(R.id.time_layout, if (lessonData.hideTime) View.GONE else View.VISIBLE)

    views.setTextViewText(R.id.time, lessonData.times)

    val colorId = when (lessonData.workTypeId) {
        -1 -> R.color.free_color
        1 -> R.color.lecture_color
        2 -> R.color.lab_color
        3 -> R.color.practice_color
        else -> R.color.subtext_color
    }

    views.setInt(
        R.id.divider, "setColorFilter",
        ContextCompat.getColor(context, colorId)
    )

    appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
}