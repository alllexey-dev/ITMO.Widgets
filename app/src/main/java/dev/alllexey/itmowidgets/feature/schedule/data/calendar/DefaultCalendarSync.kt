package dev.alllexey.itmowidgets.feature.schedule.data.calendar

import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.CalendarSync
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncResult
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncState
import dev.alllexey.itmowidgets.core.schedule.CalendarTarget
import dev.alllexey.itmowidgets.core.schedule.WritableCalendar
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.core.work.CheckOutcome
import dev.alllexey.itmowidgets.core.work.outcomeOf
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.CalendarSyncRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.CalendarSyncScheduler
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * The switch and the session decide whether the periodic sync exists. It does not depend on the schedule change
 * check: the two have their own work.
 */
@Singleton
class DefaultCalendarSync @Inject constructor(
    private val repository: CalendarSyncRepository,
    private val scheduler: CalendarSyncScheduler,
    private val sessionTokens: SessionTokenStore
) : CalendarSync {

    override fun observeState(): Flow<CalendarSyncState> = repository.observeState()

    override suspend fun enable(target: CalendarTarget): CalendarSyncResult =
        repository.enable(target).also { result ->
            if (result == CalendarSyncResult.DONE) {
                syncWork()
                if (sessionTokens.hasRefreshToken()) scheduler.runOnce()
            }
        }

    override suspend fun disable() {
        scheduler.cancel()
        repository.disable()
    }

    override suspend fun writableCalendars(): List<WritableCalendar>? = repository.writableCalendars()

    override suspend fun syncWork() {
        if (sessionTokens.hasRefreshToken() && repository.isEnabled()) scheduler.ensurePeriodic()
        else scheduler.cancel()
    }

    override fun stopWork() = scheduler.cancel()

    override suspend fun requestSync() {
        if (sessionTokens.hasRefreshToken() && repository.isEnabled()) scheduler.runOnce()
    }

    /** One background run; when synchronization turned itself off, its work goes too. */
    suspend fun run(): CheckOutcome {
        if (!sessionTokens.hasRefreshToken() || !repository.isEnabled()) return CheckOutcome.SKIPPED
        val result = repository.sync()
        if (!repository.isEnabled()) scheduler.cancel()
        return outcomeOf(listOfNotNull((result as? AppResult.Failure)?.error))
    }
}
