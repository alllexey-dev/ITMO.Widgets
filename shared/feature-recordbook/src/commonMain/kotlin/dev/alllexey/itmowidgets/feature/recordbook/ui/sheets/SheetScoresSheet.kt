package dev.alllexey.itmowidgets.feature.recordbook.ui.sheets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetScaffold
import dev.alllexey.itmowidgets.designsystem.components.state.ContentState
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateAction
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateLoading
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateSize
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetCell
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetRowMatch
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetStatus
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetTab
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetText
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.SheetScoresUiState
import dev.alllexey.itmowidgets.shared.feature.recordbook.Res
import dev.alllexey.itmowidgets.shared.feature.recordbook.sheet_scores_no_values
import dev.alllexey.itmowidgets.shared.feature.recordbook.sheet_scores_offline
import dev.alllexey.itmowidgets.shared.feature.recordbook.sheet_scores_pick_row
import dev.alllexey.itmowidgets.shared.feature.recordbook.sheet_scores_pick_tab
import dev.alllexey.itmowidgets.shared.feature.recordbook.sheet_scores_pick_total
import dev.alllexey.itmowidgets.shared.feature.recordbook.sheet_scores_search_clear
import dev.alllexey.itmowidgets.shared.feature.recordbook.sheet_scores_search_empty
import dev.alllexey.itmowidgets.shared.feature.recordbook.sheet_scores_search_hint
import dev.alllexey.itmowidgets.shared.feature.recordbook.sheet_scores_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.core.common_retry
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes
import dev.alllexey.itmowidgets.shared.designsystem.ic_check
import dev.alllexey.itmowidgets.shared.designsystem.ic_close
import dev.alllexey.itmowidgets.shared.designsystem.ic_error
import dev.alllexey.itmowidgets.shared.designsystem.ic_search

/** What the scores sheet (`sheet_scores_title`) asks its host to do; every effect is the host's (the view model's actions). */
@Immutable
class SheetScoresActions(
    val onRetry: () -> Unit = {},
    val onPickRow: (SheetRowMatch) -> Unit = {},
    val onPickTab: (SheetTab) -> Unit = {},
    val onPickTotal: (SheetCell) -> Unit = {},
)

/** Test tags of [SheetScoresSheet]. */
object SheetScoresSheetTestTags {
    const val SEARCH = "sheet_scores_search"
    const val SEARCH_CLEAR = "sheet_scores_search_clear"
    const val LIST = "sheet_scores_list"
    const val RETRY = "sheet_scores_retry"
}

/**
 * The scores sheet (`sheet_scores_title`, ex `sheet_scores_setup.xml`): the title over [subjectName], then one bounded area where loading, a
 * failure and the choices take turns without moving the sheet: the own row among several, the tab to look in, a row of
 * that tab, the total among the own row's cells by tab. A long list of people gets a search by name whose text
 * survives the state changes and recreation; it is the only state the sheet keeps. Cell values show as the sheet has
 * them (ADR 0014). The host owns the container, [SheetScoresUiState.Done] (it closes the sheet) and the save failure.
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
    ) {
        when (state) {
            SheetScoresUiState.Loading -> ContentStateLoading(StateArea, ContentStateSize.Compact)
            is SheetScoresUiState.Failed -> Failure(state.status, actions.onRetry)
            is SheetScoresUiState.PickRow -> Rows(state.candidates, query, { query = it }, actions.onPickRow)
            is SheetScoresUiState.PickTabRow -> Rows(state.rows, query, { query = it }, actions.onPickRow)
            is SheetScoresUiState.PickTab -> Choices(Res.string.sheet_scores_pick_tab, tabOptions(state.tabs, actions))
            is SheetScoresUiState.PickTotal -> Choices(Res.string.sheet_scores_pick_total, totalOptions(state, actions))
            // The host closes the sheet; the area stays empty until then.
            SheetScoresUiState.Done -> Unit
        }
    }
}

/** A row of the choices: a tab heading or one choice. */
private sealed interface SheetOption {
    val key: String

    data class Section(override val key: String, val title: String) : SheetOption

    /** [value] sits at the end of the row; [selected] is the current choice. */
    data class Choice(
        override val key: String,
        val title: String,
        val caption: String? = null,
        val value: String? = null,
        val selected: Boolean = false,
        val onClick: () -> Unit,
    ) : SheetOption
}

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
                stringResource(CoreRes.string.common_retry),
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
private fun rowOption(match: SheetRowMatch, onPick: (SheetRowMatch) -> Unit) = SheetOption.Choice(
    key = "row:${match.tab.gid}:${match.row}",
    title = match.label,
    caption = match.tab.name.takeIf { it.isNotBlank() },
) { onPick(match) }

@Composable
private fun tabOptions(tabs: List<SheetTab>, actions: SheetScoresActions): List<SheetOption> {
    val untitled = stringResource(Res.string.sheet_scores_pick_tab)
    return tabs.map { tab ->
        SheetOption.Choice("tab:${tab.gid}", tab.name.ifBlank { untitled }) { actions.onPickTab(tab) }
    }
}

/** The cells under a heading of their tab; a tab without a name has no heading. */
@Composable
private fun totalOptions(state: SheetScoresUiState.PickTotal, actions: SheetScoresActions): List<SheetOption> =
    state.cells.groupBy { it.tab }.flatMap { (tab, cells) ->
        val heading = SheetOption.Section("tab:${tab.gid}", tab.name).takeIf { tab.name.isNotBlank() }
        listOfNotNull(heading) + cells.map { cell ->
            SheetOption.Choice(
                key = "cell:${cell.tab.gid}:${cell.column}",
                title = sheetColumnTitle(cell.headerPath, cell.column),
                value = cell.value,
                selected = cell == state.selected,
            ) { actions.onPickTotal(cell) }
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
        items(options, key = { it.key }) { option ->
            when (option) {
                is SheetOption.Section -> SectionTitle(option.title)
                is SheetOption.Choice -> ChoiceRow(option)
            }
        }
    }
}

/** `item_subject_link_section.xml` without its icon: the tab's name over its cells. */
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

/**
 * `item_sheet_scores_option.xml`: the whole row is the 48 dp target, read once as a selectable option with its title,
 * caption and value; the check in `primary` marks the current choice.
 */
@Composable
private fun ChoiceRow(option: SheetOption.Choice) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ItmoTheme.spacing.touchTarget)
            .selectable(option.selected, role = Role.RadioButton, onClick = option.onClick)
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
        option.value?.takeIf { it.isNotEmpty() }?.let { value ->
            Text(
                value,
                Modifier
                    .padding(start = ItmoTheme.spacing.content)
                    .widthIn(max = ValueMaxWidth),
                color = ItmoTheme.colorScheme.onSurface,
                style = ItmoTheme.typography.titleSmall,
                textAlign = TextAlign.End,
            )
        }
        Icon(
            painterResource(KitRes.drawable.ic_check),
            contentDescription = null,
            modifier = Modifier
                .padding(start = ItmoTheme.spacing.compact)
                .size(CheckSize)
                .alpha(if (option.selected) 1f else 0f),
            tint = ItmoTheme.colorScheme.primary,
        )
    }
}

/** More people than this get the name search. */
private const val SEARCH_FROM = 8

/** `sheet_scores_content_min_height`: loading, a failure and the choices share this much, so nothing jumps. */
private val ContentMinHeight = 288.dp

/** Loading and failures centre in the whole shared area. */
private val StateArea = Modifier.heightIn(min = ContentMinHeight)

/** `sheet_scores_value_max_width`: a long cell value wraps before it squeezes the title. */
private val ValueMaxWidth = 120.dp

private val CheckSize = 24.dp
