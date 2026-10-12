package dev.alllexey.itmowidgets.core.navigation

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

    const val DAY_SCHEDULE =
        "dev.alllexey.itmowidgets.feature.schedule.ui.widget.DayScheduleWidgetProvider"

    const val QR_CODE =
        "dev.alllexey.itmowidgets.feature.qr.ui.widget.QrCodeWidgetProvider"
}
