package dev.alllexey.itmowidgets.feature.schedule.ui.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import dev.alllexey.itmowidgets.core.settings.WidgetPalette
import dev.alllexey.itmowidgets.core.ui.widget.widgetSizes
import dev.alllexey.itmowidgets.core.ui.widget.widgetWidth
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshot
import kotlin.time.Instant

/** The placed next-lesson and day widgets, and one render of all of them. */
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

    /** Draws [snapshot] at [now] on [singleIds] and [dayIds], every placed widget by default. */
    fun render(
        context: Context,
        snapshot: ScheduleWidgetSnapshot,
        palette: WidgetPalette?,
        now: Instant,
        singleIds: IntArray = singleLessonIds(context),
        dayIds: IntArray = dayScheduleIds(context),
    ) {
        val manager = AppWidgetManager.getInstance(context)
        singleIds.forEach { id ->
            manager.updateAppWidget(id, NextLessonWidgetRenderer.views(context, snapshot, palette, now, id, manager.widgetWidth(id)))
        }
        dayIds.forEach { id ->
            manager.updateAppWidget(id, DayWidgetRenderer.views(context, snapshot, palette, manager.widgetSizes(id), id))
        }
    }
}
