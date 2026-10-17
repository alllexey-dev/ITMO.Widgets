package dev.alllexey.itmowidgets.feature.schedule.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.alllexey.itmowidgets.feature.schedule.ui.widget.ScheduleWidgetProviders
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object ScheduleWidgetWork {

    private const val UPDATE_WORK = "dev.alllexey.itmowidgets.ScheduleWidgetUpdate"
    private const val PERIODIC_WORK = "dev.alllexey.itmowidgets.ScheduleWidgetPeriodicUpdate"
    private const val FOLLOW_UP_WORK = "dev.alllexey.itmowidgets.ScheduleWidgetFollowUpUpdate"
    private const val MIN_ENQUEUE_INTERVAL_MILLIS = 60_000L
    private const val NEVER = Long.MIN_VALUE
    private const val PERIODIC_UPDATE_HOURS = 1L
    private val FOLLOW_UP_DELAY: Duration = 1.seconds

    private val lastEnqueuedAt = AtomicLong(NEVER)

    fun ensurePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<ScheduleWidgetUpdateWorker>(
            PERIODIC_UPDATE_HOURS,
            TimeUnit.HOURS
        ).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun enqueueUpdate(context: Context, force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        val previous = lastEnqueuedAt.get()
        val tooSoon = previous != NEVER && now - previous < MIN_ENQUEUE_INTERVAL_MILLIS
        if (!force && tooSoon) return
        lastEnqueuedAt.set(now)

        WorkManager.getInstance(context).enqueueUniqueWork(
            UPDATE_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ScheduleWidgetUpdateWorker>().build()
        )
    }

    /**
     * A second fetch after a remote change: MyITMO can answer the first fetch with the
     * schedule from before the change, and the next automatic update may be an hour away.
     */
    fun enqueueFollowUp(context: Context, delay: Duration = FOLLOW_UP_DELAY) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            FOLLOW_UP_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ScheduleWidgetUpdateWorker>()
                .setInitialDelay(delay.inWholeMilliseconds, TimeUnit.MILLISECONDS)
                .build()
        )
    }

    fun scheduleNext(context: Context, delay: Duration) {
        val delayMillis = delay.inWholeMilliseconds.coerceAtLeast(0L)
        alarmManager(context).setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + delayMillis,
            refreshIntent(context)
        )
    }

    /**
     * The next minute of a running countdown or `сейчас` line: a render from the cache only (no request), on a
     * non-waking alarm, since nobody looks at a sleeping screen.
     */
    fun scheduleTick(context: Context, delay: Duration) {
        alarmManager(context).setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + delay.inWholeMilliseconds.coerceAtLeast(0L),
            tickIntent(context)
        )
    }

    fun cancelTick(context: Context) {
        alarmManager(context).cancel(tickIntent(context))
    }

    fun cancelIfUnused(context: Context) {
        if (ScheduleWidgetProviders.hasAnyWidget(context)) return

        cancelAll(context)
    }

    fun cancelAll(context: Context) {
        alarmManager(context).cancel(refreshIntent(context))
        cancelTick(context)
        WorkManager.getInstance(context).cancelUniqueWork(UPDATE_WORK)
        WorkManager.getInstance(context).cancelUniqueWork(FOLLOW_UP_WORK)
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK)
    }

    private fun alarmManager(context: Context): AlarmManager {
        return context.getSystemService(AlarmManager::class.java)
    }

    private fun refreshIntent(context: Context): PendingIntent = receiverIntent(context, ScheduleWidgetRefreshReceiver.ACTION_REFRESH, 0)

    private fun tickIntent(context: Context): PendingIntent = receiverIntent(context, ScheduleWidgetRefreshReceiver.ACTION_TICK, 1)

    private fun receiverIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, ScheduleWidgetRefreshReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
