package dev.alllexey.itmowidgets.core.payments

import dev.alllexey.itmowidgets.core.bell.BellEvent
import dev.alllexey.itmowidgets.core.bell.BellItem
import dev.alllexey.itmowidgets.core.bell.BellKind
import dev.alllexey.itmowidgets.core.navigation.EntryRoute
import dev.alllexey.itmowidgets.core.text.PaymentTexts
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import kotlin.time.Instant

/**
 * The bell's `Платёж зачислен`, the same for dormitory and tuition: a Платёж that was unpaid in the previous stored
 * answer and is paid in the new one, matched by its Договор and due date. The first answer is a baseline.
 */
object PaymentBellEvents {

    const val PAID = "payment-paid"

    fun paid(feature: String, previous: List<Contract>, current: List<Contract>, at: Instant): List<BellEvent> {
        val unpaid = previous.flatMap { contract ->
            contract.payments.filterNot(Payment::isPaid).map { contract.id to it.dueDate }
        }.toSet()
        return current.flatMap { contract ->
            contract.payments.filter { it.isPaid && (contract.id to it.dueDate) in unpaid }.map { payment ->
                BellEvent(
                    id = "$feature:paid:${contract.id}:${payment.dueDate}",
                    type = PAID,
                    at = at.toEpochMilliseconds(),
                    values = listOf(contract.kind.name, payment.period, payment.amount.kopecks.toString()),
                )
            }
        }
    }

    /** The row of a [PAID] event (`Договор найма · Ноябрь 2026 · 1 500 ₽`); null for another type or a broken one. */
    fun item(event: BellEvent, target: EntryRoute): BellItem? {
        if (event.type != PAID) return null
        val (kind, period, kopecks) = event.values.takeIf { it.size == PAID_VALUES } ?: return null
        val contractKind = ContractKind.entries.firstOrNull { it.name == kind } ?: return null
        val amount = kopecks.toLongOrNull()?.let(::Money) ?: return null
        return BellItem(
            id = event.id,
            kind = BellKind.PAYMENT,
            at = event.seenAt,
            title = UiText.Res(CoreStrings.payment_bell_paid),
            text = UiText.Joined(listOf(PaymentTexts.kind(contractKind), UiText.Dynamic(period), PaymentTexts.money(amount)), SEPARATOR),
            target = target,
        )
    }

    private const val PAID_VALUES = 3
    private const val SEPARATOR = " · "
}
