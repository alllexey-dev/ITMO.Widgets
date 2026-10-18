package dev.alllexey.itmowidgets.feature.sport.ui.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import dev.alllexey.itmowidgets.core.settings.WidgetPalette
import dev.alllexey.itmowidgets.core.ui.widget.widgetWidth
import dev.alllexey.itmowidgets.feature.sport.domain.widget.SportWidgetSnapshot

/** The placed sport widgets, and one render of all of them. */
object SportWidgetProviders {

    fun ids(context: Context): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, SportWidgetProvider::class.java))

    fun render(context: Context, snapshot: SportWidgetSnapshot, palette: WidgetPalette?, ids: IntArray = ids(context)) {
        val manager = AppWidgetManager.getInstance(context)
        ids.forEach { id -> manager.updateAppWidget(id, SportWidgetRenderer.views(context, snapshot, palette, id, manager.widgetWidth(id))) }
    }
}
