package dev.alllexey.itmowidgets.feature.schedule.data.changes

import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeTracking
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangesRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangesScheduler
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** The switch and the session decide whether the periodic check exists; off also forgets the snapshot. */
@Singleton
class DefaultScheduleChangeTracking @Inject constructor(
    private val settings: AppSettingsStorage,
    private val sessionTokens: SessionTokenStore,
    private val scheduler: ScheduleChangesScheduler,
    private val repository: ScheduleChangesRepository
) : ScheduleChangeTracking {

    override fun observeEnabled(): Flow<Boolean> = settings.observeScheduleChangesEnabled()

    override suspend fun setEnabled(enabled: Boolean) {
        settings.setScheduleChangesEnabled(enabled)
        if (enabled) {
            syncWork()
        } else {
            scheduler.cancel()
            // The next time the switch is on, the first check is a baseline again, not a flood of old changes.
            repository.resetSnapshot()
        }
    }

    override suspend fun syncWork() {
        if (sessionTokens.hasRefreshToken() && settings.getScheduleChangesEnabled()) scheduler.ensurePeriodic()
        else scheduler.cancel()
    }

    override fun stopWork() = scheduler.cancel()

    override fun checkNow() = scheduler.runOnce()
}
