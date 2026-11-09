package dev.alllexey.itmowidgets.core.payments.check

import dev.alllexey.itmowidgets.core.notification.NotificationDestination
import dev.alllexey.itmowidgets.core.payments.Contract
import dev.alllexey.itmowidgets.core.payments.PaymentState
import dev.alllexey.itmowidgets.core.text.PaymentTexts
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.core.CorePlurals
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import kotlinx.datetime.LocalDate

/**
 * The reminders of unpaid Платежи, the same for dormitory and tuition: one within [DAYS_BEFORE] days of the due date
 * (`Платёж через 3 дня`) and one on the due day (`Сегодня срок платежа`), each its own key, so each is posted once.
 * A Платёж first seen two days ahead gets the first one then; an overdue one gets none: it is a Долг, and the app
 * does not nag about it (`Нужно сделать` keeps it).
 */
object PaymentReminders {

    const val DAYS_BEFORE = 3

    fun of(contracts: List<Contract>, today: LocalDate, feature: String, destination: NotificationDestination): List<PaymentsNotice> =
        contracts.flatMap { contract ->
            contract.payments.mapNotNull { payment ->
                val state = payment.stateOn(today)
                val days = payment.daysLeft(today)
                val title = when {
                    state == PaymentState.DUE_TODAY -> UiText.Res(CoreStrings.payment_reminder_today)
                    state == PaymentState.DUE_SOON && days <= DAYS_BEFORE ->
                        UiText.Plural(CorePlurals.payment_reminder_in_days, days, listOf(days))
                    else -> return@mapNotNull null
                }
                val slot = "$feature-payment-${contract.id}-${payment.dueDate}"
                PaymentsNotice(
                    key = "$slot-${if (state == PaymentState.DUE_TODAY) "today" else "soon"}",
                    slot = slot,
                    title = title,
                    text = PaymentTexts.line(
                        listOf(PaymentTexts.kind(contract.kind), UiText.Dynamic(payment.period), PaymentTexts.money(payment.remaining)),
                    ),
                    publicTitle = title,
                    destination = destination,
                )
            }
        }
}
