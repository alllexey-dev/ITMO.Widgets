package dev.alllexey.itmowidgets.core.text

import dev.alllexey.itmowidgets.core.payments.AcademicYear
import dev.alllexey.itmowidgets.core.payments.Contract
import dev.alllexey.itmowidgets.core.payments.ContractKind
import dev.alllexey.itmowidgets.core.payments.Money
import dev.alllexey.itmowidgets.core.payments.Payment
import dev.alllexey.itmowidgets.core.payments.PaymentState
import dev.alllexey.itmowidgets.shared.core.CorePlurals
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format
import kotlin.math.abs

/** How a payment text reads: [DONE] paid or overpaid, [ATTENTION] due soon, [DEBT] a Долг or an overdue Платёж. */
enum class PaymentTone { NEUTRAL, DONE, ATTENTION, DEBT }

/** A Платёж's state as a pill: `Оплачен`, `Сегодня`, `Через 3 дня`, `Просрочен`. */
data class PaymentPill(val text: UiText, val tone: PaymentTone)

/** A Договор's balance as one line: `Долг 1 500 ₽`, `Переплата 250 ₽` or `Баланс 0 ₽`. */
data class BalanceLine(val label: UiText, val amount: UiText, val tone: PaymentTone)

/**
 * The texts of Договоры and Платежи every screen, `Нужно сделать` item and reminder shares (GLOSSARY.md, Payments),
 * so dormitory and tuition read the same.
 */
object PaymentTexts {

    /**
     * `1 500 ₽`, `1 500,50 ₽`: the number with groups of three split by no-break spaces and kopecks only when there
     * are any, the currency from the string resource.
     */
    fun money(money: Money): UiText {
        val kopecks = abs(money.kopecks)
        val rubles = (kopecks / Money.KOPECKS_PER_RUBLE).toString().reversed().chunked(GROUP).joinToString(NBSP).reversed()
        val fraction = (kopecks % Money.KOPECKS_PER_RUBLE).toInt()
        val sign = if (money.isNegative) MINUS else ""
        val decimals = if (fraction == 0) "" else "," + fraction.toString().padStart(2, '0')
        return UiText.Res(CoreStrings.payment_money, listOf("$sign$rubles$decimals"))
    }

    /** [parts] in one line split by ` · `: `Договор найма · Ноябрь 2026 · 1 000 ₽`, `до 10 октября · оплачено 500 ₽`. */
    fun line(parts: List<UiText>): UiText = UiText.Joined(parts, SEPARATOR)

    /** `Договор найма`, `Договор об обучении`. */
    fun kind(kind: ContractKind): UiText = when (kind) {
        ContractKind.RENT -> UiText.Res(CoreStrings.contract_kind_rent)
        ContractKind.TUITION -> UiText.Res(CoreStrings.contract_kind_tuition)
    }

    /** `№ 0000.00.00.0001 · 1 сентября 2026 - 30 июня 2027`. */
    fun subtitle(contract: Contract): UiText = UiText.Res(
        CoreStrings.contract_subtitle,
        listOf(contract.number, contract.start.format(DateTexts.DAY_MONTH_YEAR), contract.end.format(DateTexts.DAY_MONTH_YEAR)),
    )

    /** `2025/2026 учебный год`; the years go as text, so no platform groups their digits. */
    fun academicYear(year: AcademicYear): UiText =
        UiText.Res(CoreStrings.contract_academic_year, listOf(year.startYear.toString(), year.endYear.toString()))

    /** The Долг when there is one, else the overpayment or a zero balance. */
    fun balance(contract: Contract, today: LocalDate): BalanceLine {
        contract.debtOn(today)?.let { return BalanceLine(UiText.Res(CoreStrings.contract_debt), money(it), PaymentTone.DEBT) }
        if (contract.balance.isPositive) {
            return BalanceLine(UiText.Res(CoreStrings.contract_overpaid), money(contract.balance), PaymentTone.DONE)
        }
        return BalanceLine(UiText.Res(CoreStrings.contract_balance), money(contract.balance), PaymentTone.NEUTRAL)
    }

    /** `до 10 октября`; another calendar year than [today]'s adds the year. */
    fun due(payment: Payment, today: LocalDate): UiText = UiText.Res(CoreStrings.payment_due, listOf(dueDate(payment, today)))

    /** `10 октября`; another calendar year than [today]'s adds the year. */
    fun dueDate(payment: Payment, today: LocalDate): String {
        val format = if (payment.dueDate.year == today.year) DateTexts.DAY_MONTH else DateTexts.DAY_MONTH_YEAR
        return payment.dueDate.format(format)
    }

    /** `оплачено 500 ₽` for a Платёж paid in part, else null. */
    fun paidPart(payment: Payment): UiText? =
        if (payment.isPartlyPaid) UiText.Res(CoreStrings.payment_paid_part, listOf(money(payment.paid))) else null

    /** The pill of a Платёж on [today]; an upcoming one has none. */
    fun pill(payment: Payment, today: LocalDate): PaymentPill? = when (payment.stateOn(today)) {
        PaymentState.PAID -> PaymentPill(UiText.Res(CoreStrings.payment_paid), PaymentTone.DONE)
        PaymentState.UPCOMING -> null
        PaymentState.DUE_SOON -> payment.daysLeft(today).let {
            PaymentPill(UiText.Plural(CorePlurals.payment_due_in_days, it, listOf(it)), PaymentTone.ATTENTION)
        }
        PaymentState.DUE_TODAY -> PaymentPill(UiText.Res(CoreStrings.payment_due_today), PaymentTone.ATTENTION)
        PaymentState.OVERDUE -> PaymentPill(UiText.Res(CoreStrings.payment_overdue), PaymentTone.DEBT)
    }

    private const val SEPARATOR = " \u00B7 "
    private const val GROUP = 3
    private const val NBSP = "\u00A0"
    private const val MINUS = "\u2212"
}
