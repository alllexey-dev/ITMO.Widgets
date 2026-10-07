package dev.alllexey.itmowidgets.feature.schedule.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import dev.alllexey.itmowidgets.core.work.PeriodicCheckSpec
import dev.alllexey.itmowidgets.core.work.workResultOf
import dev.alllexey.itmowidgets.di.bridge.KoinStarter
import dev.alllexey.itmowidgets.feature.schedule.data.calendar.DefaultCalendarSync
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import org.koin.core.Koin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class CalendarSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val sync: DefaultCalendarSync by inject()

    /** WorkManager can run a worker before `Application.onCreate()` has started Koin. */
    override fun getKoin(): Koin = KoinStarter.ensureStarted(applicationContext)

    override suspend fun doWork(): Result {
        // CancellationException propagates: WorkManager stopped the run and owns what happens next.
        return workResultOf(sync.run(), runAttemptCount)
    }
}

/**
 * The sync runs every two hours with a network, like the schedule change check. The names go to
 * `enqueueUniquePeriodicWork` and `enqueueUniqueWork` and are stable identifiers.
 */
val CALENDAR_SYNC_SPEC = PeriodicCheckSpec(
    worker = CalendarSyncWorker::class,
    periodicWork = "calendar-sync",
    oneOffWork = "calendar-sync-now",
    tag = "calendar-sync",
    period = 2.hours,
    backoff = 15.minutes
)
