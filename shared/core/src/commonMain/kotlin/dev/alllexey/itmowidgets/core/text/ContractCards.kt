package dev.alllexey.itmowidgets.core.text

import dev.alllexey.itmowidgets.core.payments.Contract
import dev.alllexey.itmowidgets.core.payments.ContractsByYear
import kotlinx.datetime.LocalDate

/**
 * The Договоры of a screen as both features draw them (`Общежитие`, `Оплата обучения`): [current] this academic
 * year's and any Долг, [pastYears] the other earlier years folded into `Прошлые годы` (null without any). The kit
 * draws it on Android (`kit/Contracts.kt`), the iOS DesignSystem in SwiftUI (`ItmoContracts.swift`).
 */
data class ContractList(val current: List<ContractCard>, val pastYears: PastYears?) {
    companion object {
        /** [contracts] on [today] (Moscow); null without any. [pastYearsExpanded] unfolds `Прошлые годы`. */
        fun of(contracts: List<Contract>, today: LocalDate, pastYearsExpanded: Boolean): ContractList? {
            if (contracts.isEmpty()) return null
            val byYear = ContractsByYear.of(contracts, today)
            return ContractList(
                current = byYear.current.map { ContractCard.of(it, today) },
                pastYears = byYear.past.takeIf { it.isNotEmpty() }?.let { past ->
                    PastYears(
                        expanded = pastYearsExpanded,
                        years = past.map { year ->
                            ContractYearGroup(PaymentTexts.academicYear(year.year), year.contracts.map { ContractCard.of(it, today) })
                        },
                    )
                },
            )
        }
    }
}

/** `Прошлые годы`: [years] newest first, shown while [expanded]. */
data class PastYears(val expanded: Boolean, val years: List<ContractYearGroup>)

/** One earlier academic year, `2025/2026 учебный год`. */
data class ContractYearGroup(val title: UiText, val contracts: List<ContractCard>)

/**
 * One Договор: [title] its kind, [subtitle] the number and dates, [balance] the Долг, overpayment or zero balance,
 * [payments] the schedule in ITMO's order; [pay] adds `Оплатить`, which opens the feature's my.itmo page in the
 * system browser.
 */
data class ContractCard(
    val id: String,
    val title: UiText,
    val subtitle: UiText,
    val balance: BalanceLine,
    val pay: Boolean,
    val payments: List<PaymentRow>,
) {
    companion object {
        fun of(contract: Contract, today: LocalDate) = ContractCard(
            id = contract.id,
            title = PaymentTexts.kind(contract.kind),
            subtitle = PaymentTexts.subtitle(contract),
            balance = PaymentTexts.balance(contract, today),
            pay = contract.hasSomethingToPay(),
            payments = contract.payments.map { payment ->
                PaymentRow(
                    period = payment.period,
                    amount = PaymentTexts.money(payment.amount),
                    due = PaymentTexts.due(payment, today),
                    paid = PaymentTexts.paidPart(payment),
                    pill = PaymentTexts.pill(payment, today),
                )
            },
        )
    }
}

/**
 * One Платёж: ITMO's [period] label, the [amount], `до 10 октября`, `оплачено 500 ₽` when paid in part, its state;
 * [details] is the due date and the paid part in the one line both platforms show under the period.
 */
data class PaymentRow(val period: String, val amount: UiText, val due: UiText, val paid: UiText?, val pill: PaymentPill?) {
    val details: UiText get() = PaymentTexts.line(listOfNotNull(due, paid))
}
