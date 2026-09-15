package dev.alllexey.itmowidgets.feature.update.domain

import dev.alllexey.itmowidgets.core.time.WallClock
import java.time.Clock
import java.time.Duration
import javax.inject.Inject

/**
 * The update worth interrupting the user with, or null.
 *
 * Returning an update also records that it was shown: the offer is rate-limited
 * by when it last appeared, not by which button closed it, so backing out of the
 * screen postpones it exactly like «Напомнить позже» does.
 *
 * An unsupported build ignores both the skip and the interval — there is nothing
 * left to postpone it to.
 */
class PendingAppUpdate @Inject constructor(
    private val repository: AppUpdateRepository,
    @param:WallClock private val clock: Clock
) {

    suspend operator fun invoke(): AppUpdate? {
        val update = repository.loadUpdate() ?: return null
        if (!update.unsupported && !shouldOffer(update)) return null
        repository.markNotified()
        return update
    }

    private suspend fun shouldOffer(update: AppUpdate): Boolean {
        val reminder = repository.reminder()
        if (update.latest <= reminder.skippedVersion) return false
        return Duration.between(reminder.notifiedAt, clock.instant()) >= REMINDER_INTERVAL
    }

    private companion object {
        val REMINDER_INTERVAL: Duration = Duration.ofDays(1)
    }
}
