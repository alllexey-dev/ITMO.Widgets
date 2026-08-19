package dev.alllexey.itmowidgets.feature.schedule.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ScheduleWidgetRefreshReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REFRESH) return
        ScheduleWidgetWork.ensurePeriodic(context)
        ScheduleWidgetWork.enqueueUpdate(context, force = true)
    }

    companion object {
        const val ACTION_REFRESH =
            "dev.alllexey.itmowidgets.action.SCHEDULE_WIDGET_REFRESH"
    }
}
