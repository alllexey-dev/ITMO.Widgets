package dev.alllexey.itmowidgets.feature.schedule.data.changes

import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.core.storage.ScheduleCheckPreferences
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.work.CheckOutcome
import dev.alllexey.itmowidgets.core.work.outcomeOf
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangeDigests
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangeNotifier
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangesRepository
import kotlinx.coroutines.flow.first

/** One background run: check the own schedule, then deliver what waits, even when the check failed. */
class ScheduleChangesCheck(
    private val sessionTokens: SessionTokenStore,
    private val scheduleChecks: ScheduleCheckPreferences,
    private val repository: ScheduleChangesRepository,
    private val notifier: ScheduleChangeNotifier,
    private val timeProvider: AcademicTimeProvider
) {
    suspend fun run(): CheckOutcome {
        if (!sessionTokens.hasRefreshToken() || !scheduleChecks.getScheduleChangesEnabled()) return CheckOutcome.SKIPPED
        val result = repository.check()
        // Changes found in the quiet hours wait here for the first run after them, whatever the network does then.
        deliver()
        return outcomeOf(listOfNotNull((result as? AppResult.Failure)?.error))
    }

    private suspend fun deliver() {
        val decision = ScheduleChangeDigests.decide(repository.observeChanges().first(), timeProvider.localNow())
        decision.digest?.let(notifier::show)
        if (decision.handled.isNotEmpty()) repository.markNotified(decision.handled)
    }
}
