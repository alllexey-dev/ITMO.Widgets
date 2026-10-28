package dev.alllexey.itmowidgets.core.navigation

import dev.alllexey.itmowidgets.core.settings.WidgetFormat

/**
 * Widget providers addressed by class name.
 *
 * A screen that offers to pin a widget lives in its own feature and must not
 * import the feature that owns the provider, so the component is named here and
 * resolved through `ComponentName`. `WidgetProvidersTest` keeps the names honest.
 */
object WidgetProviders {

    const val SINGLE_LESSON =
        "dev.alllexey.itmowidgets.feature.schedule.ui.widget.SingleLessonWidgetProvider"

    /** The classic list (`Список пар`), the day list placed since 2.2. */
    const val DAY_SCHEDULE =
        "dev.alllexey.itmowidgets.feature.schedule.ui.widget.DayScheduleWidgetProvider"

    /** The day to scale (W2). */
    const val DAY_TIMELINE =
        "dev.alllexey.itmowidgets.feature.schedule.ui.widget.DayTimelineWidgetProvider"

    /** The cards, new in 2.3. */
    const val CARDS =
        "dev.alllexey.itmowidgets.feature.schedule.ui.widget.CardsWidgetProvider"

    const val QR_CODE =
        "dev.alllexey.itmowidgets.feature.qr.ui.widget.QrCodeWidgetProvider"

    const val SPORT =
        "dev.alllexey.itmowidgets.feature.sport.ui.widget.SportWidgetProvider"

    /** The provider of each format of the widgets gallery. */
    fun of(format: WidgetFormat): String = when (format) {
        WidgetFormat.NEXT -> SINGLE_LESSON
        WidgetFormat.DAY -> DAY_TIMELINE
        WidgetFormat.CARDS -> CARDS
        WidgetFormat.LIST -> DAY_SCHEDULE
        WidgetFormat.SPORT -> SPORT
        WidgetFormat.PASS -> QR_CODE
    }
}
