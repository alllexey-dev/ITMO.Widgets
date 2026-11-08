package dev.alllexey.itmowidgets.feature.dormitory.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.location.MapDestination
import dev.alllexey.itmowidgets.core.text.PaymentTone
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.designsystem.components.buttons.TonalButton
import dev.alllexey.itmowidgets.designsystem.components.state.AppRefreshBox
import dev.alllexey.itmowidgets.designsystem.components.state.ContentState
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateAction
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateLoading
import dev.alllexey.itmowidgets.designsystem.kit.ItmoCard
import dev.alllexey.itmowidgets.designsystem.kit.ItmoDetailScreen
import dev.alllexey.itmowidgets.designsystem.kit.ItmoSnackbarHost
import dev.alllexey.itmowidgets.designsystem.kit.ListGroup
import dev.alllexey.itmowidgets.designsystem.kit.ListGroupNote
import dev.alllexey.itmowidgets.designsystem.kit.ListRow
import dev.alllexey.itmowidgets.designsystem.kit.RichTextParagraph
import dev.alllexey.itmowidgets.designsystem.kit.SectionHeader
import dev.alllexey.itmowidgets.designsystem.kit.StaleContent
import dev.alllexey.itmowidgets.designsystem.kit.StatusPill
import dev.alllexey.itmowidgets.designsystem.kit.Tone
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.dormitory.presentation.ContractCard
import dev.alllexey.itmowidgets.feature.dormitory.presentation.DormitoryContent
import dev.alllexey.itmowidgets.feature.dormitory.presentation.DormitoryContracts
import dev.alllexey.itmowidgets.feature.dormitory.presentation.DormitoryDocuments
import dev.alllexey.itmowidgets.feature.dormitory.presentation.DormitoryPlace
import dev.alllexey.itmowidgets.feature.dormitory.presentation.DormitoryStageCard
import dev.alllexey.itmowidgets.feature.dormitory.presentation.DormitoryTone
import dev.alllexey.itmowidgets.feature.dormitory.presentation.DormitoryUiState
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_apartment
import dev.alllexey.itmowidgets.shared.designsystem.ic_check_circle
import dev.alllexey.itmowidgets.shared.designsystem.ic_error
import dev.alllexey.itmowidgets.shared.designsystem.ic_event_available
import dev.alllexey.itmowidgets.shared.designsystem.ic_expand_more
import dev.alllexey.itmowidgets.shared.designsystem.ic_history
import dev.alllexey.itmowidgets.shared.designsystem.ic_map
import dev.alllexey.itmowidgets.shared.designsystem.ic_open_in_new
import dev.alllexey.itmowidgets.shared.designsystem.ic_upload
import dev.alllexey.itmowidgets.shared.feature.dormitory.DormitoryStrings
import org.jetbrains.compose.resources.painterResource

/** What the dormitory screen asks of its route. */
data class DormitoryActions(
    val onBack: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onRetry: () -> Unit = {},
    /** `Выбрать дату заселения` and `Загрузить`: the dormitory section of my.itmo in the app. */
    val onOpenMyItmo: () -> Unit = {},
    val onOpenMap: (MapDestination) -> Unit = {},
    val onLink: (String) -> Unit = {},
    /** `Оплатить`: my.itmo in the system browser. */
    val onPay: () -> Unit = {},
    val onTogglePastYears: () -> Unit = {},
)

/** Tags for host tests. */
object DormitoryTestTags {
    const val CHOOSE_DATE = "dormitory_choose_date"
    const val UPLOAD = "dormitory_upload"
    const val MAP = "dormitory_map"
    const val PAY = "dormitory_pay"
    const val PAST_YEARS = "dormitory_past_years"
}

/**
 * `Общежитие` (docs/features/dormitory.md): the Этап общежития with the queue place or `Выбрать дату заселения`, the
 * Документы для заселения while they matter, the Договоры найма with their Платежи and `Оплатить`, the assigned
 * dormitory with `На карте` and the settlement instructions;
 * the stored answer dimmed under `Обновлено 2 ч назад` when my.itmo did not answer. Stateless; [DormitoryRoute] feeds
 * it.
 */
@Composable
fun DormitoryScreen(
    state: DormitoryUiState,
    actions: DormitoryActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    ItmoDetailScreen(
        title = text(DormitoryStrings.dormitory_title),
        onBack = actions.onBack,
        backLabel = text(CoreStrings.common_back),
        modifier = modifier,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AppRefreshBox(refreshing = state.refreshing, onRefresh = actions.onRefresh, modifier = Modifier.fillMaxSize()) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(
                        start = ItmoTheme.spacing.screenMargin,
                        end = ItmoTheme.spacing.screenMargin,
                        top = ItmoTheme.spacing.related,
                        bottom = ItmoTheme.spacing.section,
                    ),
                ) {
                    when (val content = state.content) {
                        DormitoryContent.Loading -> ContentStateLoading()
                        is DormitoryContent.Failed -> ContentState(
                            text(CoreStrings.common_load_error_title),
                            icon = painterResource(Res.drawable.ic_error),
                            description = content.text.asString(),
                            action = ContentStateAction(text(CoreStrings.common_retry), actions.onRetry),
                        )
                        DormitoryContent.NoApplication -> ContentState(
                            text(DormitoryStrings.dormitory_none_title),
                            icon = painterResource(Res.drawable.ic_apartment),
                            description = text(DormitoryStrings.dormitory_none_text),
                        )
                        is DormitoryContent.Shown -> {
                            val stale = state.stale
                            if (stale != null) StaleContent(stale.asString()) { Shown(content, actions) } else Shown(content, actions)
                        }
                    }
                }
            }
            ItmoSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun Shown(content: DormitoryContent.Shown, actions: DormitoryActions) {
    Column(verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content)) {
        content.stage?.let { Stage(it, actions) }
        content.documents?.let { Documents(it, actions) }
        content.contracts?.let { Contracts(it, actions) }
        content.dormitory?.let { Place(it, actions) }
    }
}

/** The stage as the screen's hero: what it means, the queue place, the date to pick. */
@Composable
private fun Stage(stage: DormitoryStageCard, actions: DormitoryActions) {
    ItmoCard(hero = true, tone = stage.tone.cardTone()) {
        Text(stage.title.asString(), Modifier.semantics { heading() }, style = ItmoTheme.typography.titleLarge)
        stage.queuePlace?.let { Text(it.asString(), style = ItmoTheme.typography.titleMedium) }
        Text(stage.text.asString(), style = ItmoTheme.typography.bodyMedium)
        if (stage.chooseDate) {
            TonalButton(
                onClick = actions.onOpenMyItmo,
                modifier = Modifier.padding(top = ItmoTheme.spacing.related).testTag(DormitoryTestTags.CHOOSE_DATE),
            ) {
                Icon(painterResource(Res.drawable.ic_event_available), null, Modifier.size(ButtonIcon))
                Text(text(DormitoryStrings.dormitory_choose_date), Modifier.padding(start = ItmoTheme.spacing.related))
            }
        }
    }
}

/** Each document with ITMO's status, then `Загрузить` while one is not confirmed. */
@Composable
private fun Documents(documents: DormitoryDocuments, actions: DormitoryActions) {
    SectionHeader(text(DormitoryStrings.dormitory_documents_section), Modifier.padding(top = ItmoTheme.spacing.content))
    if (documents.rows.isEmpty()) {
        ListGroupNote(text(DormitoryStrings.dormitory_documents_none))
        return
    }
    ListGroup {
        documents.rows.forEach { row ->
            item {
                ListRow(
                    row.name,
                    below = {
                        StatusPill(
                            row.status,
                            tone = if (row.confirmed) Tone.Success else Tone.Warning,
                            icon = painterResource(if (row.confirmed) Res.drawable.ic_check_circle else Res.drawable.ic_error),
                        )
                    },
                )
            }
        }
        if (documents.upload) {
            item {
                ListRow(
                    text(DormitoryStrings.dormitory_upload),
                    Modifier.testTag(DormitoryTestTags.UPLOAD),
                    subtitle = text(DormitoryStrings.dormitory_upload_subtitle),
                    icon = painterResource(Res.drawable.ic_upload),
                    iconTone = Tone.Primary,
                    chevron = true,
                    onClick = actions.onOpenMyItmo,
                )
            }
        }
    }
}

/** The dormitory, `На карте` in the system map app, and how to settle in. */
@Composable
private fun Place(place: DormitoryPlace, actions: DormitoryActions) {
    SectionHeader(text(DormitoryStrings.dormitory_place_section), Modifier.padding(top = ItmoTheme.spacing.content))
    ListGroup {
        item {
            ListRow(place.name, subtitle = place.address, icon = painterResource(Res.drawable.ic_apartment), iconTone = Tone.Primary)
        }
        item {
            ListRow(
                text(DormitoryStrings.dormitory_on_map),
                Modifier.testTag(DormitoryTestTags.MAP),
                icon = painterResource(Res.drawable.ic_map),
                iconTone = Tone.Neutral,
                accessory = {
                    Icon(painterResource(Res.drawable.ic_open_in_new), null, tint = ItmoTheme.colorScheme.onSurfaceVariant)
                },
                onClick = { actions.onOpenMap(place.map) },
            )
        }
    }
    if (place.instructions.blocks.isNotEmpty()) {
        SectionHeader(text(DormitoryStrings.dormitory_instructions_section), Modifier.padding(top = ItmoTheme.spacing.content))
        ItmoCard {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content)) {
                place.instructions.blocks.forEach { RichTextParagraph(it, actions.onLink) }
            }
        }
    }
}

/** The current Договоры найма, then `Прошлые годы`, unfolded on a tap. */
@Composable
private fun Contracts(contracts: DormitoryContracts, actions: DormitoryActions) {
    contracts.current.forEach { ContractSection(it, actions) }
    val past = contracts.pastYears ?: return
    ListGroup(Modifier.padding(top = ItmoTheme.spacing.content)) {
        item {
            ListRow(
                text(CoreStrings.contract_past_years),
                Modifier.testTag(DormitoryTestTags.PAST_YEARS),
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
                onClick = actions.onTogglePastYears,
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
            year.contracts.forEach { ContractSection(it, actions) }
        }
    }
}

/** One Договор: its kind, number and dates, the balance with `Оплатить`, then its Платежи. */
@Composable
private fun ContractSection(contract: ContractCard, actions: DormitoryActions) {
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
                onClick = actions.onPay,
                modifier = Modifier.padding(top = ItmoTheme.spacing.related).testTag(DormitoryTestTags.PAY),
            ) {
                Icon(painterResource(Res.drawable.ic_open_in_new), null, Modifier.size(ButtonIcon))
                Text(text(CoreStrings.contract_pay), Modifier.padding(start = ItmoTheme.spacing.related))
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

private fun DormitoryTone.cardTone(): Tone? = when (this) {
    DormitoryTone.NEUTRAL -> null
    DormitoryTone.ATTENTION -> Tone.Warning
    DormitoryTone.DONE -> Tone.Success
}

@Composable
private fun text(key: String, vararg arguments: Any): String = UiText.Res(key, arguments.toList()).asString()

private val ButtonIcon = 18.dp

private const val SEPARATOR = " · "
