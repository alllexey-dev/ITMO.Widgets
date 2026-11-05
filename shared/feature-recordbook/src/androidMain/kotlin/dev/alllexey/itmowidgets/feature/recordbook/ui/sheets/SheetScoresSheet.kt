package dev.alllexey.itmowidgets.feature.recordbook.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.designsystem.components.buttons.ButtonRow
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButton
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButtonStyle
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetScaffold
import dev.alllexey.itmowidgets.designsystem.components.state.ContentState
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateAction
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateLoading
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateSize
import dev.alllexey.itmowidgets.designsystem.kit.ChipSpec
import dev.alllexey.itmowidgets.designsystem.kit.FilterChips
import dev.alllexey.itmowidgets.designsystem.kit.ItmoCard
import dev.alllexey.itmowidgets.designsystem.kit.Tone
import dev.alllexey.itmowidgets.designsystem.kit.ink
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetCell
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetRowMatch
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetStatus
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetTab
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetText
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.SheetAiState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.SheetColumnOption
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.SheetSavedTotal
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.SheetScoresUiState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.title
import dev.alllexey.itmowidgets.shared.compose.strings.Res
import dev.alllexey.itmowidgets.shared.compose.strings.common_retry
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_ai_analyze
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_ai_check
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_ai_dismiss
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_ai_running
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_ai_running_note
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_ai_title
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_ai_use
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_formula_sum
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_scores_empty_cell
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_scores_empty_columns
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_scores_no_values
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_scores_official
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_scores_offline
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_scores_other_cell
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_scores_pick_row
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_scores_pick_tab
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_scores_pick_total
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_scores_reset_auto
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_scores_save
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_scores_search_clear
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_scores_search_empty
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_scores_search_hint
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_scores_sum_hint
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_scores_sum_mode
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_scores_title
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes
import dev.alllexey.itmowidgets.shared.designsystem.ic_auto_awesome
import dev.alllexey.itmowidgets.shared.designsystem.ic_close
import dev.alllexey.itmowidgets.shared.designsystem.ic_error
import dev.alllexey.itmowidgets.shared.designsystem.ic_expand_more
import dev.alllexey.itmowidgets.shared.designsystem.ic_search
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** What the scores sheet (`sheet_scores_title`) asks its host to do; every effect is the host's (the view model's actions). */
@Immutable
class SheetScoresActions(
    val onRetry: () -> Unit = {},
    val onPickRow: (SheetRowMatch) -> Unit = {},
    val onPickTab: (SheetTab) -> Unit = {},
    val onPickTotal: (SheetCell) -> Unit = {},
    val onResetTotal: () -> Unit = {},
    val onSumming: (Boolean) -> Unit = {},
    val onSave: () -> Unit = {},
    val onAnalyze: () -> Unit = {},
    val onUseAi: () -> Unit = {},
    val onDismissAi: () -> Unit = {},
)

/** Test tags of [SheetScoresSheet]. */
object SheetScoresSheetTestTags {
    const val SEARCH = "sheet_scores_search"
    const val SEARCH_CLEAR = "sheet_scores_search_clear"
    const val LIST = "sheet_scores_list"
    const val RETRY = "sheet_scores_retry"
    const val RESET = "sheet_scores_reset"
    const val OTHER_CELL = "sheet_scores_other_cell"
    const val SAVE = "sheet_scores_save"
    const val SUM_MODE = "sheet_scores_sum_mode"
    const val EMPTY_COLUMNS = "sheet_scores_empty_columns"
    const val AI = "sheet_scores_ai"
    const val AI_USE = "sheet_scores_ai_use"
    const val AI_DISMISS = "sheet_scores_ai_dismiss"
}

/**
 * The scores sheet (`sheet_scores_title`): the title over [subjectName], then one bounded area where loading, a
 * failure and the choices take turns without moving the sheet: the own row among several, the tab to look in, a row of
 * that tab, the total among the own row's cells by tab. A long list of people gets a search by name whose text
 * survives the state changes and recreation, as does the open column list of the total's review. Cell values show as
 * the sheet has them; the review's «Сохранить» sits under the area. The host owns the container,
 * [SheetScoresUiState.Done] (it closes the sheet) and the save failure.
 */
@Composable
fun SheetScoresSheet(
    subjectName: String,
    state: SheetScoresUiState,
    actions: SheetScoresActions,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    SheetScaffold(
        title = stringResource(Res.string.sheet_scores_title),
        subtitle = subjectName,
        modifier = modifier,
        contentMinHeight = ContentMinHeight,
        footer = (state as? SheetScoresUiState.PickTotal)?.let { review -> { SaveButton(review, actions.onSave) } },
    ) {
        when (state) {
            SheetScoresUiState.Loading -> ContentStateLoading(StateArea, ContentStateSize.Compact)
            is SheetScoresUiState.Failed -> Failure(state.status, actions.onRetry)
            is SheetScoresUiState.PickRow -> Rows(state.candidates, query, { query = it }, actions.onPickRow)
            is SheetScoresUiState.PickTabRow -> Rows(state.rows, query, { query = it }, actions.onPickRow)
            is SheetScoresUiState.PickTab -> Choices(Res.string.sheet_scores_pick_tab, tabOptions(state.tabs, actions))
            is SheetScoresUiState.PickTotal -> TotalReview(state, actions)
            // The host closes the sheet; the area stays empty until then.
            SheetScoresUiState.Done -> Unit
        }
    }
}

/** One row of the choices: a person or a tab. */
private data class SheetOption(val key: String, val title: String, val caption: String? = null, val onClick: () -> Unit)

/** The item_content_state of the View: the reading's failure, with a retry only when the network failed. */
@Composable
private fun Failure(status: SheetStatus, onRetry: () -> Unit) {
    ContentState(
        title = stringResource(status.labelResource() ?: Res.string.sheet_scores_offline),
        modifier = StateArea,
        size = ContentStateSize.Compact,
        icon = painterResource(KitRes.drawable.ic_error),
        action = if (status == SheetStatus.NETWORK) {
            ContentStateAction(
                stringResource(Res.string.common_retry),
                onRetry,
                modifier = Modifier.testTag(SheetScoresSheetTestTags.RETRY),
            )
        } else {
            null
        },
    )
}

/** The people to choose from, each with its tab under the name; more than [SEARCH_FROM] get the search. */
@Composable
private fun ColumnScope.Rows(
    rows: List<SheetRowMatch>,
    query: String,
    onQuery: (String) -> Unit,
    onPick: (SheetRowMatch) -> Unit,
) {
    val searchable = rows.size > SEARCH_FROM
    val needle = if (searchable) SheetText.normalize(query) else ""
    val shown = if (needle.isEmpty()) rows else rows.filter { needle in SheetText.normalize(it.label) }
    Prompt(Res.string.sheet_scores_pick_row)
    if (searchable) Search(query, onQuery)
    if (shown.isEmpty()) Empty(Res.string.sheet_scores_search_empty)
    OptionList(shown.map { match -> rowOption(match, onPick) })
}

/** Tabs or cells; an empty list says the row has no values. */
@Composable
private fun ColumnScope.Choices(prompt: StringResource, options: List<SheetOption>) {
    Prompt(prompt)
    if (options.isEmpty()) Empty(Res.string.sheet_scores_no_values)
    OptionList(options)
}

/** The name in the row with its tab under it, so a long tab name does not bury the name. */
private fun rowOption(match: SheetRowMatch, onPick: (SheetRowMatch) -> Unit) = SheetOption(
    key = "row:${match.tab.gid}:${match.row}",
    title = match.label,
    caption = match.tab.name.takeIf { it.isNotBlank() },
) { onPick(match) }

@Composable
private fun tabOptions(tabs: List<SheetTab>, actions: SheetScoresActions): List<SheetOption> {
    val untitled = stringResource(Res.string.sheet_scores_pick_tab)
    return tabs.map { tab ->
        SheetOption("tab:${tab.gid}", tab.name.ifBlank { untitled }) { actions.onPickTab(tab) }
    }
}

@Composable
private fun Prompt(text: StringResource) {
    Text(
        stringResource(text),
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ItmoTheme.spacing.screenMargin)
            .padding(top = ItmoTheme.spacing.compact)
            .semantics { heading() },
        color = ItmoTheme.colorScheme.primary,
        style = ItmoTheme.typography.titleSmall,
    )
}

/** The name search: it filters the rows by [SheetText.normalize], so case, the letter yo and spacing do not matter. */
@Composable
private fun Search(query: String, onQuery: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ItmoTheme.spacing.screenMargin)
            .padding(top = ItmoTheme.spacing.compact)
            .testTag(SheetScoresSheetTestTags.SEARCH),
        label = { Text(stringResource(Res.string.sheet_scores_search_hint)) },
        leadingIcon = { Icon(painterResource(KitRes.drawable.ic_search), contentDescription = null) },
        trailingIcon = if (query.isEmpty()) {
            null
        } else {
            {
                IconButton(
                    onClick = { onQuery("") },
                    modifier = Modifier.testTag(SheetScoresSheetTestTags.SEARCH_CLEAR),
                ) {
                    Icon(
                        painterResource(KitRes.drawable.ic_close),
                        contentDescription = stringResource(Res.string.sheet_scores_search_clear),
                    )
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
    )
}

@Composable
private fun Empty(text: StringResource) {
    Text(
        stringResource(text),
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ItmoTheme.spacing.screenMargin)
            .padding(top = ItmoTheme.spacing.group),
        color = ItmoTheme.colorScheme.onSurfaceVariant,
        style = ItmoTheme.typography.bodyMedium,
    )
}

/** The options under the prompt; the list scrolls inside what the sheet has left, never past it. */
@Composable
private fun ColumnScope.OptionList(options: List<SheetOption>) {
    LazyColumn(
        Modifier
            .weight(1f, fill = false)
            .fillMaxWidth()
            .padding(top = ItmoTheme.spacing.related)
            .testTag(SheetScoresSheetTestTags.LIST),
    ) {
        items(options, key = { it.key }) { option -> ChoiceRow(option) }
    }
}

/** The tab's name over its cells. */
@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ItmoTheme.spacing.screenMargin)
            .padding(top = ItmoTheme.spacing.group, bottom = ItmoTheme.spacing.related)
            .semantics { heading() },
        color = ItmoTheme.colorScheme.onSurfaceVariant,
        style = ItmoTheme.typography.titleSmall,
    )
}

/** One option: the whole row is the 48 dp target, read once as a button with its title and caption. */
@Composable
private fun ChoiceRow(option: SheetOption) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ItmoTheme.spacing.touchTarget)
            .clickable(role = Role.Button, onClick = option.onClick)
            .padding(horizontal = ItmoTheme.spacing.screenMargin, vertical = ItmoTheme.spacing.content),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(option.title, color = ItmoTheme.colorScheme.onSurface, style = ItmoTheme.typography.bodyLarge)
            option.caption?.let { caption ->
                Text(
                    caption,
                    Modifier.padding(top = ItmoTheme.spacing.related),
                    color = ItmoTheme.colorScheme.onSurfaceVariant,
                    style = ItmoTheme.typography.bodySmall,
                )
            }
        }
    }
}

/**
 * The total's review: the total with its tab and why it is the total (a reason to check in the warning colour) or a
 * saved sum in words, `Разобрать с ИИ` and its result for a shared sheet, the own row around the total, the candidate columns
 * (the automatic one marked) and «Другая ячейка» as chips, «Вернуть авто» for a hand-picked total. «Другая ячейка», or
 * a review without a single-cell total, opens the column list: the «Сложить столбцы» switch, the tab switcher, every
 * column with the own value and how full it is, the empty ones folded. A tap selects; «Сохранить» saves.
 */
@Composable
private fun ColumnScope.TotalReview(state: SheetScoresUiState.PickTotal, actions: SheetScoresActions) {
    var picker by rememberSaveable { mutableStateOf(false) }
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var emptyOpen by rememberSaveable { mutableStateOf(false) }
    val shown = state.shown
    val tabs = state.tabs
    val tab = tabs.getOrNull(tabIndex) ?: tabs.firstOrNull()
    val selected = { cell: SheetCell -> if (state.summing) cell in state.summed else cell == shown }
    LazyColumn(
        Modifier
            .weight(1f, fill = false)
            .fillMaxWidth()
            .padding(bottom = ItmoTheme.spacing.content)
            .testTag(SheetScoresSheetTestTags.LIST),
        verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
    ) {
        item(key = "prompt") { Prompt(Res.string.sheet_scores_pick_total) }
        when {
            shown != null -> item(key = "total") { TotalSummary(state, shown) }
            state.saved != null -> item(key = "saved") { SavedSummary(state.saved) }
        }
        if (state.ai != SheetAiState.Hidden) item(key = "ai") { AiBlock(state.ai, actions) }
        if (shown != null) item(key = "preview") { RowPreview(state.preview, shown, actions.onPickTotal) }
        if (shown != null || state.saved != null) {
            item(key = "chips") {
                val candidates = state.candidates
                FilterChips(
                    chips = candidates.map { ChipSpec(state.chip(it).asString(), !state.summing && it == shown) } +
                        ChipSpec(stringResource(Res.string.sheet_scores_other_cell), picker),
                    onClick = { index -> candidates.getOrNull(index)?.let(actions.onPickTotal) ?: run { picker = !picker } },
                    modifier = Modifier.padding(horizontal = ItmoTheme.spacing.screenMargin).testTag(SheetScoresSheetTestTags.OTHER_CELL),
                    wrap = true,
                )
            }
        }
        if (state.canReset) {
            item(key = "reset") {
                TextButton(
                    onClick = actions.onResetTotal,
                    modifier = Modifier.padding(horizontal = ItmoTheme.spacing.compact).testTag(SheetScoresSheetTestTags.RESET),
                ) { Text(stringResource(Res.string.sheet_scores_reset_auto)) }
            }
        }
        if (picker || (shown == null && state.saved == null) || state.summing) {
            if (state.cells.none { it.value.isNotEmpty() }) item(key = "empty") { Empty(Res.string.sheet_scores_no_values) }
            item(key = "mode") {
                FilterChips(
                    chips = listOf(ChipSpec(stringResource(Res.string.sheet_scores_sum_mode), state.summing)),
                    onClick = { actions.onSumming(!state.summing) },
                    modifier = Modifier.padding(horizontal = ItmoTheme.spacing.screenMargin).testTag(SheetScoresSheetTestTags.SUM_MODE),
                )
            }
            if (state.summing) item(key = "sum") { SumLine(state) }
            if (tabs.size > 1) {
                item(key = "tabs") {
                    FilterChips(
                        chips = tabs.map { ChipSpec(it.name.ifBlank { stringResource(Res.string.sheet_scores_pick_tab) }, it == tab) },
                        onClick = { tabIndex = it },
                        modifier = Modifier.padding(horizontal = ItmoTheme.spacing.screenMargin),
                    )
                }
            }
            if (tab != null) {
                val filled = state.filledColumns(tab)
                val empty = state.emptyColumns(tab)
                items(filled, key = { "column:${it.cell.tab.gid}:${it.cell.column}" }) { option ->
                    ColumnRow(option, selected(option.cell), state.summing, actions.onPickTotal)
                }
                if (empty.isNotEmpty()) {
                    item(key = "empty-columns:${tab.gid}") {
                        FoldRow(stringResource(Res.string.sheet_scores_empty_columns, empty.size), emptyOpen) { emptyOpen = !emptyOpen }
                    }
                    if (emptyOpen) {
                        items(empty, key = { "column:${it.cell.tab.gid}:${it.cell.column}" }) { option ->
                            ColumnRow(option, selected(option.cell), state.summing, actions.onPickTotal)
                        }
                    }
                }
            }
        }
    }
}

/** «Сохранить» under the review, enabled once there is something new to save. */
@Composable
private fun SaveButton(state: SheetScoresUiState.PickTotal, onSave: () -> Unit) {
    ProgressButton(
        label = stringResource(Res.string.sheet_scores_save),
        onClick = onSave,
        enabled = state.canSave,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ItmoTheme.spacing.screenMargin)
            .padding(top = ItmoTheme.spacing.compact)
            .testTag(SheetScoresSheetTestTags.SAVE),
    )
}

/** The total as the sheet shows it, its column and tab, and why it is the total. */
@Composable
private fun TotalSummary(state: SheetScoresUiState.PickTotal, shown: SheetCell) {
    Column(Modifier.fillMaxWidth().padding(horizontal = ItmoTheme.spacing.screenMargin).semantics(mergeDescendants = true) {}) {
        Text(
            shown.value.ifEmpty { stringResource(Res.string.sheet_scores_empty_cell) },
            color = ItmoTheme.colorScheme.onSurface,
            style = ItmoTheme.typography.headlineMedium,
        )
        Text(
            sheetScoreCaption(shown.tab.name, title(shown).asString()),
            color = ItmoTheme.colorScheme.onSurfaceVariant,
            style = ItmoTheme.typography.bodyMedium,
        )
        state.note?.let { note ->
            Text(
                note.asString(),
                Modifier.padding(top = ItmoTheme.spacing.related),
                color = if (state.unsure) Tone.Warning.ink() else ItmoTheme.colorScheme.onSurfaceVariant,
                style = ItmoTheme.typography.bodySmall,
            )
        }
    }
}

/** A saved sum or AI rule: its value, the formula in words and the teacher's total beside it. */
@Composable
private fun SavedSummary(saved: SheetSavedTotal) {
    Column(Modifier.fillMaxWidth().padding(horizontal = ItmoTheme.spacing.screenMargin).semantics(mergeDescendants = true) {}) {
        Text(
            saved.value ?: stringResource(Res.string.sheet_scores_empty_cell),
            color = ItmoTheme.colorScheme.onSurface,
            style = ItmoTheme.typography.headlineMedium,
        )
        Text(saved.formula.asString(), color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodyMedium)
        saved.official?.let { official ->
            Text(
                stringResource(Res.string.sheet_scores_official, official),
                color = ItmoTheme.colorScheme.onSurfaceVariant,
                style = ItmoTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * `Разобрать с ИИ`: the button, its progress while Backend analyses the sheet, the proposal (the formula in words, the
 * own value, the teacher's total, the model's summary and warnings, `Использовать` / `Нет`) or why there is none.
 */
@Composable
private fun AiBlock(ai: SheetAiState, actions: SheetScoresActions) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = ItmoTheme.spacing.screenMargin).testTag(SheetScoresSheetTestTags.AI),
        verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.related),
    ) {
        when (ai) {
            SheetAiState.Hidden -> Unit
            SheetAiState.Offer, SheetAiState.Running, is SheetAiState.Failed -> {
                ProgressButton(
                    label = stringResource(Res.string.sheet_ai_analyze),
                    onClick = actions.onAnalyze,
                    style = ProgressButtonStyle.Tonal,
                    inProgress = ai == SheetAiState.Running,
                    icon = painterResource(KitRes.drawable.ic_auto_awesome),
                )
                val note = when (ai) {
                    SheetAiState.Running -> stringResource(Res.string.sheet_ai_running) + " " + stringResource(Res.string.sheet_ai_running_note)
                    is SheetAiState.Failed -> ai.text.asString()
                    else -> null
                }
                note?.let {
                    Text(it, color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodySmall)
                }
            }
            is SheetAiState.Proposal -> ItmoCard {
                Column(
                    Modifier.padding(ItmoTheme.spacing.content),
                    verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.related),
                ) {
                    Text(
                        stringResource(Res.string.sheet_ai_title),
                        Modifier.semantics { heading() },
                        color = ItmoTheme.colorScheme.primary,
                        style = ItmoTheme.typography.titleSmall,
                    )
                    Text(
                        ai.value ?: stringResource(Res.string.sheet_scores_empty_cell),
                        color = ItmoTheme.colorScheme.onSurface,
                        style = ItmoTheme.typography.headlineMedium,
                    )
                    Text(ai.formula.asString(), color = ItmoTheme.colorScheme.onSurface, style = ItmoTheme.typography.bodyMedium)
                    if (ai.hasOfficial) {
                        Text(
                            stringResource(Res.string.sheet_scores_official, ai.official ?: stringResource(Res.string.sheet_scores_empty_cell)),
                            color = ItmoTheme.colorScheme.onSurfaceVariant,
                            style = ItmoTheme.typography.bodyMedium,
                        )
                    }
                    if (ai.summary.isNotBlank()) {
                        Text(ai.summary, color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodySmall)
                    }
                    (ai.warnings + stringResource(Res.string.sheet_ai_check)).forEach { warning ->
                        Text(warning, color = Tone.Warning.ink(), style = ItmoTheme.typography.bodySmall)
                    }
                    ButtonRow(Modifier.fillMaxWidth().padding(top = ItmoTheme.spacing.related)) {
                        ProgressButton(
                            label = stringResource(Res.string.sheet_ai_use),
                            onClick = actions.onUseAi,
                            modifier = Modifier.testTag(SheetScoresSheetTestTags.AI_USE),
                        )
                        ProgressButton(
                            label = stringResource(Res.string.sheet_ai_dismiss),
                            onClick = actions.onDismissAi,
                            style = ProgressButtonStyle.Text,
                            modifier = Modifier.testTag(SheetScoresSheetTestTags.AI_DISMISS),
                        )
                    }
                }
            }
        }
    }
}

/** The own sum of the columns picked so far, or how to pick them. */
@Composable
private fun SumLine(state: SheetScoresUiState.PickTotal) {
    val text = state.sum?.let { stringResource(Res.string.sheet_formula_sum, it) } ?: stringResource(Res.string.sheet_scores_sum_hint)
    Text(
        text,
        Modifier.fillMaxWidth().padding(horizontal = ItmoTheme.spacing.screenMargin),
        color = if (state.sum != null) ItmoTheme.colorScheme.onSurface else ItmoTheme.colorScheme.onSurfaceVariant,
        style = if (state.sum != null) ItmoTheme.typography.titleMedium else ItmoTheme.typography.bodyMedium,
    )
}

/**
 * One column: a radio button (a checkbox while summing), its header path over how full it is, the own value at the end;
 * the whole row is the 48 dp target.
 */
@Composable
private fun ColumnRow(option: SheetColumnOption, selected: Boolean, summing: Boolean, onPick: (SheetCell) -> Unit) {
    val cell = option.cell
    val modifier = if (summing) {
        Modifier.toggleable(selected, role = Role.Checkbox) { onPick(cell) }
    } else {
        Modifier.selectable(selected, role = Role.RadioButton) { onPick(cell) }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ItmoTheme.spacing.touchTarget)
            .then(modifier)
            .padding(horizontal = ItmoTheme.spacing.screenMargin, vertical = ItmoTheme.spacing.related),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
    ) {
        if (summing) Checkbox(selected, onCheckedChange = null) else RadioButton(selected, onClick = null)
        Column(Modifier.weight(1f)) {
            Text(title(cell).asString(), color = ItmoTheme.colorScheme.onSurface, style = ItmoTheme.typography.bodyLarge)
            Text(option.summary.asString(), color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodySmall)
        }
        Text(
            cell.value.ifEmpty { stringResource(Res.string.sheet_scores_empty_cell) },
            color = ItmoTheme.colorScheme.onSurface,
            style = ItmoTheme.typography.titleSmall,
        )
    }
}

/** «Пустые столбцы · 3»: opens or folds the columns nobody has a value in. */
@Composable
private fun FoldRow(label: String, open: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ItmoTheme.spacing.touchTarget)
            .clickable(role = Role.Button, onClick = onToggle)
            .padding(horizontal = ItmoTheme.spacing.screenMargin)
            .testTag(SheetScoresSheetTestTags.EMPTY_COLUMNS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.titleSmall)
        Icon(
            painterResource(KitRes.drawable.ic_expand_more),
            contentDescription = null,
            modifier = Modifier.rotate(if (open) 180f else 0f),
            tint = ItmoTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The own row around the total with the headers, scrolled so that the total shows with one cell before it. */
@Composable
private fun RowPreview(cells: List<SheetCell>, shown: SheetCell, onPick: (SheetCell) -> Unit) {
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (cells.indexOf(shown) - 1).coerceAtLeast(0))
    LazyRow(
        Modifier.fillMaxWidth(),
        state = list,
        contentPadding = PaddingValues(horizontal = ItmoTheme.spacing.screenMargin),
        horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact),
    ) {
        items(cells, key = { "${it.tab.gid}:${it.column}" }) { cell -> CellTile(cell, cell == shown, onPick) }
    }
}

/** One cell: its header over its value, a selectable 48 dp target; the total on the primary container. */
@Composable
private fun CellTile(cell: SheetCell, selected: Boolean, onPick: (SheetCell) -> Unit) {
    val container = if (selected) ItmoTheme.colorScheme.primaryContainer else ItmoTheme.colorScheme.surfaceContainerHigh
    val content = if (selected) ItmoTheme.colorScheme.onPrimaryContainer else ItmoTheme.colorScheme.onSurface
    Column(
        Modifier
            .width(CellWidth)
            .heightIn(min = CellMinHeight)
            .clip(ItmoTheme.shapes.medium)
            .background(container)
            .selectable(selected, role = Role.RadioButton) { onPick(cell) }
            .padding(ItmoTheme.spacing.compact),
        verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.related),
    ) {
        Text(
            title(cell).asString(),
            color = content.copy(alpha = HeaderAlpha),
            style = ItmoTheme.typography.labelSmall,
        )
        Text(
            cell.value.ifEmpty { stringResource(Res.string.sheet_scores_empty_cell) },
            color = content,
            style = ItmoTheme.typography.titleSmall,
        )
    }
}

/** More people than this get the name search. */
private const val SEARCH_FROM = 8

/** `sheet_scores_content_min_height`: loading, a failure and the choices share this much, so nothing jumps. */
private val ContentMinHeight = 288.dp

/** Loading and failures centre in the whole shared area. */
private val StateArea = Modifier.heightIn(min = ContentMinHeight)


/** `sheet_scores_cell_width`: about seven characters of a header on a line, three cells across a narrow phone. */
private val CellWidth = 112.dp

private val CellMinHeight = 56.dp

private const val HeaderAlpha = 0.8f
