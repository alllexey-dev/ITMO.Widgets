package dev.alllexey.itmowidgets.designsystem.kit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.text.ContractCard
import dev.alllexey.itmowidgets.core.text.ContractList
import dev.alllexey.itmowidgets.core.text.PaymentTone
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.designsystem.components.buttons.TonalButton
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_expand_more
import dev.alllexey.itmowidgets.shared.designsystem.ic_history
import dev.alllexey.itmowidgets.shared.designsystem.ic_open_in_new
import org.jetbrains.compose.resources.painterResource

// The shared Договоры (`core/text/ContractCards.kt`) drawn by the kit, so `Общежитие` and `Оплата обучения` read the
// same; iOS draws them in `ItmoContracts.swift`.

/** Tags for host tests of the screens that draw [ContractSections]. */
object ContractTestTags {
    const val PAY = "contract_pay"
    const val PAST_YEARS = "contract_past_years"
}

/**
 * The current Договоры, each with its balance, `Оплатить` ([onPay]) while something is left to pay and its Платежи,
 * then `Прошлые годы`, unfolded by [onTogglePastYears]. Laid out in the caller's column.
 */
@Composable
fun ContractSections(list: ContractList, onPay: () -> Unit, onTogglePastYears: () -> Unit) {
    list.current.forEach { ContractSection(it, onPay) }
    val past = list.pastYears ?: return
    ListGroup(Modifier.padding(top = ItmoTheme.spacing.content)) {
        item {
            ListRow(
                UiText.Res(CoreStrings.contract_past_years).asString(),
                Modifier.testTag(ContractTestTags.PAST_YEARS),
                icon = painterResource(Res.drawable.ic_history),
                iconTone = Tone.Neutral,
                accessory = {
                    Icon(
                        painterResource(Res.drawable.ic_expand_more),
                        null,
                        Modifier.rotate(if (past.expanded) 180f else 0f),
                        tint = ItmoTheme.colorScheme.onSurfaceVariant,
                    )
                },
                onClick = onTogglePastYears,
            )
        }
    }
    if (past.expanded) {
        past.years.forEach { year ->
            Text(
                year.title.asString(),
                Modifier.padding(top = ItmoTheme.spacing.content, start = ItmoTheme.spacing.related).semantics { heading() },
                color = ItmoTheme.colorScheme.onSurfaceVariant,
                style = ItmoTheme.typography.labelLarge,
            )
            year.contracts.forEach { ContractSection(it, onPay) }
        }
    }
}

/** One Договор: its kind, number and dates, the balance with `Оплатить`, then its Платежи. */
@Composable
private fun ContractSection(contract: ContractCard, onPay: () -> Unit) {
    SectionHeader(contract.title.asString(), Modifier.padding(top = ItmoTheme.spacing.content))
    ItmoCard(tone = if (contract.balance.tone == PaymentTone.DEBT) Tone.Error else null) {
        Text(contract.subtitle.asString(), color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodyMedium)
        Row(
            Modifier.fillMaxWidth().padding(top = ItmoTheme.spacing.related),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
        ) {
            Text(contract.balance.label.asString(), Modifier.weight(1f), style = ItmoTheme.typography.titleMedium)
            Text(contract.balance.amount.asString(), style = ItmoTheme.typography.titleLarge)
        }
        if (contract.pay) {
            TonalButton(
                onClick = onPay,
                modifier = Modifier.padding(top = ItmoTheme.spacing.related).testTag(ContractTestTags.PAY),
            ) {
                Icon(painterResource(Res.drawable.ic_open_in_new), null, Modifier.size(ButtonIcon))
                Text(UiText.Res(CoreStrings.contract_pay).asString(), Modifier.padding(start = ItmoTheme.spacing.related))
            }
        }
    }
    if (contract.payments.isEmpty()) return
    ListGroup(Modifier.padding(top = ItmoTheme.spacing.related)) {
        contract.payments.forEach { payment ->
            item {
                ListRow(
                    payment.period,
                    subtitle = listOfNotNull(payment.due, payment.paid).map { it.asString() }.joinToString(SEPARATOR),
                    trailing = payment.amount.asString(),
                    below = payment.pill?.let { pill -> { StatusPill(pill.text.asString(), tone = pill.tone.kitTone()) } },
                )
            }
        }
    }
}

private fun PaymentTone.kitTone(): Tone = when (this) {
    PaymentTone.NEUTRAL -> Tone.Neutral
    PaymentTone.DONE -> Tone.Success
    PaymentTone.ATTENTION -> Tone.Warning
    PaymentTone.DEBT -> Tone.Error
}

private val ButtonIcon = 18.dp

private const val SEPARATOR = " \u00B7 "
