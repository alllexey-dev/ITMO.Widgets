package dev.alllexey.itmowidgets.app

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.annotation.RequiresApi
import dev.alllexey.itmowidgets.core.navigation.WidgetProviders
import dev.alllexey.itmowidgets.core.settings.WidgetFormat
import dev.alllexey.itmowidgets.core.settings.WidgetPaletteSource
import dev.alllexey.itmowidgets.core.storage.DeviceHintPreferences
import dev.alllexey.itmowidgets.core.storage.WidgetSettingsPreferences
import dev.alllexey.itmowidgets.core.ui.widget.inSystemNightMode
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SchedulePreviewScenario
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The widget picker's previews on Android 15+ (`AppWidgetManager.setWidgetPreview`): the gallery's sample drawn with
 * the widget settings and theme of the moment in the system's mode, in place of the static images older versions
 * keep. The system rate-limits the call, so [publish] gives them only when an input changed since the system last took
 * them (an install or update, a widget setting, the theme, the system's mode); a refused call is retried at the next
 * [publish]. The pass keeps its static image.
 */
class WidgetPickerPreviews(
    context: Context,
    private val scenario: SchedulePreviewScenario,
    private val palettes: WidgetPaletteSource,
    private val widgetSettings: WidgetSettingsPreferences,
    private val hints: DeviceHintPreferences,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private var pending: Job? = null

    /** After [SETTLE_MILLIS] without another call, so a run of setting changes costs one call per widget. */
    fun publish() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        pending?.cancel()
        pending = scope.launch {
            delay(SETTLE_MILLIS)
            publishNow()
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    internal suspend fun publishNow() {
        val context = appContext.inSystemNightMode()
        val palette = palettes.current()
        val settings = widgetSettings.getScheduleWidgetSettings()
        val installed = appContext.packageManager.getPackageInfo(appContext.packageName, PackageManager.PackageInfoFlags.of(0)).lastUpdateTime
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        // toString, not hashCode: an enum's hash differs between processes.
        val inputs = listOf(installed, night, palette, settings).joinToString("|")
        if (hints.widgetPickerPreviews() == inputs) return
        val manager = AppWidgetManager.getInstance(appContext)
        val previews = SCHEDULE_FORMATS.associateWith { LauncherWidgetViews.scheduleSample(context, it, scenario, settings, palette) } +
            (WidgetFormat.SPORT to LauncherWidgetViews.sportSample(context, palette))
        val published = previews.map { (format, views) ->
            val provider = ComponentName(appContext, WidgetProviders.of(format))
            // False when rate-limited; a launcher that cannot take the views throws.
            views != null && runCatching {
                manager.setWidgetPreview(provider, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN, views)
            }.getOrDefault(false)
        }
        if (published.all { it }) hints.setWidgetPickerPreviews(inputs)
    }

    private companion object {
        const val SETTLE_MILLIS = 2_000L
        val SCHEDULE_FORMATS = listOf(WidgetFormat.NEXT, WidgetFormat.DAY, WidgetFormat.CARDS, WidgetFormat.LIST)
    }
}
