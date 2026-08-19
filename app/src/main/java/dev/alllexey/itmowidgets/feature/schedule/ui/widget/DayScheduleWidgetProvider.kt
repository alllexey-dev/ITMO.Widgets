package dev.alllexey.itmowidgets.feature.schedule.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import dev.alllexey.itmowidgets.feature.schedule.work.ScheduleWidgetWork

class DayScheduleWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        ScheduleWidgetWork.ensurePeriodic(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { appWidgetId ->
            ScheduleWidgetRenderer.renderListShell(
                context,
                appWidgetManager,
                appWidgetId
            )
        }
        ScheduleWidgetWork.ensurePeriodic(context)
        ScheduleWidgetWork.enqueueUpdate(context)
    }

    override fun onDisabled(context: Context) {
        ScheduleWidgetWork.cancelIfUnused(context)
    }
}
