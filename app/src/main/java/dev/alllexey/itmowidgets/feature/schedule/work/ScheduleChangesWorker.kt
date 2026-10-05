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
import dev.alllexey.itmowidgets.feature.schedule.data.changes.ScheduleChangesCheck
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** Workers are built by WorkManager; see `QrWidgetEntryPoint` for why this is not `@HiltWorker`. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ScheduleChangesEntryPoint {
    fun check(): ScheduleChangesCheck
}

class ScheduleChangesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val check = EntryPointAccessors.fromApplication(applicationContext, ScheduleChangesEntryPoint::class.java).check()
        // CancellationException propagates: WorkManager stopped the run and owns what happens next.
        return workResultOf(check.run(), runAttemptCount)
    }
}

/**
 * The check runs every two hours with a network. The names go to `enqueueUniquePeriodicWork` and
 * `enqueueUniqueWork` and are stable identifiers.
 */
val SCHEDULE_CHANGES_SPEC = PeriodicCheckSpec(
    worker = ScheduleChangesWorker::class,
    periodicWork = "schedule-changes-check",
    oneOffWork = "schedule-changes-now",
    tag = "schedule-changes",
    period = 2.hours,
    backoff = 15.minutes
)
