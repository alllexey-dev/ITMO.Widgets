package dev.alllexey.itmowidgets.app

import android.content.Context
import dev.alllexey.itmowidgets.core.settings.WidgetPreviewSettings
import dev.alllexey.itmowidgets.core.ui.widget.WidgetPreview
import dev.alllexey.itmowidgets.core.ui.widget.WidgetPreviewFactory
import dev.alllexey.itmowidgets.core.ui.widget.inSystemNightMode
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrColorResolver
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrPreviewBitmapCache
import dev.alllexey.itmowidgets.feature.qr.ui.widget.QrSettingsPreview
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SchedulePreviewScenario
import dev.alllexey.itmowidgets.feature.schedule.ui.widget.ScheduleSettingsPreview
import dev.alllexey.itmowidgets.feature.sport.ui.widget.SportSettingsPreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Composition belongs at the app boundary; features only see the shared preview contract. Every preview draws in the
 * system's light or dark mode, as the launcher draws the placed widget, not in the mode the app's `Тема` forces.
 */
class DefaultWidgetPreviewFactory(
    private val images: QrPreviewBitmapCache,
    private val colors: QrColorResolver,
    private val scheduleScenario: SchedulePreviewScenario
) : WidgetPreviewFactory {
    override fun preload(context: Context, scope: CoroutineScope) {
        val system = context.inSystemNightMode()
        // Resolve theme colors on the caller thread; background work captures only color ints.
        val palettes = setOf(colors.getQrColors(system, true), colors.getQrColors(system, false))
        scope.launch {
            try {
                palettes.forEach { images.load(it) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Optional warm-up: the visible preview can retry and render a safe error.
            }
        }
    }

    override fun create(context: Context, scope: CoroutineScope, settings: WidgetPreviewSettings): WidgetPreview {
        val system = context.inSystemNightMode()
        return when (settings) {
            is WidgetPreviewSettings.Qr -> QrSettingsPreview(system, scope, images, colors)
            is WidgetPreviewSettings.Schedule -> ScheduleSettingsPreview(system, scheduleScenario)
            is WidgetPreviewSettings.Sport -> SportSettingsPreview(system)
        }.also { it.bind(settings) }
    }
}
