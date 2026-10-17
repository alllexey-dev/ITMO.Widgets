package dev.alllexey.itmowidgets.feature.sport.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.alllexey.itmowidgets.core.diagnostics.AppDiagnostics
import dev.alllexey.itmowidgets.core.settings.WidgetPaletteSource
import dev.alllexey.itmowidgets.di.KoinStarter
import dev.alllexey.itmowidgets.feature.sport.data.widget.SportWidgetDataProvider
import dev.alllexey.itmowidgets.feature.sport.data.widget.SportWidgetSnapshotStore
import dev.alllexey.itmowidgets.feature.sport.domain.widget.SportWidgetKind
import dev.alllexey.itmowidgets.feature.sport.domain.widget.SportWidgetSnapshot
import dev.alllexey.itmowidgets.feature.sport.ui.widget.SportWidgetProvider
import dev.alllexey.itmowidgets.feature.sport.ui.widget.SportWidgetRenderer
import dev.alllexey.itmowidgets.core.ui.widget.widgetWidth
import dev.alllexey.itmowidgets.core.settings.WidgetPalette
import android.os.SystemClock
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException

/**
 * The sport widget's updates: hourly, after a sport action and at its next change by itself (a booking ends, a
 * midnight), each a fetch as `Моё` does it; launcher redraws use the stored snapshot.
 */
object SportWidgetWork {
    private const val UPDATE_WORK = "dev.alllexey.itmowidgets.SportWidgetUpdate"
    private const val PERIODIC_WORK = "dev.alllexey.itmowidgets.SportWidgetPeriodicUpdate"
    internal const val ACTION_REFRESH = "dev.alllexey.itmowidgets.action.SPORT_WIDGET_REFRESH"
    private const val PERIODIC_HOURS = 1L
    private const val MIN_ENQUEUE_INTERVAL_MILLIS = 60_000L
    private const val NEVER = Long.MIN_VALUE
    private val lastEnqueuedAt = AtomicLong(NEVER)

    fun ids(context: Context): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, SportWidgetProvider::class.java))

    fun ensurePeriodic(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SportWidgetUpdateWorker>(PERIODIC_HOURS, TimeUnit.HOURS).build()
        )
    }

    /**
     * Once a minute at most unless [force]: WorkManager toggling its own components re-sends APPWIDGET_UPDATE, which
     * must not start another fetch.
     */
    fun enqueueUpdate(context: Context, force: Boolean = false) {
        if (ids(context).isEmpty()) return
        val now = SystemClock.elapsedRealtime()
        val previous = lastEnqueuedAt.get()
        if (!force && previous != NEVER && now - previous < MIN_ENQUEUE_INTERVAL_MILLIS) return
        lastEnqueuedAt.set(now)
        WorkManager.getInstance(context).enqueueUniqueWork(
            UPDATE_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<SportWidgetUpdateWorker>().build()
        )
    }

    fun cancelAll(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(refreshIntent(context))
        WorkManager.getInstance(context).cancelUniqueWork(UPDATE_WORK)
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK)
    }

    fun render(context: Context, snapshot: SportWidgetSnapshot, palette: WidgetPalette?, ids: IntArray = ids(context)) {
        val manager = AppWidgetManager.getInstance(context)
        ids.forEach { id -> manager.updateAppWidget(id, SportWidgetRenderer.views(context, snapshot, palette, id, manager.widgetWidth(id))) }
    }

    internal fun scheduleAt(context: Context, epochMillis: Long) {
        context.getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.RTC, epochMillis, refreshIntent(context))
    }

    private fun refreshIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, SportWidgetRefreshReceiver::class.java).setAction(ACTION_REFRESH),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

/** The alarm at the sport widget's next change. */
class SportWidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == SportWidgetWork.ACTION_REFRESH) SportWidgetWork.enqueueUpdate(context, force = true)
    }
}

class SportWidgetUpdateWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ids = SportWidgetWork.ids(applicationContext)
        if (ids.isEmpty()) return Result.success()
        val koin = KoinStarter.ensureStarted(applicationContext)
        val store = koin.get<SportWidgetSnapshotStore>()
        val generation = store.currentGeneration()
        val load = try {
            koin.get<SportWidgetDataProvider>().load()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            koin.get<AppDiagnostics>().warn(TAG, "Sport widget update failed; keeping the last snapshot", error)
            null
        }
        // A failed fetch keeps a snapshot that still has content.
        val previous = store.read()
        val snapshot = load?.snapshot?.takeUnless { it.kind == SportWidgetKind.ERROR && previous.kind == SportWidgetKind.CONTENT } ?: previous
        if (!store.writeIfCurrent(snapshot, generation)) return Result.success()
        SportWidgetWork.render(applicationContext, store.read(), koin.get<WidgetPaletteSource>().current(), ids)
        load?.nextChange?.let { SportWidgetWork.scheduleAt(applicationContext, it.toEpochMilliseconds()) }
        return Result.success()
    }

    private companion object {
        const val TAG = "SportWidget"
    }
}
