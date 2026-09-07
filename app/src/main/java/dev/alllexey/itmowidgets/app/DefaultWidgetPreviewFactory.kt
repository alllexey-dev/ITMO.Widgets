package dev.alllexey.itmowidgets.app

import android.content.Context
import dev.alllexey.itmowidgets.core.settings.WidgetPreviewSettings
import dev.alllexey.itmowidgets.core.ui.widget.WidgetPreview
import dev.alllexey.itmowidgets.core.ui.widget.WidgetPreviewFactory
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrColorResolver
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrPreviewBitmapCache
import dev.alllexey.itmowidgets.feature.qr.ui.widget.QrSettingsPreview
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SchedulePreviewScenario
import dev.alllexey.itmowidgets.feature.schedule.ui.widget.ScheduleSettingsPreview
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Composition belongs at the app boundary; features only see the shared preview contract. */
class DefaultWidgetPreviewFactory @Inject constructor(
    private val images: QrPreviewBitmapCache,
    private val colors: QrColorResolver,
    private val scheduleScenario: SchedulePreviewScenario
) : WidgetPreviewFactory {
    override fun preload(context: Context, scope: CoroutineScope) {
        // Resolve theme colors on the caller thread; background work captures only color ints.
        val palettes = setOf(colors.getQrColors(context, true), colors.getQrColors(context, false))
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

    override fun create(context: Context, scope: CoroutineScope, settings: WidgetPreviewSettings): WidgetPreview =
        when (settings) {
            is WidgetPreviewSettings.Qr -> QrSettingsPreview(context, scope, images, colors)
            is WidgetPreviewSettings.Schedule -> ScheduleSettingsPreview(context, scheduleScenario)
        }.also { it.bind(settings) }
}
