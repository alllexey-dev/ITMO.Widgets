package dev.alllexey.itmowidgets.core.payments

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * A [Contract] as a feature keeps it in its stored answer, the same for dormitory and tuition: amounts in kopecks,
 * dates as Moscow calendar dates, [academicYear] the first year of the academic year its [start] falls in. The kind
 * is the feature's own, so it is not stored. Number and amounts are personal financial data: never log them.
 */
@Serializable
data class StoredContract(
    val id: String,
    val number: String,
    val start: LocalDate,
    val end: LocalDate,
    val academicYear: Int,
    val active: Boolean,
    val balance: Long,
    val payments: List<StoredPayment> = emptyList(),
) {
    fun toContract(kind: ContractKind) = Contract(
        id = id,
        kind = kind,
        number = number,
        start = start,
        end = end,
        academicYear = AcademicYear(academicYear),
        active = active,
        balance = Money(balance),
        payments = payments.map { it.toPayment() },
    )

    companion object {
        /** A Договор from my.itmo's fields; its academic year comes from its [start], never from a listing period. */
        fun of(
            id: String,
            number: String,
            start: LocalDate,
            end: LocalDate,
            active: Boolean,
            balance: Money,
            payments: List<StoredPayment>,
        ) = StoredContract(
            id = id,
            number = number,
            start = start,
            end = end,
            academicYear = AcademicYear.of(start).startYear,
            active = active,
            balance = balance.kopecks,
            payments = payments,
        )
    }
}

/** A [Payment] as it is stored: [amount] and [paid] in kopecks, [dueDate] a Moscow calendar date. */
@Serializable
data class StoredPayment(val amount: Long, val paid: Long, val dueDate: LocalDate, val period: String) {
    fun toPayment() = Payment(Money(amount), Money(paid), dueDate, period)

    companion object {
        fun of(amount: Money, paid: Money, dueDate: LocalDate, period: String) = StoredPayment(amount.kopecks, paid.kopecks, dueDate, period)
    }
}
