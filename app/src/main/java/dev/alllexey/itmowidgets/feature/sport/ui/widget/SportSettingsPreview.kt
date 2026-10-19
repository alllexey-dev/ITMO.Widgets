package dev.alllexey.itmowidgets.feature.sport.ui.widget

import android.content.Context
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.RemoteViews
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.settings.WidgetPreviewSettings
import dev.alllexey.itmowidgets.core.ui.widget.WidgetPreview
import dev.alllexey.itmowidgets.core.ui.widget.WidgetPreviewFrame
import dev.alllexey.itmowidgets.designsystem.theme.widgetPalette
import dev.alllexey.itmowidgets.feature.sport.domain.widget.SportWidgetSampleLabels
import dev.alllexey.itmowidgets.feature.sport.domain.widget.SportWidgetSampleState
import dev.alllexey.itmowidgets.feature.sport.domain.widget.SportWidgetSamples

/** The real small sport widget on its sample (`SportWidgetSamples`), for the widgets gallery. */
class SportSettingsPreview(private val context: Context) : WidgetPreview {
    private val frame = WidgetPreviewFrame(context).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        size = SizeF(SIZE_DP, SIZE_DP)
    }
    override val view: View = frame

    override fun bind(settings: WidgetPreviewSettings) {
        val remote = views(context, settings as WidgetPreviewSettings.Sport, medium = false)
        frame.removeAllViews()
        frame.addView(remote.apply(context, frame))
    }

    override fun close() = frame.removeAllViews()

    companion object {
        /** The small widget's 2 x 2 cells. */
        const val SIZE_DP = 150f

        fun labels(context: Context) = SportWidgetSampleLabels(
            swimming = context.getString(R.string.widget_preview_sport_swimming),
            volleyball = context.getString(R.string.widget_preview_sport_volleyball),
        )

        fun views(
            context: Context,
            settings: WidgetPreviewSettings.Sport,
            medium: Boolean,
            state: SportWidgetSampleState = SportWidgetSampleState.QUEUE,
        ): RemoteViews = SportWidgetRenderer.layout(
            context,
            SportWidgetSamples.snapshot(state, labels(context)),
            settings.theme?.widgetPalette(context),
            widgetId = null,
            medium = medium,
        )
    }
}
