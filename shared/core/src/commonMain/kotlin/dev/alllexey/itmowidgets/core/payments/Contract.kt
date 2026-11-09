package dev.alllexey.itmowidgets.core.payments

import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.daysUntil

/** What a Договор is for (GLOSSARY.md): a dormitory place or paid study. */
enum class ContractKind {
    /** Договор найма. */
    RENT,

    /** Договор об обучении. */
    TUITION,
}

/** An academic year by its first calendar year: 2026 is 2026/2027, from 1 September to 31 August. */
data class AcademicYear(val startYear: Int) : Comparable<AcademicYear> {

    val endYear: Int get() = startYear + 1

    override fun compareTo(other: AcademicYear): Int = startYear.compareTo(other.startYear)

    companion object {
        fun of(date: LocalDate) = AcademicYear(if (date.month >= Month.SEPTEMBER) date.year else date.year - 1)
    }
}

/**
 * Where a Платёж stands on a day: [PAID] once paid in full; unpaid it is [UPCOMING], due within [DUE_SOON_DAYS]
 * days ([DUE_SOON]), due that day ([DUE_TODAY]) or past its date ([OVERDUE], which makes its Договор a Долг).
 */
enum class PaymentState {
    PAID,
    UPCOMING,
    DUE_SOON,
    DUE_TODAY,
    OVERDUE;

    companion object {
        /** `Нужно сделать` looks this many days ahead; the reminders only `PaymentReminders.DAYS_BEFORE`. */
        const val DUE_SOON_DAYS = 7
    }
}

/** One line of a Договор's schedule (GLOSSARY.md, Платёж): [amount] due by [dueDate] (Moscow), [paid] so far. */
data class Payment(
    val amount: Money,
    val paid: Money,
    val dueDate: LocalDate,
    /** ITMO's label of the billed period, `Сентябрь 2026`. */
    val period: String,
) {
    val isPaid: Boolean get() = paid >= amount

    /** What is left to pay; zero once paid. */
    val remaining: Money get() = if (isPaid) Money.ZERO else amount - paid

    /** Paid in part: something paid, not all of it. */
    val isPartlyPaid: Boolean get() = !isPaid && paid.isPositive

    fun stateOn(today: LocalDate): PaymentState {
        if (isPaid) return PaymentState.PAID
        val days = today.daysUntil(dueDate)
        return when {
            days < 0 -> PaymentState.OVERDUE
            days == 0 -> PaymentState.DUE_TODAY
            days <= PaymentState.DUE_SOON_DAYS -> PaymentState.DUE_SOON
            else -> PaymentState.UPCOMING
        }
    }

    /** Whole days from [today] to the due date; negative once overdue. */
    fun daysLeft(today: LocalDate): Int = today.daysUntil(dueDate)
}

/**
 * A Договор (GLOSSARY.md): what it is for, ITMO's [id] and printed [number], the dates it runs, the [academicYear] it
 * belongs to, whether it is [active], its [balance] (negative is owed, positive an overpayment) and its schedule of
 * [payments] in ITMO's order. Number and amounts are personal financial data: never log them.
 */
data class Contract(
    val id: String,
    val kind: ContractKind,
    val number: String,
    val start: LocalDate,
    val end: LocalDate,
    val academicYear: AcademicYear,
    val active: Boolean,
    val balance: Money,
    val payments: List<Payment>,
) {
    /**
     * The Долг on [today]: what a negative balance says is owed, otherwise what is left of the overdue Платежи; null
     * when nothing is owed.
     */
    fun debtOn(today: LocalDate): Money? {
        if (balance.isNegative) return -balance
        val overdue = payments.filter { it.stateOn(today) == PaymentState.OVERDUE }.fold(Money.ZERO) { sum, it -> sum + it.remaining }
        return overdue.takeIf { it.isPositive }
    }

    fun isDebtOn(today: LocalDate): Boolean = debtOn(today) != null

    /** Something is left to pay: a Долг or an unpaid Платёж. */
    fun hasSomethingToPay(): Boolean = balance.isNegative || payments.any { !it.isPaid }
}

/** Contracts of one earlier academic year. */
data class ContractYear(val year: AcademicYear, val contracts: List<Contract>)

/**
 * Contracts as a screen shows them: [current] the ones of this academic year or later, and any Долг whatever its
 * year; [past] the other earlier years, newest first (`Прошлые годы`).
 */
data class ContractsByYear(val current: List<Contract>, val past: List<ContractYear>) {
    companion object {
        fun of(contracts: List<Contract>, today: LocalDate): ContractsByYear {
            val thisYear = AcademicYear.of(today)
            val (current, past) = contracts.partition { it.academicYear >= thisYear || it.isDebtOn(today) }
            return ContractsByYear(
                current = current.sortedByDescending { it.academicYear },
                past = past.groupBy { it.academicYear }.entries.sortedByDescending { it.key }
                    .map { (year, inYear) -> ContractYear(year, inYear) },
            )
        }
    }
}
