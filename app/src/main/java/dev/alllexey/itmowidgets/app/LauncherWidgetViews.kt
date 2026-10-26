package dev.alllexey.itmowidgets.app

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Build
import android.widget.RemoteViews
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetFormat
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.WidgetFormat
import dev.alllexey.itmowidgets.core.settings.WidgetPalette
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SchedulePreviewMoment
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SchedulePreviewScenario
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshot
import dev.alllexey.itmowidgets.feature.schedule.ui.widget.DayWidgetRenderer
import dev.alllexey.itmowidgets.feature.schedule.ui.widget.NextLessonWidgetRenderer
import dev.alllexey.itmowidgets.feature.schedule.ui.widget.ScheduleListWidgetRenderer
import dev.alllexey.itmowidgets.feature.schedule.ui.widget.ScheduleSettingsPreview
import dev.alllexey.itmowidgets.feature.sport.domain.widget.SportWidgetSampleState
import dev.alllexey.itmowidgets.feature.sport.domain.widget.SportWidgetSamples
import dev.alllexey.itmowidgets.feature.sport.domain.widget.SportWidgetSnapshot
import dev.alllexey.itmowidgets.feature.sport.ui.widget.SportSettingsPreview
import dev.alllexey.itmowidgets.feature.sport.ui.widget.SportWidgetRenderer
import kotlin.time.Instant

/**
 * A widget as the launcher shows it before it is placed (the pin dialog, the Android 15 picker): the real renderers
 * at the default size the gallery draws.
 */
internal object LauncherWidgetViews {

    /** [format]'s widget on [snapshot] at [now]; null for the classic list below Android 12L. */
    fun schedule(context: Context, format: WidgetFormat, snapshot: ScheduleWidgetSnapshot, palette: WidgetPalette?, now: Instant): RemoteViews? =
        when (format) {
            WidgetFormat.NEXT -> NextLessonWidgetRenderer.layout(context, snapshot, palette, now, widgetId = null, medium = true)
            WidgetFormat.DAY -> DayWidgetRenderer.layout(context, snapshot, palette, ScheduleSettingsPreview.size(ScheduleWidgetFormat.FULL), widgetId = null)
            // Inline rows from 12L; below it RemoteViewsCompat keeps them for a placed widget's id in its own service.
            WidgetFormat.LIST -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2) {
                ScheduleListWidgetRenderer.views(
                    context, snapshot, palette, AppWidgetManager.INVALID_APPWIDGET_ID, listOf(ScheduleSettingsPreview.size(ScheduleWidgetFormat.LIST))
                )
            } else null
            WidgetFormat.SPORT, WidgetFormat.PASS -> error("$format is not a schedule widget")
        }

    /** [format]'s widget on the gallery's sample Monday at 12:22 with [settings]. */
    fun scheduleSample(
        context: Context,
        format: WidgetFormat,
        scenario: SchedulePreviewScenario,
        settings: ScheduleWidgetSettings,
        palette: WidgetPalette?,
    ): RemoteViews? {
        val moment = SchedulePreviewMoment.LESSON
        val snapshot = scenario.snapshot(settings, moment, ScheduleSettingsPreview.labels(context))
        return schedule(context, format, snapshot, palette, scenario.instant(moment))
    }

    /** The small sport widget (its default size) on [snapshot]. */
    fun sport(context: Context, snapshot: SportWidgetSnapshot, palette: WidgetPalette?): RemoteViews =
        SportWidgetRenderer.layout(context, snapshot, palette, widgetId = null, medium = false)

    /** The sport widget on the gallery's sample. */
    fun sportSample(context: Context, palette: WidgetPalette?): RemoteViews =
        sport(context, SportWidgetSamples.snapshot(SportWidgetSampleState.QUEUE, SportSettingsPreview.labels(context)), palette)
}
