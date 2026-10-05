package dev.alllexey.itmowidgets.feature.update.domain

import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * The update worth interrupting the user with, or null.
 *
 * Returning an update also records that it was shown: the offer is rate-limited
 * by when it last appeared, not by which button closed it, so backing out of the
 * screen postpones it exactly like «Напомнить позже» does.
 *
 * An unsupported build ignores both the skip and the interval — there is nothing
 * left to postpone it to.
 *
 * The interval runs on the wall [clock], not the academic time: a debug date
 * override must not hold back or release an offer.
 */
class PendingAppUpdate @Inject constructor(
    private val repository: AppUpdateRepository,
    private val clock: Clock
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
        return clock.now() - reminder.notifiedAt >= REMINDER_INTERVAL
    }

    private companion object {
        val REMINDER_INTERVAL: Duration = 1.days
    }
}
