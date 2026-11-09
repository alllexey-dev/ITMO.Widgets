package dev.alllexey.itmowidgets.core.payments.check

import dev.alllexey.itmowidgets.core.notification.NotificationDestination
import dev.alllexey.itmowidgets.core.payments.Contract
import dev.alllexey.itmowidgets.core.payments.Payment
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
            contract.payments.filterNot(Payment::isPaid).mapNotNull { payment ->
                val days = payment.daysLeft(today)
                val slot = "$feature-payment-${contract.id}-${payment.dueDate}"
                val title = when (days) {
                    0 -> UiText.Res(CoreStrings.payment_reminder_today)
                    in 1..DAYS_BEFORE -> UiText.Plural(CorePlurals.payment_reminder_in_days, days, listOf(days))
                    else -> return@mapNotNull null
                }
                PaymentsNotice(
                    key = "$slot-${if (days == 0) "today" else "soon"}",
                    slot = slot,
                    title = title,
                    text = UiText.Joined(
                        listOf(PaymentTexts.kind(contract.kind), UiText.Dynamic(payment.period), PaymentTexts.money(payment.remaining)),
                        SEPARATOR,
                    ),
                    publicTitle = title,
                    destination = destination,
                )
            }
        }

    private const val SEPARATOR = " · "
}
