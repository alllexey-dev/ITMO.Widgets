package dev.alllexey.itmowidgets.feature.schedule.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import dev.alllexey.itmowidgets.feature.schedule.work.ScheduleWidgetEntryPoint
import dev.alllexey.itmowidgets.feature.schedule.work.ScheduleWidgetWork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DayScheduleWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        ScheduleWidgetWork.ensurePeriodic(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pending = goAsync()
        scope.launch {
            try {
                val snapshot = ScheduleWidgetEntryPoint.from(context)
                    .scheduleWidgetSnapshotStore()
                    .read()
                appWidgetIds.forEach { appWidgetId ->
                    ScheduleWidgetRenderer.renderList(
                        context,
                        appWidgetManager,
                        appWidgetId,
                        snapshot
                    )
                }
                // WorkManager toggling its own components re-sends APPWIDGET_UPDATE: only the throttled
                // enqueue and the kept periodic job may follow, or the widget refreshes in a loop.
                ScheduleWidgetWork.ensurePeriodic(context)
                ScheduleWidgetWork.enqueueUpdate(context)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDisabled(context: Context) {
        ScheduleWidgetWork.cancelIfUnused(context)
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
