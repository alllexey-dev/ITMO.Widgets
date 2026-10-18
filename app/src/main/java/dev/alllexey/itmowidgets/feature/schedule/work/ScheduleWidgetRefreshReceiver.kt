package dev.alllexey.itmowidgets.feature.schedule.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.alllexey.itmowidgets.di.KoinStarter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** The schedule widgets' alarms: [ACTION_REFRESH] at a boundary (a fetch through the worker), [ACTION_TICK] a minute. */
class ScheduleWidgetRefreshReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REFRESH -> {
                ScheduleWidgetWork.ensurePeriodic(context)
                ScheduleWidgetWork.enqueueUpdate(context, force = true)
            }
            ACTION_TICK -> {
                val pending = goAsync()
                scope.launch {
                    try {
                        ScheduleWidgetUpdate(KoinStarter.ensureStarted(context), context.applicationContext).run(refresh = false)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "dev.alllexey.itmowidgets.action.SCHEDULE_WIDGET_REFRESH"
        const val ACTION_TICK = "dev.alllexey.itmowidgets.action.SCHEDULE_WIDGET_TICK"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
