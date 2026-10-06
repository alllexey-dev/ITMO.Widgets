package dev.alllexey.itmowidgets.feature.schedule.data.calendar

import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.CalendarSync
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncResult
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncState
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.core.work.CheckOutcome
import dev.alllexey.itmowidgets.core.work.outcomeOf
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.CalendarSyncRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.CalendarSyncScheduler
import kotlinx.coroutines.flow.Flow

/**
 * The switch and the session decide whether the periodic sync exists. It does not depend on the schedule change
 * check: the two have their own work. One per process (Koin's `scheduleDataModule`); Android's `CalendarSyncWorker`
 * runs [run].
 */
class DefaultCalendarSync(
    private val repository: CalendarSyncRepository,
    private val scheduler: CalendarSyncScheduler,
    private val sessionTokens: SessionTokenStore,
    private val demo: DemoMode
) : CalendarSync {

    override fun observeState(): Flow<CalendarSyncState> = repository.observeState()

    override suspend fun enable(): CalendarSyncResult =
        if (demo.isActive()) CalendarSyncResult.DEMO_UNAVAILABLE else repository.enable().also { result ->
            if (result == CalendarSyncResult.DONE) {
                syncWork()
                if (sessionTokens.hasRefreshToken()) scheduler.runOnce()
            }
        }

    /** Stops a running sync, deletes the app's events and keeps the work while left calendars are swept again. */
    override suspend fun disable() {
        scheduler.cancel()
        repository.disable()
        syncWork()
    }

    override suspend fun syncWork() {
        if (sessionTokens.hasRefreshToken() && needsWork()) scheduler.ensurePeriodic()
        else scheduler.cancel()
    }

    override fun stopWork() = scheduler.cancel()

    private suspend fun needsWork() = repository.isEnabled() || repository.hasPendingCleanup()

    override suspend fun requestSync() {
        if (sessionTokens.hasRefreshToken() && repository.isEnabled()) scheduler.runOnce()
    }

    /** One background run: sweeps left calendars and syncs; with nothing left to do, the work goes. */
    suspend fun run(): CheckOutcome {
        if (!sessionTokens.hasRefreshToken() || !needsWork()) return CheckOutcome.SKIPPED
        val result = repository.sync()
        if (!needsWork()) scheduler.cancel()
        return outcomeOf(listOfNotNull((result as? AppResult.Failure)?.error))
    }
}
