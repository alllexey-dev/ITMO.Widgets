package dev.alllexey.itmowidgets.feature.sport.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import dev.alllexey.itmowidgets.core.settings.WidgetPaletteSource
import dev.alllexey.itmowidgets.di.KoinStarter
import dev.alllexey.itmowidgets.feature.sport.data.widget.SportWidgetSnapshotStore
import dev.alllexey.itmowidgets.feature.sport.work.SportWidgetWork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** The sport widget (W3); the class name is a stable identifier of placed widgets. */
class SportWidgetProvider : AppWidgetProvider(), KoinComponent {

    private val store: SportWidgetSnapshotStore by inject()
    private val palettes: WidgetPaletteSource by inject()

    override fun onReceive(context: Context, intent: Intent) {
        KoinStarter.ensureStarted(context)
        super.onReceive(context, intent)
    }

    override fun onEnabled(context: Context) {
        SportWidgetWork.ensurePeriodic(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        render(context, appWidgetIds, update = true)
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        render(context, intArrayOf(appWidgetId), update = false)
    }

    override fun onDisabled(context: Context) {
        SportWidgetWork.cancelAll(context)
    }

    private fun render(context: Context, ids: IntArray, update: Boolean) {
        val pending = goAsync()
        scope.launch {
            try {
                SportWidgetWork.render(context, store.read(), palettes.current(), ids)
                if (update) {
                    SportWidgetWork.ensurePeriodic(context)
                    SportWidgetWork.enqueueUpdate(context)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
