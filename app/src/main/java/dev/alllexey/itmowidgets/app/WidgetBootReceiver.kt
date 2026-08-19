package dev.alllexey.itmowidgets.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.alllexey.itmowidgets.feature.qr.ui.widget.QrCodeWidgetProvider
import dev.alllexey.itmowidgets.feature.qr.work.QrWidgetWork
import dev.alllexey.itmowidgets.feature.schedule.ui.widget.ScheduleWidgetProviders
import dev.alllexey.itmowidgets.feature.schedule.work.ScheduleWidgetWork

class WidgetBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        if (QrCodeWidgetProvider.widgetIds(context).isNotEmpty()) {
            QrWidgetWork.enqueueUpdate(context, force = true)
        }
        if (ScheduleWidgetProviders.hasAnyWidget(context)) {
            ScheduleWidgetWork.ensurePeriodic(context)
            ScheduleWidgetWork.enqueueUpdate(context, force = true)
        }
    }
}
