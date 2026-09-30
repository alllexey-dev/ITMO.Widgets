package dev.alllexey.itmowidgets.feature.schedule.data.changes

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangeDigests
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangeNotifier
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** How one background run ended, for the worker to decide on a retry. */
enum class CheckOutcome { SKIPPED, DONE, RETRY }

/** One background run: check the own schedule, then deliver what waits, even when the check failed. */
class ScheduleChangesCheck @Inject constructor(
    private val sessionTokens: SessionTokenStore,
    private val settings: AppSettingsStorage,
    private val repository: ScheduleChangesRepository,
    private val notifier: ScheduleChangeNotifier,
    private val timeProvider: AcademicTimeProvider
) {
    suspend fun run(): CheckOutcome {
        if (!sessionTokens.hasRefreshToken() || !settings.getScheduleChangesEnabled()) return CheckOutcome.SKIPPED
        val result = repository.check()
        // Changes found in the quiet hours wait here for the first run after them, whatever the network does then.
        deliver()
        return when {
            result is AppResult.Failure && result.error != AppError.Unauthorized -> CheckOutcome.RETRY
            else -> CheckOutcome.DONE
        }
    }

    private suspend fun deliver() {
        val decision = ScheduleChangeDigests.decide(repository.observeChanges().first(), timeProvider.now().toLocalDateTime())
        decision.digest?.let(notifier::show)
        if (decision.handled.isNotEmpty()) repository.markNotified(decision.handled)
    }
}
