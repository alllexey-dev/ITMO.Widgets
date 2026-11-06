package dev.alllexey.itmowidgets.feature.recordbook.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.presentation.GroupPosition
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButton
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButtonStyle
import dev.alllexey.itmowidgets.designsystem.components.controls.ItmoActivityIndicator
import dev.alllexey.itmowidgets.designsystem.components.groups.GroupSurface
import dev.alllexey.itmowidgets.designsystem.components.groups.connectedGroupItem
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetClose
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetClosePlacement
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetScaffold
import dev.alllexey.itmowidgets.designsystem.components.state.ContentState
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateAction
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateLoading
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateSize
import dev.alllexey.itmowidgets.designsystem.kit.ChipSpec
import dev.alllexey.itmowidgets.designsystem.kit.FilterChips
import dev.alllexey.itmowidgets.designsystem.kit.ItmoCard
import dev.alllexey.itmowidgets.designsystem.kit.ListGroupNote
import dev.alllexey.itmowidgets.designsystem.kit.ListRow
import dev.alllexey.itmowidgets.designsystem.kit.SearchField
import dev.alllexey.itmowidgets.designsystem.kit.SectionHeader
import dev.alllexey.itmowidgets.designsystem.kit.Tone
import dev.alllexey.itmowidgets.designsystem.kit.ink
import dev.alllexey.itmowidgets.designsystem.kit.tabular
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetCell
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetRowMatch
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetStatus
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetTab
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetText
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.SheetAiState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.SheetScoresUiState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.SheetTotalHero
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.TotalAction
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.TotalOption
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.TotalPage
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.canGoBack
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.groupTitle
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.reason
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.shortTitle
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.text
import dev.alllexey.itmowidgets.shared.compose.strings.Res
import dev.alllexey.itmowidgets.shared.compose.strings.common_back
import dev.alllexey.itmowidgets.shared.compose.strings.common_retry
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_ai_analyze
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_ai_less
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_ai_more
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_ai_offer_note
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_ai_running
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_scores_empty_cell
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_scores_empty_columns
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_scores_offline
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_scores_pick_row
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_scores_pick_row_note
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_scores_pick_tab
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_scores_pick_tab_note
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_scores_save
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_scores_search_clear
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_scores_search_empty
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_scores_search_hint
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_scores_sum_hint
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_scores_sum_mode
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_scores_tab_untitled
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_scores_title
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_total_done
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_total_find
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_total_label
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_total_none
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_total_other
import dev.alllexey.itmowidgets.shared.compose.strings.sheet_total_pick_column
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes
import dev.alllexey.itmowidgets.shared.designsystem.ic_add
import dev.alllexey.itmowidgets.shared.designsystem.ic_auto_awesome
import dev.alllexey.itmowidgets.shared.designsystem.ic_check
import dev.alllexey.itmowidgets.shared.designsystem.ic_cloud_off
import dev.alllexey.itmowidgets.shared.designsystem.ic_error
import dev.alllexey.itmowidgets.shared.designsystem.ic_expand_more
import dev.alllexey.itmowidgets.shared.designsystem.ic_table
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** What the scores sheet (`sheet_scores_title`) asks its host to do; every effect is the host's (the view model's actions). */
@Immutable
class SheetScoresActions(
    val onRetry: () -> Unit = {},
    val onPickRow: (SheetRowMatch) -> Unit = {},
    val onPickTab: (SheetTab) -> Unit = {},
    val onSelect: (TotalOption) -> Unit = {},
    val onOpenColumns: () -> Unit = {},
    val onOpenSum: () -> Unit = {},
    val onPickColumn: (SheetCell) -> Unit = {},
    val onToggleSum: (SheetCell) -> Unit = {},
    val onFinishSum: () -> Unit = {},
    val onAnalyze: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/** Test tags of [SheetScoresSheet]. */
object SheetScoresSheetTestTags {
    const val SEARCH = "sheet_scores_search"
    const val LIST = "sheet_scores_list"
    const val RETRY = "sheet_scores_retry"
    const val HERO = "sheet_scores_hero"
    const val SAVE = "sheet_scores_save"
    const val COLUMNS = "sheet_scores_columns"
    const val SUM = "sheet_scores_sum"
    const val AI = "sheet_scores_ai"
    const val AI_DETAILS = "sheet_scores_ai_details"
    const val EMPTY_COLUMNS = "sheet_scores_empty_columns"
}

/**
 * The scores sheet (`sheet_scores_title`): one question per step in one bounded area, so loading, a failure and the
 * steps take turns without moving the sheet. The own row among several, the tab to look in and its rows (with a name
 * search over eight); then the total: the chosen one on top with where it comes from, the other ready totals and the
 * ways to another one (a column, a sum, the AI), one button under the area. The column list and the sum are pages
 * with a back arrow. The host owns the container, [SheetScoresUiState.Done] (it closes the sheet), Back and the save
 * failure.
 */
@Composable
fun SheetScoresSheet(
    subjectName: String,
    state: SheetScoresUiState,
    actions: SheetScoresActions,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val back = state.canGoBack
    val total = state as? SheetScoresUiState.PickTotal
    SheetScaffold(
        title = stringResource(
            when (total?.page) {
                TotalPage.COLUMNS -> Res.string.sheet_total_pick_column
                TotalPage.SUM -> Res.string.sheet_scores_sum_mode
                else -> Res.string.sheet_scores_title
            },
        ),
        subtitle = subjectName,
        modifier = modifier,
        close = if (back) SheetClose(stringResource(Res.string.common_back), back = true, onClick = actions.onBack) else null,
        closePlacement = if (back) SheetClosePlacement.Start else SheetClosePlacement.End,
        contentMinHeight = ContentMinHeight,
        footer = total?.action?.let { action -> { Footer(total, action, actions) } },
    ) {
        when (state) {
            SheetScoresUiState.Loading -> ContentStateLoading(StateArea, ContentStateSize.Compact)
            is SheetScoresUiState.Failed -> Failure(state.status, actions.onRetry)
            is SheetScoresUiState.PickRow ->
                Rows(state.candidates, Res.string.sheet_scores_pick_row_note, withTab = true, query, { query = it }, actions.onPickRow)
            is SheetScoresUiState.PickTabRow -> Rows(state.rows, null, withTab = false, query, { query = it }, actions.onPickRow)
            is SheetScoresUiState.PickTab -> Tabs(state.tabs, actions.onPickTab)
            is SheetScoresUiState.PickTotal -> when (state.page) {
                TotalPage.OVERVIEW -> Overview(state, actions)
                TotalPage.COLUMNS -> Columns(state, actions.onPickColumn)
                TotalPage.SUM -> Sum(state, actions.onToggleSum)
            }
            // The host closes the sheet; the area stays empty until then.
            SheetScoresUiState.Done -> Unit
        }
    }
}

/** A failed reading: what happened, why when it is not obvious, and a retry only when the network failed. */
@Composable
private fun Failure(status: SheetStatus, onRetry: () -> Unit) {
    val offline = status == SheetStatus.NETWORK
    ContentState(
        title = status.text()?.asString() ?: stringResource(Res.string.sheet_scores_offline),
        modifier = StateArea,
        size = ContentStateSize.Compact,
        icon = painterResource(if (offline) KitRes.drawable.ic_cloud_off else KitRes.drawable.ic_error),
        description = status.reason()?.asString(),
        action = if (offline) {
            ContentStateAction(stringResource(Res.string.common_retry), onRetry, modifier = Modifier.testTag(SheetScoresSheetTestTags.RETRY))
        } else {
            null
        },
    )
}

/** «Какая строка ваша?»: the people to choose from; more than [SEARCH_FROM] get the search by name. */
@Composable
private fun ColumnScope.Rows(
    rows: List<SheetRowMatch>,
    note: StringResource?,
    withTab: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    onPick: (SheetRowMatch) -> Unit,
) {
    val searchable = rows.size > SEARCH_FROM
    val needle = if (searchable) SheetText.normalize(query) else ""
    val shown = if (needle.isEmpty()) rows else rows.filter { needle in SheetText.normalize(it.label) }
    val noteText = note?.let { stringResource(it) }
    val placeholder = stringResource(Res.string.sheet_scores_search_hint)
    val clear = stringResource(Res.string.sheet_scores_search_clear)
    val nobody = stringResource(Res.string.sheet_scores_search_empty)
    val question = stringResource(Res.string.sheet_scores_pick_row)
    Body {
        item(key = "question") { Question(question, noteText) }
        if (searchable) {
            item(key = "search") {
                SearchField(query, onQuery, placeholder, clear, Modifier.padding(top = ItmoTheme.spacing.content).testTag(SheetScoresSheetTestTags.SEARCH))
            }
        }
        gap("rows")
        if (shown.isEmpty()) item(key = "nobody") { ListGroupNote(nobody) }
        group(shown, key = { "row:${it.tab.gid}:${it.row}" }) { match, position ->
            ListRow(
                match.label,
                position,
                subtitle = match.tab.name.takeIf { withTab && it.isNotBlank() },
                onClick = { onPick(match) },
            )
        }
    }
}

/** «На каком листе ваша группа?»: the tabs with students. */
@Composable
private fun ColumnScope.Tabs(tabs: List<SheetTab>, onPick: (SheetTab) -> Unit) {
    val question = stringResource(Res.string.sheet_scores_pick_tab)
    val note = stringResource(Res.string.sheet_scores_pick_tab_note)
    val untitled = stringResource(Res.string.sheet_scores_tab_untitled)
    Body {
        item(key = "question") { Question(question, note) }
        gap("tabs")
        group(tabs, key = { "tab:${it.gid}" }) { tab, position ->
            ListRow(tab.name.ifBlank { untitled }, position, chevron = true, onClick = { onPick(tab) })
        }
    }
}

/**
 * The total: the chosen one on top (or why there is none), then under «Другой итог» the other ready totals with their
 * values and the ways to another one: a column, a sum, the AI for a shared sheet.
 */
@Composable
private fun ColumnScope.Overview(state: SheetScoresUiState.PickTotal, actions: SheetScoresActions) {
    val hero = state.hero
    val variants = state.variants
    val ways = buildList {
        add(Way.COLUMN)
        add(Way.SUM)
        if (state.ai is SheetAiState.Offer || state.ai is SheetAiState.Running || state.ai is SheetAiState.Failed) add(Way.AI)
    }
    val heading = stringResource(if (hero == null) Res.string.sheet_total_find else Res.string.sheet_total_other)
    val missing = state.missing.asString()
    Body {
        item(key = "hero") { if (hero != null) Hero(hero) else Missing(missing) }
        gap("heading") { ItmoTheme.spacing.group }
        item(key = "heading") { SectionHeader(heading) }
        gap("variants") { ItmoTheme.spacing.compact }
        group(variants, key = { variantKey(it.option) }) { variant, position ->
            ListRow(
                variant.title.asString(),
                position.semantics(mergeDescendants = true) {},
                subtitle = variant.subtitle?.asString(),
                accessory = { Value(variant.value) },
                onClick = { actions.onSelect(variant.option) },
            )
        }
        if (variants.isNotEmpty()) gap("ways")
        group(ways, key = { it.name }) { way, position -> WayRow(way, state.ai, actions, position) }
    }
}

private enum class Way { COLUMN, SUM, AI }

@Composable
private fun WayRow(way: Way, ai: SheetAiState, actions: SheetScoresActions, modifier: Modifier) {
    when (way) {
        Way.COLUMN -> ListRow(
            stringResource(Res.string.sheet_total_pick_column),
            modifier.testTag(SheetScoresSheetTestTags.COLUMNS),
            icon = painterResource(KitRes.drawable.ic_table),
            iconTone = Tone.Neutral,
            chevron = true,
            onClick = actions.onOpenColumns,
        )
        Way.SUM -> ListRow(
            stringResource(Res.string.sheet_scores_sum_mode),
            modifier.testTag(SheetScoresSheetTestTags.SUM),
            icon = painterResource(KitRes.drawable.ic_add),
            iconTone = Tone.Neutral,
            chevron = true,
            onClick = actions.onOpenSum,
        )
        Way.AI -> {
            val running = ai == SheetAiState.Running
            ListRow(
                stringResource(Res.string.sheet_ai_analyze),
                modifier.testTag(SheetScoresSheetTestTags.AI),
                subtitle = when (ai) {
                    SheetAiState.Running -> stringResource(Res.string.sheet_ai_running)
                    is SheetAiState.Failed -> ai.text.asString()
                    else -> stringResource(Res.string.sheet_ai_offer_note)
                },
                icon = painterResource(KitRes.drawable.ic_auto_awesome),
                iconTone = Tone.Neutral,
                accessory = if (running) ({ ItmoActivityIndicator() }) else null,
                onClick = if (running) null else actions.onAnalyze,
            )
        }
    }
}

/** The chosen total: `Ваш итог`, the number, where it comes from, one note, the teacher's total, the AI's details on demand. */
@Composable
private fun Hero(hero: SheetTotalHero) {
    var details by rememberSaveable { mutableStateOf(false) }
    ItmoCard(Modifier.testTag(SheetScoresSheetTestTags.HERO)) {
        Column(Modifier.semantics(mergeDescendants = true) {}) {
            Text(stringResource(Res.string.sheet_total_label), color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.labelLarge)
            Text(
                hero.value ?: stringResource(Res.string.sheet_scores_empty_cell),
                color = ItmoTheme.colorScheme.onSurface,
                style = ItmoTheme.typography.displaySmall.tabular(),
            )
            Text(hero.source.asString(), color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodyMedium)
        }
        hero.note?.let { note ->
            Text(
                note.asString(),
                color = if (hero.warning) Tone.Warning.ink() else ItmoTheme.colorScheme.onSurfaceVariant,
                style = ItmoTheme.typography.bodyMedium,
            )
        }
        hero.official?.let { Text(it.asString(), color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodyMedium) }
        if (hero.details.isNotEmpty()) {
            if (details) {
                hero.details.forEach { Text(it, color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodySmall) }
            }
            ProgressButton(
                label = stringResource(if (details) Res.string.sheet_ai_less else Res.string.sheet_ai_more),
                onClick = { details = !details },
                style = ProgressButtonStyle.Text,
                modifier = Modifier.testTag(SheetScoresSheetTestTags.AI_DETAILS),
            )
        }
    }
}

/** Nothing chosen yet: `Итог не найден` and why. */
@Composable
private fun Missing(reason: String) {
    ItmoCard(Modifier.testTag(SheetScoresSheetTestTags.HERO)) {
        Column(Modifier.semantics(mergeDescendants = true) {}) {
            Text(stringResource(Res.string.sheet_total_none), color = ItmoTheme.colorScheme.onSurface, style = ItmoTheme.typography.titleMedium)
            Text(reason, color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodyMedium)
        }
    }
}

/** `Выбрать столбец`: every column of the own row by tab, the current one checked, the empty ones folded; a tap picks. */
@Composable
private fun ColumnScope.Columns(state: SheetScoresUiState.PickTotal, onPick: (SheetCell) -> Unit) {
    val selected = (state.selection as? TotalOption.Column)?.cell
    val tabs = state.tabs
    var tabIndex by rememberSaveable { mutableIntStateOf(tabs.indexOf(selected?.tab).coerceAtLeast(0)) }
    var emptyOpen by rememberSaveable { mutableStateOf(false) }
    val tab = tabs.getOrNull(tabIndex) ?: tabs.firstOrNull()
    val untitled = stringResource(Res.string.sheet_scores_tab_untitled)
    val emptyTitle = stringResource(Res.string.sheet_scores_empty_columns)
    Body {
        if (tabs.size > 1) {
            item(key = "tabs") { FilterChips(tabs.map { ChipSpec(it.name.ifBlank { untitled }, it == tab) }, { tabIndex = it }) }
            gap("columns")
        }
        if (tab == null) return@Body
        group(state.filledColumns(tab).map { it.cell }, key = { "column:${it.tab.gid}:${it.column}" }) { cell, position ->
            CellRow(cell, position.selectable(cell == selected, role = Role.RadioButton) { onPick(cell) }, selected = cell == selected)
        }
        val empty = state.emptyColumns(tab).map { it.cell }
        if (empty.isNotEmpty()) {
            gap("empty")
            group(listOf<SheetCell?>(null) + if (emptyOpen) empty else emptyList(), key = { it?.let { "column:${it.tab.gid}:${it.column}" } ?: "empty" }) { cell, position ->
                if (cell == null) {
                    ListRow(
                        emptyTitle,
                        position.testTag(SheetScoresSheetTestTags.EMPTY_COLUMNS),
                        trailing = empty.size.toString(),
                        accessory = {
                            Icon(
                                painterResource(KitRes.drawable.ic_expand_more),
                                contentDescription = null,
                                modifier = Modifier.rotate(if (emptyOpen) 180f else 0f),
                                tint = ItmoTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        onClick = { emptyOpen = !emptyOpen },
                    )
                } else {
                    CellRow(cell, position.selectable(cell == selected, role = Role.RadioButton) { onPick(cell) }, selected = cell == selected)
                }
            }
        }
    }
}

/** `Сложить столбцы`: the live sum on top as the total it would be, then the columns with points, each with a check. */
@Composable
private fun ColumnScope.Sum(state: SheetScoresUiState.PickTotal, onToggle: (SheetCell) -> Unit) {
    val tabs = state.tabs
    var tabIndex by rememberSaveable { mutableIntStateOf(tabs.indexOf(state.summed.firstOrNull()?.tab).coerceAtLeast(0)) }
    val tab = tabs.getOrNull(tabIndex) ?: tabs.firstOrNull()
    val untitled = stringResource(Res.string.sheet_scores_tab_untitled)
    val line = state.sumText?.asString() ?: stringResource(Res.string.sheet_scores_sum_hint)
    Body {
        item(key = "sum") {
            ItmoCard(Modifier.testTag(SheetScoresSheetTestTags.HERO)) {
                Column(Modifier.semantics(mergeDescendants = true) {}) {
                    Text(stringResource(Res.string.sheet_total_label), color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.labelLarge)
                    Text(
                        state.sum ?: stringResource(Res.string.sheet_scores_empty_cell),
                        color = ItmoTheme.colorScheme.onSurface,
                        style = ItmoTheme.typography.displaySmall.tabular(),
                    )
                    Text(line, color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodyMedium)
                }
            }
        }
        gap("tabs") { ItmoTheme.spacing.group }
        if (tabs.size > 1) {
            item(key = "tabs") { FilterChips(tabs.map { ChipSpec(it.name.ifBlank { untitled }, it == tab) }, { tabIndex = it }) }
            gap("columns")
        }
        if (tab == null) return@Body
        group(state.sumColumns(tab).map { it.cell }, key = { "column:${it.tab.gid}:${it.column}" }) { cell, position ->
            val checked = cell in state.summed
            ListRow(
                shortTitle(cell).asString(),
                position.toggleable(checked, role = Role.Checkbox) { onToggle(cell) },
                subtitle = groupTitle(cell)?.asString(),
                leading = { Checkbox(checked, onCheckedChange = null) },
                accessory = { Value(cell.value.ifEmpty { stringResource(Res.string.sheet_scores_empty_cell) }) },
            )
        }
    }
}

/** A column: its own header over the group above it, the own value, a check when it is the chosen one. */
@Composable
private fun CellRow(cell: SheetCell, modifier: Modifier, selected: Boolean) {
    ListRow(
        shortTitle(cell).asString(),
        modifier,
        subtitle = groupTitle(cell)?.asString(),
        accessory = {
            Value(cell.value.ifEmpty { stringResource(Res.string.sheet_scores_empty_cell) })
            if (selected) Icon(painterResource(KitRes.drawable.ic_check), contentDescription = null, tint = ItmoTheme.colorScheme.primary)
        },
    )
}

/** The footer's one button: `Сохранить`, `Готово` when nothing changes or a sum is finished. */
@Composable
private fun Footer(state: SheetScoresUiState.PickTotal, action: TotalAction, actions: SheetScoresActions) {
    ProgressButton(
        label = stringResource(if (action == TotalAction.SAVE) Res.string.sheet_scores_save else Res.string.sheet_total_done),
        onClick = if (action == TotalAction.FINISH_SUM) actions.onFinishSum else actions.onSave,
        enabled = action != TotalAction.FINISH_SUM || state.canFinishSum,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ItmoTheme.spacing.screenMargin)
            .padding(top = ItmoTheme.spacing.compact)
            .testTag(SheetScoresSheetTestTags.SAVE),
    )
}

/** A step's question over an optional line of why it is asked. */
@Composable
private fun Question(text: String, note: String?) {
    Column(Modifier.semantics(mergeDescendants = true) {}) {
        SectionHeader(text)
        note?.let {
            Text(
                it,
                Modifier.padding(horizontal = ItmoTheme.spacing.related),
                color = ItmoTheme.colorScheme.onSurfaceVariant,
                style = ItmoTheme.typography.bodyMedium,
            )
        }
    }
}

/** A value at the end of a row, as the sheet shows it. */
@Composable
private fun Value(text: String) {
    Text(text, color = ItmoTheme.colorScheme.onSurface, style = ItmoTheme.typography.titleMedium.tabular())
}

/** The step's content: it scrolls inside what the sheet has left, never past it. */
@Composable
private fun ColumnScope.Body(content: LazyListScope.() -> Unit) {
    LazyColumn(
        Modifier
            .weight(1f, fill = false)
            .fillMaxWidth()
            .testTag(SheetScoresSheetTestTags.LIST),
        contentPadding = PaddingValues(
            start = ItmoTheme.spacing.screenMargin,
            end = ItmoTheme.spacing.screenMargin,
            bottom = ItmoTheme.spacing.content,
        ),
        content = content,
    )
}

/** Space between two parts of a step; by default a group's 12 dp to the group or row above it (docs/design.md). */
private fun LazyListScope.gap(key: String, height: @Composable () -> Dp = { ItmoTheme.spacing.content }) {
    item(key = "gap:$key") { Spacer(Modifier.height(height())) }
}

/** The rows of one connected group, each its own lazy item drawn by its position. */
private fun <T> LazyListScope.group(rows: List<T>, key: (T) -> Any, row: @Composable (T, Modifier) -> Unit) {
    itemsIndexed(rows, key = { _, it -> key(it) }) { index, item ->
        row(item, Modifier.connectedGroupItem(GroupPosition.of(index, rows.size), GroupSurface.Sheet))
    }
}

private fun variantKey(option: TotalOption): String = when (option) {
    is TotalOption.Column -> "variant:${option.cell.tab.gid}:${option.cell.column}"
    is TotalOption.Sum -> "variant:sum"
    TotalOption.Saved -> "variant:saved"
    TotalOption.Ai -> "variant:ai"
}

/** More people than this get the name search. */
private const val SEARCH_FROM = 8

/** `sheet_scores_content_min_height`: loading, a failure and the steps share this much, so nothing jumps. */
private val ContentMinHeight = 288.dp

/** Loading and failures centre in the whole shared area. */
private val StateArea = Modifier.heightIn(min = ContentMinHeight)
