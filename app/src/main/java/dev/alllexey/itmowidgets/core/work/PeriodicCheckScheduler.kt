package dev.alllexey.itmowidgets.core.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass
import kotlin.time.Duration

/**
 * One periodic background check as WorkManager sees it. [periodicWork], [oneOffWork] and [tag] are stable
 * identifiers: WorkManager keeps them across app updates, so a rename needs an ADR and a cancel of the old name.
 */
data class PeriodicCheckSpec(
    val worker: KClass<out ListenableWorker>,
    val periodicWork: String,
    val oneOffWork: String,
    val tag: String,
    val period: Duration,
    val backoff: Duration
)

/**
 * Runs the check of [spec] with a network. Android picks the moment: Doze, App Standby and vendor limits can delay
 * it by hours or stop it for an app that is not exempt from battery optimisation, so nothing here promises prompt
 * delivery.
 */
class PeriodicCheckScheduler(
    private val context: Context,
    private val spec: PeriodicCheckSpec
) : CheckScheduler {

    override fun ensurePeriodic() {
        val request = PeriodicWorkRequest
            .Builder(spec.worker.java, spec.period.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, spec.backoff.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            .addTag(spec.tag)
            .build()
        // UPDATE keeps the enrolment time, so a call on every app start does not push the next run away.
        workManager().enqueueUniquePeriodicWork(spec.periodicWork, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    override fun runOnce() {
        val request = OneTimeWorkRequest.Builder(spec.worker.java)
            .setConstraints(networkConstraints())
            .addTag(spec.tag)
            .build()
        workManager().enqueueUniqueWork(spec.oneOffWork, ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel() {
        workManager().cancelUniqueWork(spec.periodicWork)
        workManager().cancelUniqueWork(spec.oneOffWork)
    }

    private fun networkConstraints() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    private fun workManager() = WorkManager.getInstance(context)
}
