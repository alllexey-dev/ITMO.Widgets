package dev.alllexey.itmowidgets.feature.recordbook.ui

import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.designsystem.components.expressive.ItmoWavyProgress
import dev.alllexey.itmowidgets.designsystem.components.state.AppRefreshBox
import dev.alllexey.itmowidgets.designsystem.components.state.ContentState
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateAction
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateLoading
import dev.alllexey.itmowidgets.designsystem.components.state.LoadingPulse
import dev.alllexey.itmowidgets.designsystem.kit.ItmoCard
import dev.alllexey.itmowidgets.designsystem.kit.ItmoRootScreen
import dev.alllexey.itmowidgets.designsystem.kit.ListGroup
import dev.alllexey.itmowidgets.designsystem.kit.ListGroupNote
import dev.alllexey.itmowidgets.designsystem.kit.PointsMeter
import dev.alllexey.itmowidgets.designsystem.kit.SectionHeader
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
import dev.alllexey.itmowidgets.shared.compose.strings.Res
import dev.alllexey.itmowidgets.shared.compose.strings.common_load_error_title
import dev.alllexey.itmowidgets.shared.compose.strings.common_retry
import dev.alllexey.itmowidgets.shared.compose.strings.recordbook_bars_connect
import dev.alllexey.itmowidgets.shared.compose.strings.recordbook_bars_expired
import dev.alllexey.itmowidgets.shared.compose.strings.recordbook_bars_failed
import dev.alllexey.itmowidgets.shared.compose.strings.recordbook_bars_hide
import dev.alllexey.itmowidgets.shared.compose.strings.recordbook_bars_loading
import dev.alllexey.itmowidgets.shared.compose.strings.recordbook_bars_offer
import dev.alllexey.itmowidgets.shared.compose.strings.recordbook_bars_sign_in
import dev.alllexey.itmowidgets.shared.compose.strings.recordbook_empty_description
import dev.alllexey.itmowidgets.shared.compose.strings.recordbook_empty_title
import dev.alllexey.itmowidgets.shared.compose.strings.recordbook_period_label
import dev.alllexey.itmowidgets.shared.compose.strings.recordbook_refresh_error
import dev.alllexey.itmowidgets.shared.compose.strings.recordbook_score_pending
import dev.alllexey.itmowidgets.shared.compose.strings.recordbook_session_title
import dev.alllexey.itmowidgets.shared.compose.strings.recordbook_sources_action
import dev.alllexey.itmowidgets.shared.compose.strings.recordbook_subject_new
import dev.alllexey.itmowidgets.shared.designsystem.ic_arrow_drop_down
import dev.alllexey.itmowidgets.shared.designsystem.ic_assignment
import dev.alllexey.itmowidgets.shared.designsystem.ic_check
import dev.alllexey.itmowidgets.shared.designsystem.ic_chevron_right
import dev.alllexey.itmowidgets.shared.designsystem.ic_close
import dev.alllexey.itmowidgets.shared.designsystem.ic_error
import dev.alllexey.itmowidgets.shared.designsystem.ic_history
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.kit.IconTile
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
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
    val onHideOffer: () -> Unit = {},
    val onOpenSources: (id: Long) -> Unit = {},
    val onCloseSources: () -> Unit = {},
)

/**
 * Зачётка (docs/features/recordbook.md): the root header with [title], the period's subtitle and the period action,
 * one list: the session's hero near the end of the semester, then what needs action first and the rest, each number
 * over its quiet source. A source opens `Откуда баллы`. Stateless; [RecordbookRoute] feeds it.
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
                    title = stringResource(Res.string.common_load_error_title),
                    modifier = Modifier.testTag(RecordbookTestTags.ERROR),
                    description = body.error.toUiText().asString(),
                    action = ContentStateAction(stringResource(Res.string.common_retry), actions.onRetry),
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
            BarsRow(
                title = stringResource(if (expired) Res.string.recordbook_bars_expired else Res.string.recordbook_bars_failed),
                subtitle = (notice as? RecordbookBarsNotice.Failed)?.error?.toUiText()?.asString(),
                icon = painterResource(if (expired) KitRes.drawable.ic_history else KitRes.drawable.ic_error),
                iconTone = Tone.Warning,
                action = stringResource(if (expired) Res.string.recordbook_bars_sign_in else Res.string.common_retry),
                onAction = if (expired) onSignIn else onRetry,
            )
        }
    }
}

/** The quiet offer under the list: what BARS adds, `Подключить` and a cross that hides it for good. */
@Composable
private fun BarsOffer(actions: RecordbookActions) {
    ListGroup(Modifier.testTag(RecordbookTestTags.BARS_OFFER)) { item { BarsOfferRow(actions.onBarsSignIn, actions.onHideOffer) } }
}

/** `Контрольные и минимумы из БАРС` over `Подключить`; with [onHide] also the cross. */
@Composable
internal fun BarsOfferRow(onConnect: () -> Unit, onHide: (() -> Unit)? = null) {
    BarsRow(
        title = stringResource(Res.string.recordbook_bars_offer),
        icon = painterResource(KitRes.drawable.ic_assignment),
        iconTone = Tone.Neutral,
        action = stringResource(Res.string.recordbook_bars_connect),
        onAction = onConnect,
        onHide = onHide,
    )
}

/**
 * A BARS row of a group: the icon tile, the [title] and [subtitle] over the [action] text button, the cross at the top
 * end with [onHide]. The text takes the whole width but the cross and the button sits under it, so a narrow phone or a
 * large font never squeezes the title into a column that breaks inside words.
 */
@Composable
private fun BarsRow(
    title: String,
    icon: Painter,
    iconTone: Tone,
    action: String,
    onAction: () -> Unit,
    subtitle: String? = null,
    onHide: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = ItmoTheme.spacing.cardPadding, end = if (onHide != null) ItmoTheme.spacing.related else ItmoTheme.spacing.cardPadding)
            .padding(top = ItmoTheme.spacing.content, bottom = ItmoTheme.spacing.related),
        horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
    ) {
        IconTile(icon, iconTone)
        Column(Modifier.weight(1f).padding(top = ItmoTheme.spacing.compact)) {
            Text(title, style = ItmoTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodyMedium)
            }
            // The label lines up with the title; the button keeps its touch area around it.
            TextButton(onClick = onAction, Modifier.offset(x = -ButtonDefaults.TextButtonContentPadding.calculateStartPadding(LayoutDirection.Ltr))) {
                Text(action)
            }
        }
        if (onHide != null) {
            // Centred on the icon tile: the touch target is taller than the tile.
            IconButton(onClick = onHide, Modifier.offset(y = (IconTileHeight - ItmoTheme.spacing.touchTarget) / 2)) {
                Icon(
                    painterResource(KitRes.drawable.ic_close),
                    contentDescription = stringResource(Res.string.recordbook_bars_hide),
                    tint = ItmoTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The kit's row icon tile. */
private val IconTileHeight = 40.dp

/**
 * A subject: name and reason or kind on the left, the new-mark dot, the number over its source, the chevron. The number
 * moves under the name when beside it a word would not fit, so a narrow phone or a large font never breaks a word.
 */
@Composable
private fun RecordRow(row: RecordbookRow, actions: RecordbookActions) {
    val meta = row.meta.asString()
    val nameStyle = ItmoTheme.typography.titleMedium
    val metaStyle = ItmoTheme.typography.bodyMedium
    val measurer = rememberTextMeasurer()
    val longestWord = remember(row.name, meta, nameStyle, metaStyle, measurer) {
        maxOf(measurer.longestWord(row.name, nameStyle), measurer.longestWord(meta, metaStyle))
    }
    Row(
        Modifier
            .fillMaxWidth()
            // A number BARS brings may need another line; the row grows smoothly instead of jumping.
            .animateContentSize()
            .clickable { actions.onOpen(row.target) }
            .heightIn(min = ItmoTheme.spacing.touchTarget)
            .padding(start = ItmoTheme.spacing.cardPadding, end = ItmoTheme.spacing.content)
            .padding(vertical = ItmoTheme.spacing.content)
            .testTag(RecordbookTestTags.row(row.id)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
    ) {
        NameAndValue(
            name = {
                Column(verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.related)) {
                    Text(row.name, style = nameStyle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(meta, style = metaStyle, color = row.metaTone.kit().ink())
                }
            },
            value = { RecordValue(row, actions) },
            longestWord = longestWord,
            modifier = Modifier.weight(1f),
        )
        Icon(painterResource(KitRes.drawable.ic_chevron_right), contentDescription = null, tint = ItmoTheme.colorScheme.onSurfaceVariant)
    }
}

/** The new-mark dot and the number over its source, pulsing while BARS has not answered yet. */
@Composable
private fun RecordValue(row: RecordbookRow, actions: RecordbookActions) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content)) {
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
        val loading = stringResource(Res.string.recordbook_bars_loading)
        LoadingPulse(row.pending, if (row.pending) Modifier.semantics { stateDescription = loading } else Modifier) {
            ValueWithSource(
                source = source,
                modifier = Modifier.testTag(RecordbookTestTags.source(row.id)),
                stale = row.stale,
                onSourceClick = if (row.hasSources) ({ actions.onOpenSources(row.id) }) else null,
                sourceLabel = stringResource(Res.string.recordbook_sources_action, source),
            ) { RowValue(row.value) }
        }
    }
}

/**
 * [name] beside [value] (both centred) while [longestWord] px still fits beside it; otherwise [value] under [name] at
 * the end, and [name] takes the whole width.
 */
@Composable
private fun NameAndValue(
    name: @Composable () -> Unit,
    value: @Composable () -> Unit,
    longestWord: Int,
    modifier: Modifier = Modifier,
) {
    val gap = ItmoTheme.spacing.content
    Layout(contents = listOf(name, value), modifier) { (names, values), constraints ->
        val gapPx = gap.roundToPx()
        val width = constraints.maxWidth
        val shown = values.single().measure(Constraints(maxWidth = width))
        val beside = width - shown.width - gapPx
        if (beside >= longestWord) {
            val text = names.single().measure(Constraints(maxWidth = beside))
            val height = maxOf(text.height, shown.height)
            layout(width, height) {
                text.place(0, (height - text.height) / 2)
                shown.place(width - shown.width, (height - shown.height) / 2)
            }
        } else {
            val text = names.single().measure(Constraints(maxWidth = width))
            layout(width, text.height + gapPx + shown.height) {
                text.place(0, 0)
                shown.place(width - shown.width, text.height + gapPx)
            }
        }
    }
}

/** The widest single word of [text] in [style], in px. */
private fun TextMeasurer.longestWord(text: String, style: TextStyle): Int =
    text.split(' ').maxOfOrNull { measure(it, style, softWrap = false, maxLines = 1).size.width } ?: 0

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
