package dev.alllexey.itmowidgets.core.today

import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.EntryRoute
import dev.alllexey.itmowidgets.core.payments.Contract
import dev.alllexey.itmowidgets.core.payments.Payment
import dev.alllexey.itmowidgets.core.payments.PaymentState
import dev.alllexey.itmowidgets.core.text.AppIcon
import dev.alllexey.itmowidgets.core.text.PaymentTexts
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import kotlinx.datetime.LocalDate

/**
 * The `Нужно сделать` items of Договоры, the same for dormitory and tuition: one `Погасить долг, 1 500 ₽` per Договор
 * with a Долг, whatever its year, until it is paid off; then one `Оплатить до 13 ноября, 1 000 ₽` per unpaid Платёж
 * due within [PaymentState.DUE_SOON_DAYS] days or today, with what is left of it. An overdue Платёж is the Долг's
 * item, not one of its own. Amounts are in the text only: never log the items.
 */
object PaymentTodos {

    /**
     * [contracts] on [today] (Moscow). Ids start with [feature] (`dormitory-debt-<contract>`,
     * `dormitory-payment-<contract>-<due date>`); each item opens [screen] over Сегодня.
     */
    fun of(contracts: List<Contract>, today: LocalDate, feature: String, screen: AppRoute): List<TodayTodo> {
        val target = EntryRoute(AppTab.HOME, overlay = screen)
        val debts = contracts.mapNotNull { contract ->
            contract.debtOn(today)?.let { debt ->
                TodayTodo(
                    id = "$feature-debt-${contract.id}",
                    icon = AppIcon.ERROR,
                    title = UiText.Res(CoreStrings.payment_todo_debt_title, listOf(PaymentTexts.money(debt))),
                    reason = UiText.Res(CoreStrings.payment_todo_debt_reason, listOf(PaymentTexts.kind(contract.kind), contract.number)),
                    action = UiText.Res(CoreStrings.payment_todo_open),
                    target = target,
                )
            }
        }
        val due = contracts.flatMap { contract -> contract.payments.filter { it.isDueOn(today) }.map { contract to it } }
            .sortedBy { (_, payment) -> payment.dueDate }
            .map { (contract, payment) ->
                TodayTodo(
                    id = "$feature-payment-${contract.id}-${payment.dueDate}",
                    icon = AppIcon.PAYMENTS,
                    title = UiText.Res(
                        CoreStrings.payment_todo_pay_title,
                        listOf(PaymentTexts.dueDate(payment, today), PaymentTexts.money(payment.remaining)),
                    ),
                    reason = UiText.Joined(
                        listOfNotNull(PaymentTexts.kind(contract.kind), PaymentTexts.pill(payment, today)?.text?.let(UiText::LowercaseFirst)),
                        SEPARATOR,
                    ),
                    action = UiText.Res(CoreStrings.payment_todo_open),
                    target = target,
                )
            }
        return debts + due
    }

    private fun Payment.isDueOn(today: LocalDate): Boolean =
        stateOn(today).let { it == PaymentState.DUE_SOON || it == PaymentState.DUE_TODAY }

    private const val SEPARATOR = " · "
}
