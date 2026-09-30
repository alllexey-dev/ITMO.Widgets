package dev.alllexey.itmowidgets.feature.schedule.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.feature.schedule.data.changes.CheckOutcome
import dev.alllexey.itmowidgets.feature.schedule.data.changes.ScheduleChangesCheck

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

/** A failed check is retried twice with backoff; after that the next period tries again. */
internal fun workResultOf(outcome: CheckOutcome, attempt: Int): Result =
    if (outcome == CheckOutcome.RETRY && attempt < MAX_RETRIES) Result.retry() else Result.success()

internal const val MAX_RETRIES = 2
