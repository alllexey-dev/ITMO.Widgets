package dev.alllexey.itmowidgets.feature.schedule.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.core.work.PeriodicCheckSpec
import dev.alllexey.itmowidgets.core.work.workResultOf
import dev.alllexey.itmowidgets.feature.schedule.data.calendar.DefaultCalendarSync
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** Workers are built by WorkManager; see `QrWidgetEntryPoint` for why this is not `@HiltWorker`. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface CalendarSyncEntryPoint {
    fun calendarSync(): DefaultCalendarSync
}

class CalendarSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val sync = EntryPointAccessors.fromApplication(applicationContext, CalendarSyncEntryPoint::class.java).calendarSync()
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
