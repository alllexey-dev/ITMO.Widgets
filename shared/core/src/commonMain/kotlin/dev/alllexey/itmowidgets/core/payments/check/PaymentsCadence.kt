package dev.alllexey.itmowidgets.core.payments.check

import dev.alllexey.itmowidgets.core.payments.Contract
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * How often the check asks my.itmo, part by part. The platforms run it every [PERIOD]; a run asks a part while its
 * stored answer is pending (the part decides what that is) or has something left to pay, and otherwise only [IDLE]
 * after its answer or its last attempt, whichever is later, so something new is found within a day and a part that
 * keeps failing is not asked every run.
 */
object PaymentsCadence {

    val PERIOD: Duration = 3.hours

    val IDLE: Duration = 24.hours

    /** [state] null where the part has no answer yet, [lastAttempt] null before the check ever asked it. */
    fun isDue(state: PaymentsCheckState?, lastAttempt: Instant?, now: Instant): Boolean {
        if (state != null && (state.pending || state.contracts.any(Contract::hasSomethingToPay))) return true
        val last = listOfNotNull(state?.fetchedAt, lastAttempt).maxOrNull() ?: return true
        return now - last >= IDLE
    }
}
