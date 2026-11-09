package dev.alllexey.itmowidgets.feature.finances.domain

import dev.alllexey.itmowidgets.core.payments.Contract
import kotlin.time.Instant

/**
 * The last successful answer from my.itmo and when it came: [contracts] are the student's Договоры об обучении of
 * every academic year, empty for a student on a budget place (my.itmo says tuition is not available to them).
 */
data class TuitionAnswer(val contracts: List<Contract>, val fetchedAt: Instant) {
    /** Something to show: at least one Договор об обучении. */
    val hasAnything: Boolean get() = contracts.isNotEmpty()
}
