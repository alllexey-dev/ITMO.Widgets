package dev.alllexey.itmowidgets.feature.schedule.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import dev.alllexey.itmowidgets.core.settings.WidgetPaletteSource
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.di.KoinStarter
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshotStore
import dev.alllexey.itmowidgets.feature.schedule.ui.widget.ScheduleWidgetProviders.NONE
import dev.alllexey.itmowidgets.feature.schedule.work.ScheduleWidgetWork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * The classic list widget (`Список пар`), the day list of 2.2: placed list widgets kept this class, a stable identifier
 * (ADR 0016), so they stay lists.
 */
class DayScheduleWidgetProvider : AppWidgetProvider(), KoinComponent {

    // Resolved on first use, after onReceive() has started Koin.
    private val store: ScheduleWidgetSnapshotStore by inject()
    private val palettes: WidgetPaletteSource by inject()
    private val time: AcademicTimeProvider by inject()

    override fun onReceive(context: Context, intent: Intent) {
        // The one idempotent starter, as every Android component (KoinStarter); inject() then reads its graph.
        KoinStarter.ensureStarted(context)
        super.onReceive(context, intent)
    }

    override fun onEnabled(context: Context) {
        ScheduleWidgetWork.ensurePeriodic(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        render(context, appWidgetIds, scheduleUpdate = true)
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        render(context, intArrayOf(appWidgetId), scheduleUpdate = false)
    }

    override fun onDisabled(context: Context) {
        ScheduleWidgetWork.cancelIfUnused(context)
    }

    private fun render(context: Context, ids: IntArray, scheduleUpdate: Boolean) {
        val pending = goAsync()
        scope.launch {
            try {
                ScheduleWidgetProviders.render(context, store.read(), palettes.current(), time.now(), singleIds = NONE, dayIds = NONE, listIds = ids)
                // WorkManager toggling its own components re-sends APPWIDGET_UPDATE: only the throttled
                // enqueue and the kept periodic job may follow, or the widget refreshes in a loop.
                if (scheduleUpdate) {
                    ScheduleWidgetWork.ensurePeriodic(context)
                    ScheduleWidgetWork.enqueueUpdate(context)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
