package dev.alllexey.itmowidgets.feature.finances.data

import dev.alllexey.itmowidgets.core.payments.AcademicYear
import dev.alllexey.itmowidgets.core.payments.Contract
import dev.alllexey.itmowidgets.core.payments.ContractKind
import dev.alllexey.itmowidgets.core.payments.Money
import dev.alllexey.itmowidgets.core.payments.Payment
import dev.alllexey.itmowidgets.feature.finances.domain.TuitionAnswer
import dev.alllexey.itmoapi.myitmo.finances.TuitionPaymentContract
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** A Договор об обучении; ITMO's dates are read as calendar dates in [zone], its academic year from its start. */
internal fun TuitionPaymentContract.toStored(zone: TimeZone): StoredContract {
    val start = dateStart.toLocalDateTime(zone).date
    return StoredContract(
        id = contractId,
        number = number,
        start = start,
        end = dateEnd.toLocalDateTime(zone).date,
        academicYear = AcademicYear.of(start).startYear,
        active = active,
        balance = Money.ofRubles(balance).kopecks,
        payments = payments.map {
            StoredPayment(Money.ofRubles(it.sum).kopecks, Money.ofRubles(it.paid).kopecks, it.payUntil.toLocalDateTime(zone).date, it.period)
        },
    )
}

private fun StoredContract.toContract() = Contract(
    id = id,
    kind = ContractKind.TUITION,
    number = number,
    start = start,
    end = end,
    academicYear = AcademicYear(academicYear),
    active = active,
    balance = Money(balance),
    payments = payments.map { Payment(Money(it.amount), Money(it.paid), it.dueDate, it.period) },
)

internal fun StoredTuition.toAnswer() = TuitionAnswer(
    contracts = contracts.map { it.toContract() },
    fetchedAt = Instant.fromEpochMilliseconds(fetchedAt),
)
