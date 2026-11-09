package dev.alllexey.itmowidgets.feature.finances.data

import dev.alllexey.itmowidgets.core.payments.ContractKind
import dev.alllexey.itmowidgets.core.payments.Money
import dev.alllexey.itmowidgets.core.payments.StoredContract
import dev.alllexey.itmowidgets.core.payments.StoredPayment
import dev.alllexey.itmowidgets.feature.finances.domain.TuitionAnswer
import dev.alllexey.itmoapi.myitmo.finances.TuitionPaymentContract
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** A Договор об обучении; ITMO's dates are read as calendar dates in [zone]. */
internal fun TuitionPaymentContract.toStored(zone: TimeZone) = StoredContract.of(
    id = contractId,
    number = number,
    start = dateStart.toLocalDateTime(zone).date,
    end = dateEnd.toLocalDateTime(zone).date,
    active = active,
    balance = Money.ofRubles(balance),
    payments = payments.map {
        StoredPayment.of(Money.ofRubles(it.sum), Money.ofRubles(it.paid), it.payUntil.toLocalDateTime(zone).date, it.period)
    },
)

internal fun StoredTuition.toAnswer() = TuitionAnswer(
    contracts = contracts.map { it.toContract(ContractKind.TUITION) },
    fetchedAt = Instant.fromEpochMilliseconds(fetchedAt),
)
