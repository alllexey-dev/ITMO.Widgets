package dev.alllexey.itmowidgets.core.ui.widget

import android.appwidget.AppWidgetManager
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import androidx.core.os.BundleCompat

/** The sizes in dp a launcher draws a widget in: its list from API 31, else portrait and landscape from the options. */
fun AppWidgetManager.widgetSizes(widgetId: Int): List<SizeF> {
    val options = getAppWidgetOptions(widgetId) ?: return emptyList()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        BundleCompat.getParcelableArrayList(options, AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }
    }
    val minWidth = options.dp(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
    val maxHeight = options.dp(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
    if (minWidth == 0f || maxHeight == 0f) return emptyList()
    return listOf(
        SizeF(minWidth, maxHeight),
        SizeF(options.dp(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH), options.dp(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)),
    )
}

/** The narrowest width the launcher may draw a widget in, 0 when it does not say. */
fun AppWidgetManager.widgetWidth(widgetId: Int): Float = widgetSizes(widgetId).minOfOrNull { it.width } ?: 0f

private fun Bundle.dp(key: String): Float = getInt(key, 0).toFloat()
