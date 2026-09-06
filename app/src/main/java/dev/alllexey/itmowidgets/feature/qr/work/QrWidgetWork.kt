package dev.alllexey.itmowidgets.feature.qr.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.atomic.AtomicLong
import dev.alllexey.itmowidgets.feature.qr.ui.widget.QrCodeWidgetProvider

/** Scheduling entry points for the QR widget. */
object QrWidgetWork {

    private const val UPDATE_WORK = "dev.alllexey.itmowidgets.QrWidgetUpdate"

    /** How long a revealed pass stays on screen before it covers itself again. */
    const val AUTO_HIDE_DELAY_MILLIS = 30_000L

    private const val MIN_UPDATE_INTERVAL_MS = 60_000L
    private const val NEVER = Long.MIN_VALUE

    /** Monotonic, so it survives wall-clock changes. */
    private val lastEnqueuedAt = AtomicLong(NEVER)

    /**
     * Runs without a network constraint on purpose: offline the worker still falls
     * back to the cached pass, and waiting for connectivity would leave the widget
     * blank instead.
     *
     * Enqueues are rate limited because running a worker toggles WorkManager's
     * `RescheduleReceiver` through `setComponentEnabledSetting`. That counts as a
     * package change, the system answers it with `APPWIDGET_UPDATE`, and an
     * unconditional enqueue from `onUpdate` would loop forever. Pass [force] for
     * work the user explicitly asked for.
     */
    fun enqueueUpdate(context: Context, force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        val previous = lastEnqueuedAt.get()
        val tooSoon = previous != NEVER && now - previous < MIN_UPDATE_INTERVAL_MS
        if (!force && tooSoon) return
        lastEnqueuedAt.set(now)

        val request = OneTimeWorkRequestBuilder<QrWidgetUpdateWorker>().build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UPDATE_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun scheduleAutoHide(context: Context, appWidgetId: Int) {
        alarmManager(context).setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + AUTO_HIDE_DELAY_MILLIS,
            autoHideIntent(context, appWidgetId)
        )
    }

    fun cancelAutoHide(context: Context, appWidgetId: Int) {
        alarmManager(context).cancel(autoHideIntent(context, appWidgetId))
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UPDATE_WORK)
        QrCodeWidgetProvider.widgetIds(context).forEach { appWidgetId ->
            cancelAutoHide(context, appWidgetId)
        }
    }

    private fun alarmManager(context: Context): AlarmManager {
        return context.getSystemService(AlarmManager::class.java)
    }

    private fun autoHideIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, QrCodeWidgetProvider::class.java).apply {
            action = QrCodeWidgetProvider.ACTION_AUTO_HIDE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        return PendingIntent.getBroadcast(
            context,
            appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
