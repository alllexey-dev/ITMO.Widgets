package me.alllexey123.itmowidgets.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import me.alllexey123.itmowidgets.WidgetUpdateWorker

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            WidgetUpdateWorker.Companion.enqueueImmediateUpdate(context)
        }
    }
}