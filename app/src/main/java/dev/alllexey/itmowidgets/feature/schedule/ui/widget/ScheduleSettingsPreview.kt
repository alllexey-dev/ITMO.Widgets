package dev.alllexey.itmowidgets.feature.schedule.ui.widget

import android.content.Context
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetFormat
import dev.alllexey.itmowidgets.core.settings.WidgetPreviewSettings
import dev.alllexey.itmowidgets.core.ui.widget.WidgetPreview
import dev.alllexey.itmowidgets.designsystem.theme.widgetPalette
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SchedulePreviewLabels
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SchedulePreviewMoment
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SchedulePreviewScenario
import kotlin.math.roundToInt

/**
 * The real next-lesson, day or classic list widget on the sample Monday at 12:22 ([SchedulePreviewScenario]), with the settings
 * being edited: the widgets gallery and its sheets draw it. No host, no PendingIntents, no snapshot written.
 */
class ScheduleSettingsPreview(
    private val context: Context,
    private val scenario: SchedulePreviewScenario,
) : WidgetPreview {
    private val frame = FrameLayout(context)
    override val view: View = frame
    private var settings: WidgetPreviewSettings.Schedule? = null

    override fun bind(settings: WidgetPreviewSettings) {
        val next = settings as WidgetPreviewSettings.Schedule
        if (next == this.settings) return
        this.settings = next
        if (next.format == ScheduleWidgetFormat.LIST) {
            frame.removeAllViews()
            frame.addView(view(context, scenario, next))
        } else {
            val remote = views(context, scenario, next)
            val existing = frame.getChildAt(0)
            if (existing != null && existing.getTag(R.id.widget_preview_layout) == remote.layoutId) {
                remote.reapply(context, existing)
            } else {
                frame.removeAllViews()
                frame.addView(remote.apply(context, frame).apply { setTag(R.id.widget_preview_layout, remote.layoutId) })
            }
        }
        val height = when (next.format) {
            ScheduleWidgetFormat.COMPACT -> NEXT_HEIGHT_DP
            ScheduleWidgetFormat.FULL -> DAY_HEIGHT_DP
            ScheduleWidgetFormat.LIST -> LIST_HEIGHT_DP
        }
        frame.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(height))
    }

    override fun close() {
        frame.removeAllViews()
        settings = null
    }

    private fun dp(value: Float) = (value * context.resources.displayMetrics.density).roundToInt()

    companion object {
        const val NEXT_HEIGHT_DP = 180f
        const val DAY_HEIGHT_DP = 330f
        const val LIST_HEIGHT_DP = 250f

        fun labels(context: Context) = SchedulePreviewLabels(
            math = context.getString(R.string.widget_preview_subject_math),
            physics = context.getString(R.string.widget_preview_subject_physics),
            programming = context.getString(R.string.widget_preview_subject_programming),
            english = context.getString(R.string.widget_preview_subject_english),
            swimming = context.getString(R.string.widget_preview_subject_swimming),
            algorithms = context.getString(R.string.widget_preview_subject_algorithms),
            history = context.getString(R.string.widget_preview_subject_history),
            teacher = context.getString(R.string.widget_preview_teacher),
        )

        /**
         * The widget [settings] describe on the sample day as a view: the classic list draws its rows through a local
         * adapter (a collection needs a host), the others are [views] applied.
         */
        fun view(
            context: Context,
            scenario: SchedulePreviewScenario,
            settings: WidgetPreviewSettings.Schedule,
            moment: SchedulePreviewMoment = SchedulePreviewMoment.LESSON,
        ): View {
            if (settings.format != ScheduleWidgetFormat.LIST) return views(context, scenario, settings, moment).apply(context, FrameLayout(context))
            val snapshot = scenario.snapshot(settings.appearance, moment, labels(context))
            return ScheduleListWidgetRenderer.previewView(context, snapshot, settings.theme?.widgetPalette(context))
        }

        /** The widget [settings] describe on the sample day, drawn as the launcher would at its default size. */
        fun views(
            context: Context,
            scenario: SchedulePreviewScenario,
            settings: WidgetPreviewSettings.Schedule,
            moment: SchedulePreviewMoment = SchedulePreviewMoment.LESSON,
        ): RemoteViews {
            val snapshot = scenario.snapshot(settings.appearance, moment, labels(context))
            val palette = settings.theme?.widgetPalette(context)
            return when (settings.format) {
                ScheduleWidgetFormat.COMPACT ->
                    NextLessonWidgetRenderer.layout(context, snapshot, palette, scenario.instant(moment), widgetId = null, medium = true)
                ScheduleWidgetFormat.FULL ->
                    DayWidgetRenderer.layout(context, snapshot, palette, SizeF(DAY_WIDTH_DP, DAY_HEIGHT_DP), widgetId = null)
                ScheduleWidgetFormat.LIST -> error("The classic list is a collection: draw it with view()")
            }
        }

        private const val DAY_WIDTH_DP = 320f
    }
}
