package me.alllexey123.itmowidgets.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import me.alllexey123.itmowidgets.workers.LessonWidgetUpdateWorker
import me.alllexey123.itmowidgets.workers.QrWidgetUpdateWorker

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            LessonWidgetUpdateWorker.Companion.enqueueImmediateUpdate(context)
            QrWidgetUpdateWorker.Companion.enqueueImmediateUpdate(context)
        }
    }
}