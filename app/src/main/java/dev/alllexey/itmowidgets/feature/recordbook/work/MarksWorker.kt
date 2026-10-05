package dev.alllexey.itmowidgets.feature.recordbook.work

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
import dev.alllexey.itmowidgets.feature.recordbook.data.marks.MarksCheck
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** Workers are built by WorkManager; see `QrWidgetEntryPoint` for why this is not `@HiltWorker`. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface MarksEntryPoint {
    fun marksCheck(): MarksCheck
}

class MarksWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val check = EntryPointAccessors.fromApplication(applicationContext, MarksEntryPoint::class.java).marksCheck()
        // CancellationException propagates: WorkManager stopped the run and owns what happens next.
        return workResultOf(check.run(), runAttemptCount)
    }
}

/**
 * One check of both sources every three hours with a network. The names go to `enqueueUniquePeriodicWork` and
 * `enqueueUniqueWork` and are stable identifiers.
 */
val MARKS_SPEC = PeriodicCheckSpec(
    worker = MarksWorker::class,
    periodicWork = "marks-check",
    oneOffWork = "marks-check-now",
    tag = "marks",
    period = 3.hours,
    backoff = 15.minutes
)
