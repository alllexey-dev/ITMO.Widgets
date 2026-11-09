package dev.alllexey.itmowidgets.core.payments.check

import dev.alllexey.itmowidgets.core.payments.Contract
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * How often the check asks my.itmo. The platforms run it every [PERIOD]; a run asks while any part has an Этап
 * общежития or something left to pay, so the short window after `Место предоставлено` is not missed, and otherwise
 * only once the oldest answer is [IDLE] old, so a status that appears is found within a day.
 */
object PaymentsCadence {

    val PERIOD: Duration = 3.hours

    val IDLE: Duration = 24.hours

    /** [states] one per part, null where a part has no answer yet. */
    fun isDue(states: List<PaymentsCheckState?>, now: Instant): Boolean = states.any { state ->
        state == null || state.inProgress || state.contracts.any(Contract::hasSomethingToPay) || now - state.fetchedAt >= IDLE
    }
}
