package dev.alllexey.itmowidgets.app

import android.content.Context
import android.widget.RemoteViews
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.settings.WidgetFormat
import dev.alllexey.itmowidgets.core.settings.WidgetPaletteSource
import dev.alllexey.itmowidgets.core.storage.WidgetSettingsPreferences
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.ui.widget.inSystemNightMode
import dev.alllexey.itmowidgets.feature.qr.ui.widget.QrWidgetImages
import dev.alllexey.itmowidgets.feature.schedule.data.widget.ScheduleWidgetDataProvider
import dev.alllexey.itmowidgets.feature.schedule.data.widget.ScheduleWidgetLoadResult
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SchedulePreviewScenario
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.WidgetContentKind
import dev.alllexey.itmowidgets.feature.sport.domain.widget.SportWidgetKind
import dev.alllexey.itmowidgets.feature.sport.domain.widget.SportWidgetSnapshotStore
import kotlinx.coroutines.withContext

/**
 * The widget the launcher's pin dialog shows for `Добавить`: the user's own data as the placed widget will draw it,
 * the gallery's sample while there is none (signed out, nothing cached, no sport snapshot yet), in the system's mode.
 * Everything is read from the device and starts no request: the schedule from its cache (demo days in a demo
 * session), sport from the widget's last snapshot, the pass as its spoiler.
 */
class WidgetPinPreviews(
    context: Context,
    private val scenario: SchedulePreviewScenario,
    private val schedule: ScheduleWidgetDataProvider,
    private val sport: SportWidgetSnapshotStore,
    private val qrImages: QrWidgetImages,
    private val palettes: WidgetPaletteSource,
    private val time: AcademicTimeProvider,
    private val widgetSettings: WidgetSettingsPreferences,
    private val dispatchers: AppDispatchers,
) {
    private val appContext = context.applicationContext

    /** Null where the launcher keeps the provider's static preview. */
    suspend fun of(format: WidgetFormat): RemoteViews? = withContext(dispatchers.default) {
        val context = appContext.inSystemNightMode()
        val palette = palettes.current()
        when (format) {
            WidgetFormat.NEXT, WidgetFormat.DAY, WidgetFormat.CARDS, WidgetFormat.LIST -> {
                val own = (schedule.load(refresh = false) as? ScheduleWidgetLoadResult.Available)?.selection?.snapshot?.withoutPendingSport()
                val kind = if (format == WidgetFormat.NEXT) own?.next?.kind else own?.day?.kind
                if (own != null && (kind == WidgetContentKind.CONTENT || kind == WidgetContentKind.EMPTY)) {
                    LauncherWidgetViews.schedule(context, format, own, palette, time.now())
                } else {
                    LauncherWidgetViews.scheduleSample(context, format, scenario, widgetSettings.getScheduleWidgetSettings(), palette)
                }
            }
            WidgetFormat.SPORT -> sport.read().takeIf { it.kind == SportWidgetKind.CONTENT }
                ?.let { LauncherWidgetViews.sport(context, it, palette) }
                ?: LauncherWidgetViews.sportSample(context, palette)
            // Under its spoiler whatever the setting: the dialog never shows a pass.
            WidgetFormat.PASS -> RemoteViews(context.packageName, R.layout.widget_qr_code).apply {
                setImageViewBitmap(R.id.qr_code_image, qrImages.spoiler())
            }
        }
    }
}
