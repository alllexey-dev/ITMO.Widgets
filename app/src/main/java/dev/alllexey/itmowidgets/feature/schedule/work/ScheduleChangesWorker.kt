package dev.alllexey.itmowidgets.feature.schedule.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import dev.alllexey.itmowidgets.core.work.PeriodicCheckSpec
import dev.alllexey.itmowidgets.core.work.workResultOf
import dev.alllexey.itmowidgets.di.bridge.KoinStarter
import dev.alllexey.itmowidgets.feature.schedule.data.changes.ScheduleChangesCheck
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import org.koin.core.Koin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ScheduleChangesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val check: ScheduleChangesCheck by inject()

    /** WorkManager can run a worker before `Application.onCreate()` has started Koin. */
    override fun getKoin(): Koin = KoinStarter.ensureStarted(applicationContext)

    override suspend fun doWork(): Result {
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
