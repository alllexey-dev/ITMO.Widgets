package dev.alllexey.itmowidgets.core.settings

/**
 * Text size of a schedule widget relative to its layout's own sizes.
 *
 * Widgets cannot follow the app's typography, so a launcher that leaves the widget
 * hard to read gets a per-widget choice instead of a global one.
 */
enum class WidgetTextSize(val scale: Float) {
    NORMAL(1f),
    LARGE(1.2f),
    EXTRA_LARGE(1.4f)
}
