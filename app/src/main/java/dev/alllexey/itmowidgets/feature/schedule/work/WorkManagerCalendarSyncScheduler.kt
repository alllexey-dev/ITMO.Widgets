package dev.alllexey.itmowidgets.feature.schedule.work

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
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.CalendarSyncScheduler
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** The sync runs every two hours with a network, like the schedule change check, and as often delayed by Android. */
class WorkManagerCalendarSyncScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context
) : CalendarSyncScheduler {

    override fun ensurePeriodic() {
        val request = PeriodicWorkRequestBuilder<CalendarSyncWorker>(PERIOD_HOURS, TimeUnit.HOURS)
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
            .addTag(TAG)
            .build()
        // UPDATE keeps the enrolment time, so a call on every app start does not push the next run away.
        workManager().enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    override fun runOnce() {
        val request = OneTimeWorkRequestBuilder<CalendarSyncWorker>()
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
        const val PERIODIC_WORK = "calendar-sync"
        const val ONE_OFF_WORK = "calendar-sync-now"
        const val TAG = "calendar-sync"
        const val PERIOD_HOURS = 2L
        private const val BACKOFF_MINUTES = 15L
    }
}
