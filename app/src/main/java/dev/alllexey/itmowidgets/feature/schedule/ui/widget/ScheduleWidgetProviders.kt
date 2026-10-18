package dev.alllexey.itmowidgets.feature.schedule.ui.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import dev.alllexey.itmowidgets.core.settings.WidgetPalette
import dev.alllexey.itmowidgets.core.ui.widget.widgetSizes
import dev.alllexey.itmowidgets.core.ui.widget.widgetWidth
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshot
import kotlin.time.Instant

/** The placed next-lesson, day and classic list widgets, and one render of all of them. */
object ScheduleWidgetProviders {

    /** No widget of a kind, for a render of the others. */
    val NONE = IntArray(0)

    fun singleLessonIds(context: Context): IntArray = ids(context, SingleLessonWidgetProvider::class.java)

    fun dayIds(context: Context): IntArray = ids(context, DayTimelineWidgetProvider::class.java)

    fun listIds(context: Context): IntArray = ids(context, DayScheduleWidgetProvider::class.java)

    fun hasAnyWidget(context: Context): Boolean =
        singleLessonIds(context).isNotEmpty() || dayIds(context).isNotEmpty() || listIds(context).isNotEmpty()

    /** Draws [snapshot] at [now] on [singleIds], [dayIds] and [listIds], every placed widget by default. */
    fun render(
        context: Context,
        snapshot: ScheduleWidgetSnapshot,
        palette: WidgetPalette?,
        now: Instant,
        singleIds: IntArray = singleLessonIds(context),
        dayIds: IntArray = dayIds(context),
        listIds: IntArray = listIds(context),
    ) {
        val manager = AppWidgetManager.getInstance(context)
        singleIds.forEach { id ->
            manager.updateAppWidget(id, NextLessonWidgetRenderer.views(context, snapshot, palette, now, id, manager.widgetWidth(id)))
        }
        dayIds.forEach { id ->
            manager.updateAppWidget(id, DayWidgetRenderer.views(context, snapshot, palette, manager.widgetSizes(id), id))
        }
        listIds.forEach { id ->
            manager.updateAppWidget(id, ScheduleListWidgetRenderer.views(context, snapshot, palette, id, manager.widgetSizes(id)))
        }
    }

    private fun ids(context: Context, provider: Class<*>): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, provider))
}
