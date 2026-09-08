package dev.alllexey.itmowidgets.feature.schedule.ui

import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.widget.ImageView
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.util.color
import dev.alllexey.itmowidgets.core.util.dp

enum class ScheduleTimelineMarker {
    COMPLETED, CURRENT, NEXT, UPCOMING, AUTO_SIGN
}

/** A timeline symbol, not an action icon. The surface masks the line inside open markers. */
fun ImageView.renderTimelineMarker(marker: ScheduleTimelineMarker) {
    val colors = context.color
    val surface = colors.resolve(com.google.android.material.R.attr.colorSurfaceContainerLow)
    val ink = when (marker) {
        ScheduleTimelineMarker.CURRENT, ScheduleTimelineMarker.NEXT -> colors.primary
        else -> colors.outline
    }
    val open = marker in setOf(ScheduleTimelineMarker.UPCOMING, ScheduleTimelineMarker.NEXT, ScheduleTimelineMarker.AUTO_SIGN)
    val backdrop = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(surface)
    }
    val layers = mutableListOf(backdrop)
    val insets = mutableListOf(0)
    if (open) {
        layers += GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(surface)
            setStroke(2.dp, ink)
        }
        insets += 1.dp // A 12 dp ring within the stable 14 dp timeline target.
    }
    if (marker in setOf(ScheduleTimelineMarker.COMPLETED, ScheduleTimelineMarker.CURRENT, ScheduleTimelineMarker.NEXT)) {
        layers += GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ink)
        }
        insets += when (marker) {
            ScheduleTimelineMarker.CURRENT -> 1.dp
            ScheduleTimelineMarker.COMPLETED -> 3.dp
            else -> 5.dp
        }
    }
    val drawable = LayerDrawable(layers.toTypedArray()).apply {
        insets.forEachIndexed { index, inset -> setLayerInset(index, inset, inset, inset, inset) }
    }
    imageTintList = null
    clearColorFilter()
    setImageDrawable(drawable)
    scaleX = 1f
    scaleY = 1f
    contentDescription = when (marker) {
        ScheduleTimelineMarker.COMPLETED -> context.getString(R.string.schedule_timeline_completed)
        ScheduleTimelineMarker.CURRENT -> context.getString(R.string.schedule_timeline_current)
        ScheduleTimelineMarker.NEXT -> context.getString(R.string.schedule_timeline_next)
        ScheduleTimelineMarker.UPCOMING -> context.getString(R.string.schedule_timeline_upcoming)
        // The adjacent status already describes the pending booking to TalkBack.
        ScheduleTimelineMarker.AUTO_SIGN -> null
    }
}
