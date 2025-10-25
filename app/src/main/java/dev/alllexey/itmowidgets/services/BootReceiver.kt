package dev.alllexey.itmowidgets.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.alllexey.itmowidgets.ui.widgets.WidgetUtils
import dev.alllexey.itmowidgets.workers.LessonWidgetUpdateWorker
import dev.alllexey.itmowidgets.workers.QrWidgetUpdateWorker

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            WidgetUtils.updateAllWidgets(context)
        }
    }
}