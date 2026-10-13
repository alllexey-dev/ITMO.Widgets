package dev.alllexey.itmowidgets.feature.recordbook.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.designsystem.components.expressive.ItmoWavyProgress
import dev.alllexey.itmowidgets.designsystem.components.state.AppRefreshBox
import dev.alllexey.itmowidgets.designsystem.components.state.ContentState
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateAction
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateLoading
import dev.alllexey.itmowidgets.designsystem.kit.ItmoCard
import dev.alllexey.itmowidgets.designsystem.kit.ItmoRootScreen
import dev.alllexey.itmowidgets.designsystem.kit.ListGroup
import dev.alllexey.itmowidgets.designsystem.kit.ListGroupNote
import dev.alllexey.itmowidgets.designsystem.kit.ListRow
import dev.alllexey.itmowidgets.designsystem.kit.PointsMeter
import dev.alllexey.itmowidgets.designsystem.kit.SectionHeader
import dev.alllexey.itmowidgets.designsystem.kit.SegmentedChoice
import dev.alllexey.itmowidgets.designsystem.kit.SportRing
import dev.alllexey.itmowidgets.designsystem.kit.StatusPill
import dev.alllexey.itmowidgets.designsystem.kit.Tone
import dev.alllexey.itmowidgets.designsystem.kit.ValueWithSource
import dev.alllexey.itmowidgets.designsystem.kit.ink
import dev.alllexey.itmowidgets.designsystem.kit.tabular
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookBarsNotice
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookBody
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookPeriods
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookRow
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSummary
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookTarget
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookTone
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookUiState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookValue
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookView
import dev.alllexey.itmowidgets.shared.compose.strings.common_load_error_title
import dev.alllexey.itmowidgets.shared.compose.strings.common_retry
import dev.alllexey.itmowidgets.shared.designsystem.ic_arrow_drop_down
import dev.alllexey.itmowidgets.shared.designsystem.ic_assignment
import dev.alllexey.itmowidgets.shared.designsystem.ic_check
import dev.alllexey.itmowidgets.shared.designsystem.ic_chevron_right
import dev.alllexey.itmowidgets.shared.designsystem.ic_close
import dev.alllexey.itmowidgets.shared.designsystem.ic_error
import dev.alllexey.itmowidgets.shared.designsystem.ic_history
import dev.alllexey.itmowidgets.shared.feature.recordbook.Res
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_bars_connect
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_bars_expired
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_bars_failed
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_bars_hide
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_bars_offer
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_bars_sign_in
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_empty_description
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_empty_title
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_period_label
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_refresh_error
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_score_pending
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_session_title
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_sources_action
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_subject_new
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_view_semester
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_view_session
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.compose.strings.Res as CoreRes
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/** Test tags of [RecordbookScreen]. */
object RecordbookTestTags {
    const val LIST = "recordbook:list"
    const val LOADING = "recordbook:loading"
    const val ERROR = "recordbook:error"
    const val EMPTY = "recordbook:empty"
    const val PERIOD = "recordbook:period"
    const val SUMMARY = "recordbook:summary"
    const val BARS_OFFER = "recordbook:bars_offer"
    const val BARS_NOTICE = "recordbook:bars_notice"
    const val NEW_MARK = "recordbook:new_mark"

    fun row(id: Long) = "recordbook:row:$id"
    fun source(id: Long) = "recordbook:source:$id"
}

/**
 * What Зачётка asks of its host and its ViewModel. The host opens a [RecordbookTarget] (the subject page or the Спорт
 * tab) and the BARS sign-in; [RecordbookRoute] wires the rest to the ViewModel.
 */
data class RecordbookActions(
    val onOpen: (RecordbookTarget) -> Unit = {},
    val onBarsSignIn: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onSelectPeriod: (programId: Long, semester: Int) -> Unit = { _, _ -> },
    val onSelectView: (RecordbookView) -> Unit = {},
    val onHideOffer: () -> Unit = {},
    val onOpenSources: (id: Long) -> Unit = {},
    val onCloseSources: () -> Unit = {},
)

/**
 * Зачётка (docs/features/recordbook.md): the root header with [title], the period's subtitle and the period action,
 * `Семестр` / `Сессия`, the session's hero, then what needs action first and the rest, each number over its quiet
 * source. A source opens `Откуда баллы`. Stateless; [RecordbookRoute] feeds it.
 */
@Composable
fun RecordbookScreen(
    title: String,
    state: RecordbookUiState,
    actions: RecordbookActions,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    ItmoRootScreen(
        title = title,
        modifier = modifier,
        subtitle = state.subtitle?.asString(),
        actions = { state.periods?.let { PeriodAction(it, actions.onSelectPeriod) } },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) { Body(state.body, actions, listState) }
    }
    val sources = (state.body as? RecordbookBody.Content)?.sources
    if (sources != null) RecordbookSourcesSheet(sources, actions)
}

/** `3 семестр ▾` and its menu: the periods of each programme, newest first, the chosen one checked. */
@Composable
private fun PeriodAction(periods: RecordbookPeriods, onSelect: (Long, Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, modifier = Modifier.testTag(RecordbookTestTags.PERIOD)) {
            Text(periods.label.asString())
            Icon(painterResource(KitRes.drawable.ic_arrow_drop_down), contentDescription = stringResource(Res.string.recordbook_period_label))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            periods.groups.forEach { group ->
                group.program?.let { program ->
                    Text(
                        program,
                        Modifier.padding(horizontal = ItmoTheme.spacing.group, vertical = ItmoTheme.spacing.compact),
                        color = ItmoTheme.colorScheme.primary,
                        style = ItmoTheme.typography.labelLarge,
                    )
                }
                group.options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(option.title.asString(), style = ItmoTheme.typography.bodyLarge)
                                Text(option.detail.asString(), color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodySmall)
                            }
                        },
                        onClick = {
                            expanded = false
                            onSelect(option.programId, option.semester)
                        },
                        trailingIcon = if (option.selected) {
                            { Icon(painterResource(KitRes.drawable.ic_check), contentDescription = null) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Body(body: RecordbookBody, actions: RecordbookActions, listState: LazyListState) {
    when (body) {
        RecordbookBody.Loading -> ContentStateLoading(Modifier.testTag(RecordbookTestTags.LOADING))
        is RecordbookBody.Failed -> AppRefreshBox(body.retrying, actions.onRetry, Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                ContentState(
                    title = stringResource(CoreRes.string.common_load_error_title),
                    modifier = Modifier.testTag(RecordbookTestTags.ERROR),
                    description = body.error.toUiText().asString(),
                    action = ContentStateAction(stringResource(CoreRes.string.common_retry), actions.onRetry),
                )
            }
        }
        RecordbookBody.Empty -> ContentState(
            title = stringResource(Res.string.recordbook_empty_title),
            modifier = Modifier.testTag(RecordbookTestTags.EMPTY),
            description = stringResource(Res.string.recordbook_empty_description),
        )
        is RecordbookBody.Content -> AppRefreshBox(body.refreshing, actions.onRefresh, Modifier.fillMaxSize()) {
            Content(body, actions, listState)
        }
    }
}

@Composable
private fun Content(body: RecordbookBody.Content, actions: RecordbookActions, listState: LazyListState) {
    LazyColumn(
        Modifier.fillMaxSize().testTag(RecordbookTestTags.LIST),
        state = listState,
        contentPadding = PaddingValues(
            start = ItmoTheme.spacing.screenMargin,
            end = ItmoTheme.spacing.screenMargin,
            top = ItmoTheme.spacing.related,
            bottom = ItmoTheme.spacing.section,
        ),
        verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
    ) {
        item(key = "view") {
            val views = RecordbookView.entries
            SegmentedChoice(
                options = listOf(stringResource(Res.string.recordbook_view_semester), stringResource(Res.string.recordbook_view_session)),
                selectedIndex = views.indexOf(body.view),
                onSelect = { actions.onSelectView(views[it]) },
            )
        }
        body.refreshError?.let { error ->
            item(key = "refresh-error") {
                ListGroupNote(stringResource(Res.string.recordbook_refresh_error, error.toUiText().asString()))
            }
        }
        body.summary?.let { summary -> item(key = "summary") { SessionHero(summary) } }
        body.barsNotice?.let { notice -> item(key = "bars-notice") { BarsNotice(notice, actions.onBarsSignIn, actions.onRefresh) } }
        body.sections.forEach { section ->
            item(key = "title-${section.title}") { SectionHeader(section.title.asString()) }
            item(key = "section-${section.title}") {
                ListGroup { section.rows.forEach { row -> item { RecordRow(row, actions) } } }
            }
        }
        if (body.barsOffer) item(key = "bars-offer") { BarsOffer(actions) }
    }
}

/** The session: `Сдано 3 из 6` on a wavy progress and the average; the one hero of the list. */
@Composable
private fun SessionHero(summary: RecordbookSummary) {
    ItmoCard(Modifier.testTag(RecordbookTestTags.SUMMARY), tone = Tone.Primary, hero = true) {
        Text(stringResource(Res.string.recordbook_session_title), style = ItmoTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact)) {
            Text(summary.passed.asString(), style = ItmoTheme.typography.displaySmall.tabular())
            Text(summary.of.asString(), Modifier.padding(bottom = ItmoTheme.spacing.compact), style = ItmoTheme.typography.titleMedium)
        }
        ItmoWavyProgress({ summary.share }, Modifier.fillMaxWidth().padding(vertical = ItmoTheme.spacing.related))
        summary.average?.let { Text(it.asString(), style = ItmoTheme.typography.bodyMedium) }
    }
}

/** BARS over the list: one quiet row with `Войти` or `Повторить`, never a card. */
@Composable
internal fun BarsNotice(notice: RecordbookBarsNotice, onSignIn: () -> Unit, onRetry: () -> Unit) {
    val expired = notice is RecordbookBarsNotice.Expired
    ListGroup(Modifier.testTag(RecordbookTestTags.BARS_NOTICE)) {
        item {
            ListRow(
                title = stringResource(if (expired) Res.string.recordbook_bars_expired else Res.string.recordbook_bars_failed),
                subtitle = (notice as? RecordbookBarsNotice.Failed)?.error?.toUiText()?.asString(),
                icon = painterResource(if (expired) KitRes.drawable.ic_history else KitRes.drawable.ic_error),
                iconTone = Tone.Warning,
                accessory = {
                    TextButton(onClick = if (expired) onSignIn else onRetry) {
                        Text(stringResource(if (expired) Res.string.recordbook_bars_sign_in else CoreRes.string.common_retry))
                    }
                },
            )
        }
    }
}

/** The quiet offer under the list: what BARS adds, `Подключить` and a cross that hides it for good. */
@Composable
private fun BarsOffer(actions: RecordbookActions) {
    ListGroup(Modifier.testTag(RecordbookTestTags.BARS_OFFER)) { item { BarsOfferRow(actions.onBarsSignIn, actions.onHideOffer) } }
}

/** `Контрольные и минимумы из БАРС` with `Подключить`; with [onHide] also the cross. */
@Composable
internal fun BarsOfferRow(onConnect: () -> Unit, onHide: (() -> Unit)? = null) {
    ListRow(
        title = stringResource(Res.string.recordbook_bars_offer),
        icon = painterResource(KitRes.drawable.ic_assignment),
        iconTone = Tone.Neutral,
        accessory = {
            TextButton(onClick = onConnect) { Text(stringResource(Res.string.recordbook_bars_connect)) }
            if (onHide != null) {
                IconButton(onClick = onHide) {
                    Icon(
                        painterResource(KitRes.drawable.ic_close),
                        contentDescription = stringResource(Res.string.recordbook_bars_hide),
                        tint = ItmoTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

/** A subject: name and reason or kind on the left, the new-mark dot, the number over its source, the chevron. */
@Composable
private fun RecordRow(row: RecordbookRow, actions: RecordbookActions) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { actions.onOpen(row.target) }
            .heightIn(min = ItmoTheme.spacing.touchTarget)
            .padding(start = ItmoTheme.spacing.cardPadding, end = ItmoTheme.spacing.content)
            .padding(vertical = ItmoTheme.spacing.content)
            .testTag(RecordbookTestTags.row(row.id)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.related)) {
            Text(row.name, style = ItmoTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(row.meta.asString(), style = ItmoTheme.typography.bodyMedium, color = row.metaTone.kit().ink())
        }
        if (row.unread) {
            val label = stringResource(Res.string.recordbook_subject_new)
            Box(
                Modifier
                    .size(ItmoTheme.spacing.compact)
                    .clip(CircleShape)
                    .background(ItmoTheme.colorScheme.primary)
                    .semantics { contentDescription = label }
                    .testTag(RecordbookTestTags.NEW_MARK),
            )
        }
        val source = row.source.asString()
        ValueWithSource(
            source = source,
            modifier = Modifier.testTag(RecordbookTestTags.source(row.id)),
            stale = row.stale,
            onSourceClick = if (row.hasSources) ({ actions.onOpenSources(row.id) }) else null,
            sourceLabel = stringResource(Res.string.recordbook_sources_action, source),
        ) { RowValue(row.value) }
        Icon(painterResource(KitRes.drawable.ic_chevron_right), contentDescription = null, tint = ItmoTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun RowValue(value: RecordbookValue) {
    when (value) {
        is RecordbookValue.Points -> PointsMeter(value.text, value.share, value.tone.kit())
        is RecordbookValue.Result -> StatusPill(value.text.asString(), tone = value.tone.kit())
        is RecordbookValue.Sport -> SportRing(value.text, value.visits, value.bonus)
        RecordbookValue.None -> Text(
            stringResource(Res.string.recordbook_score_pending),
            style = ItmoTheme.typography.titleLarge,
            color = ItmoTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The kit's tone of a shared one. */
internal fun RecordbookTone.kit(): Tone = when (this) {
    RecordbookTone.NEUTRAL -> Tone.Neutral
    RecordbookTone.PRIMARY -> Tone.Primary
    RecordbookTone.OK -> Tone.Success
    RecordbookTone.WARN -> Tone.Warning
    RecordbookTone.BAD -> Tone.Error
}
