package dev.alllexey.itmowidgets.feature.settings.data

import dev.alllexey.itmowidgets.core.storage.WidgetReloader
import dev.alllexey.itmowidgets.feature.settings.domain.WidgetRefreshRequester

/**
 * Asks WidgetKit to redraw every widget kind of the app. The widgets read their App Group snapshots, which the app
 * keeps current, so a reload is the whole refresh on iOS.
 */
class IosWidgetRefreshRequester(private val reloader: WidgetReloader) : WidgetRefreshRequester {

    override fun refreshAll() = WIDGET_KINDS.forEach(reloader::reload)

    companion object {
        /** The kinds of the widget extension (`StableIdentifiersTests`). */
        val WIDGET_KINDS = listOf(
            "dev.alllexey.itmowidgets.widget.qr",
            "dev.alllexey.itmowidgets.widget.single-lesson",
            "dev.alllexey.itmowidgets.widget.day-schedule",
        )
    }
}
