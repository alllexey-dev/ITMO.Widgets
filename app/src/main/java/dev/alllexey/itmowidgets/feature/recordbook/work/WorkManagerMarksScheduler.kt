package dev.alllexey.itmowidgets.feature.recordbook.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarksScheduler
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * One check of both sources every three hours with a network. Android picks the moment: Doze, App Standby and vendor
 * limits can delay it by hours or stop it for an app that is not exempt from battery optimisation.
 */
class WorkManagerMarksScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context
) : MarksScheduler {

    override fun ensurePeriodic() {
        val request = PeriodicWorkRequestBuilder<MarksWorker>(PERIOD_HOURS, TimeUnit.HOURS)
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
            .addTag(TAG)
            .build()
        // UPDATE keeps the enrolment time, so a call on every app start does not push the next run away.
        workManager().enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    override fun runOnce() {
        val request = OneTimeWorkRequestBuilder<MarksWorker>()
            .setConstraints(networkConstraints())
            .addTag(TAG)
            .build()
        workManager().enqueueUniqueWork(ONE_OFF_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel() {
        workManager().cancelUniqueWork(PERIODIC_WORK)
        workManager().cancelUniqueWork(ONE_OFF_WORK)
    }

    private fun networkConstraints() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    private fun workManager() = WorkManager.getInstance(context)

    companion object {
        const val PERIODIC_WORK = "marks-check"
        const val ONE_OFF_WORK = "marks-check-now"
        const val TAG = "marks"
        const val PERIOD_HOURS = 3L
        private const val BACKOFF_MINUTES = 15L
    }
}
