package dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets

import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetCell
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetColumnStats
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetHeaders
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetRowMatch
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetStatus
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetTab
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetTotalDecision
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetTotals
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.TotalChoice
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.TotalConfidence
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.TotalReason
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.columnName
import dev.alllexey.itmowidgets.shared.feature.recordbook.RecordbookStrings

sealed interface SheetScoresUiState {
    data object Loading : SheetScoresUiState
    /** Several rows look like the viewer's. */
    data class PickRow(val candidates: List<SheetRowMatch>) : SheetScoresUiState
    /** No row was found: the tabs with students to look in. */
    data class PickTab(val tabs: List<SheetTab>) : SheetScoresUiState
    /** The rows of the tab picked in [PickTab]; back returns to the tabs. */
    data class PickTabRow(val tab: SheetTab, val rows: List<SheetRowMatch>) : SheetScoresUiState

    /**
     * The total: what is [selection] now (null while nothing is), what is [current]ly saved (null while connecting),
     * how the current one was [choice]n, the automatic [decision] (null without a total header) and a saved computed
     * total in words ([saved]). [page] is the overview, the column list or the columns to add: [summed] is the sum
     * being made there with its own [sum] and [sumText], [made] the last finished one and [picked] the last column
     * picked from the list, both variants until the sheet closes. [cells] is the own row by tab, [columns] describe
     * each cell, [ai] is the AI offer and its result.
     */
    data class PickTotal(
        val cells: List<SheetCell>,
        val selection: TotalOption?,
        val current: TotalOption? = null,
        val decision: SheetTotalDecision? = null,
        val choice: TotalChoice? = null,
        val saved: SheetSavedTotal? = null,
        val columns: List<SheetColumnOption> = emptyList(),
        val ai: SheetAiState = SheetAiState.Hidden,
        val page: TotalPage = TotalPage.OVERVIEW,
        val summed: List<SheetCell> = emptyList(),
        val sum: String? = null,
        val sumText: UiText? = null,
        val made: TotalOption.Sum? = null,
        val picked: SheetCell? = null,
    ) : SheetScoresUiState {

        /** The automatic total while it waits for a check: connected as one, or unsure and not confirmed. */
        private val unsureTotal: SheetCell?
            get() = decision?.takeIf {
                choice == TotalChoice.AUTO_CHECK || (it.confidence == TotalConfidence.LOW && choice != TotalChoice.AUTO)
            }?.total

        /** The selection on top: its value, where it comes from and at most one note. */
        val hero: SheetTotalHero?
            get() = when (val option = selection) {
                null -> null
                is TotalOption.Column -> {
                    val check = REASONS[decision?.reason]?.takeIf { option.cell == unsureTotal }
                    SheetTotalHero(option.cell.value.ifEmpty { null }, columnSource(option.cell), check?.let(UiText::Res), warning = check != null)
                }
                is TotalOption.Sum -> SheetTotalHero(option.value, option.text)
                TotalOption.Saved -> saved?.let { saved ->
                    SheetTotalHero(
                        value = saved.value,
                        source = saved.formula,
                        note = UiText.Res(RecordbookStrings.sheet_total_ai_note).takeIf { choice == TotalChoice.AI },
                        official = saved.official?.let { UiText.Res(RecordbookStrings.sheet_scores_official, listOf(it)) },
                    )
                }
                TotalOption.Ai -> (ai as? SheetAiState.Proposal)?.let { proposal ->
                    SheetTotalHero(
                        value = proposal.value,
                        source = proposal.formula,
                        note = UiText.Res(RecordbookStrings.sheet_total_ai_note),
                        official = if (proposal.hasOfficial) {
                            UiText.Res(RecordbookStrings.sheet_scores_official, listOf(proposal.official ?: EMPTY))
                        } else {
                            null
                        },
                        details = listOf(proposal.summary).filter { it.isNotBlank() } + proposal.warnings,
                    )
                }
            }

        /** Why nothing is on top: the own row is empty, or the sheet has no total header. */
        val missing: UiText
            get() = UiText.Res(
                if (cells.none { it.value.isNotEmpty() }) RecordbookStrings.sheet_total_none_empty else RecordbookStrings.sheet_total_none_reason,
            )

        /**
         * The other ready totals, never the selected one: the automatic total and other total columns with points,
         * the current and the picked column, the saved computed total, the sum made here and the AI's.
         */
        val variants: List<SheetTotalVariant>
            get() {
                val columns = (
                    listOfNotNull(decision?.total) +
                        decision?.candidates.orEmpty().filter { SheetTotals.number(it.value) != null } +
                        listOfNotNull((current as? TotalOption.Column)?.cell, picked)
                    ).filter { it.value.isNotEmpty() }.distinctBy { it.tab to it.column }.take(CANDIDATES)
                val spansTabs = columns.map { it.tab }.distinct().size > 1
                val options = columns.map { cell ->
                    val parts = listOfNotNull(
                        UiText.Res(RecordbookStrings.sheet_total_auto).takeIf { cell == decision?.total },
                        cell.tab.name.takeIf { spansTabs && it.isNotBlank() }?.let { UiText.Res(RecordbookStrings.sheet_total_tab, listOf(it)) },
                    )
                    SheetTotalVariant(TotalOption.Column(cell), title(cell), parts.joined(), cell.value)
                } + listOfNotNull(
                    saved?.takeIf { current == TotalOption.Saved }?.let {
                        SheetTotalVariant(TotalOption.Saved, it.formula, AI_MARK.takeIf { choice == TotalChoice.AI }, it.value ?: EMPTY)
                    },
                    made?.let { SheetTotalVariant(it, it.text, null, it.value ?: EMPTY) },
                    (ai as? SheetAiState.Proposal)?.let { SheetTotalVariant(TotalOption.Ai, it.formula, AI_MARK, it.value ?: EMPTY) },
                )
                return options.filter { it.option != selection }
            }

        /** The footer: save a new choice, close when nothing changes, finish a sum; none while nothing is chosen. */
        val action: TotalAction?
            get() = when {
                page == TotalPage.SUM -> TotalAction.FINISH_SUM
                page == TotalPage.COLUMNS || selection == null -> null
                selection == current && !(selection is TotalOption.Column && selection.cell == unsureTotal) -> TotalAction.DONE
                else -> TotalAction.SAVE
            }

        /** `Готово` of the sum page needs two columns at least. */
        val canFinishSum: Boolean get() = summed.size >= MIN_SUM

        /** The tabs of [cells] in order, for the tab switcher of the column pages. */
        val tabs: List<SheetTab> get() = cells.map { it.tab }.distinct()

        /** The columns of [tab] with a value for somebody, then those empty for everyone (folded). */
        fun filledColumns(tab: SheetTab): List<SheetColumnOption> = columns.filter { it.cell.tab == tab && !it.empty }
        fun emptyColumns(tab: SheetTab): List<SheetColumnOption> = columns.filter { it.cell.tab == tab && it.empty }

        /** The columns of [tab] that can be added: the own value is a number, or empty in a column of numbers. */
        fun sumColumns(tab: SheetTab): List<SheetColumnOption> = columns.filter { option ->
            val value = option.cell.value
            option.cell.tab == tab && (SheetTotals.number(value) != null || (value.isEmpty() && option.stats.max != null))
        }

        private companion object {
            const val CANDIDATES = 4
            const val MIN_SUM = 2
            val AI_MARK = UiText.Res(RecordbookStrings.sheet_total_ai)
            val REASONS = mapOf(
                TotalReason.SEVERAL_TABS to RecordbookStrings.sheet_total_check_tabs,
                TotalReason.EMPTY_FOR_ALL to RecordbookStrings.sheet_total_check_empty,
                TotalReason.WEAK_HEADER to RecordbookStrings.sheet_total_check_weak,
                TotalReason.NOT_POINTS to RecordbookStrings.sheet_total_check_not_points,
            )
        }
    }

    data class Failed(val status: SheetStatus) : SheetScoresUiState
    data object Done : SheetScoresUiState
}

/** Back inside the sheet: from a column page to the overview, from a tab's rows to the tabs. */
val SheetScoresUiState.canGoBack: Boolean
    get() = this is SheetScoresUiState.PickTabRow || (this is SheetScoresUiState.PickTotal && page != TotalPage.OVERVIEW)

/** The parts of the total's step. */
enum class TotalPage { OVERVIEW, COLUMNS, SUM }

/** The footer's one button. */
enum class TotalAction { SAVE, DONE, FINISH_SUM }

/** A total the viewer can choose. */
sealed interface TotalOption {
    data class Column(val cell: SheetCell) : TotalOption
    /** Columns added up on the device: their own [value] and the formula in [text]. */
    data class Sum(val cells: List<SheetCell>, val value: String?, val text: UiText) : TotalOption
    /** The computed total saved before: a hand-made sum or an AI rule. */
    data object Saved : TotalOption
    /** The AI rule's proposal ([SheetAiState.Proposal]). */
    data object Ai : TotalOption
}

/**
 * The chosen total on top: [value] (null is a dash), its [source] in one line, a [note] ([warning] when it asks to
 * check), the teacher's [official] total and the AI's [details] shown on demand.
 */
data class SheetTotalHero(
    val value: String?,
    val source: UiText,
    val note: UiText? = null,
    val warning: Boolean = false,
    val official: UiText? = null,
    val details: List<String> = emptyList(),
)

/** Another ready total: what it is ([title], [subtitle]) and its [value] as shown. */
data class SheetTotalVariant(val option: TotalOption, val title: UiText, val subtitle: UiText?, val value: String)

/** What a failed reading says; null for [SheetStatus.OK]. */
fun SheetStatus.text(): UiText? = when (this) {
    SheetStatus.OK -> null
    SheetStatus.NETWORK -> UiText.Res(RecordbookStrings.sheet_scores_offline)
    SheetStatus.CLOSED -> UiText.Res(RecordbookStrings.sheet_scores_closed)
    SheetStatus.ROW_NOT_FOUND -> UiText.Res(RecordbookStrings.sheet_scores_row_not_found)
    SheetStatus.COLUMN_NOT_FOUND -> UiText.Res(RecordbookStrings.sheet_scores_column_not_found)
    SheetStatus.TOO_LARGE -> UiText.Res(RecordbookStrings.sheet_scores_too_large)
}

/** Why a reading failed or what may have happened, under [text] in the sheet; null when the title says it all. */
fun SheetStatus.reason(): UiText? = when (this) {
    SheetStatus.OK, SheetStatus.NETWORK -> null
    SheetStatus.CLOSED -> UiText.Res(RecordbookStrings.sheet_scores_closed_reason)
    SheetStatus.ROW_NOT_FOUND -> UiText.Res(RecordbookStrings.sheet_scores_row_not_found_reason)
    SheetStatus.COLUMN_NOT_FOUND -> UiText.Res(RecordbookStrings.sheet_scores_column_not_found_reason)
    SheetStatus.TOO_LARGE -> UiText.Res(RecordbookStrings.sheet_scores_too_large_reason)
}

/** A column as the sheet names it: its header path (`ЛР › Итог`), or `Столбец D` without one. */
fun title(cell: SheetCell): UiText =
    pathText(cell.headerPath) ?: UiText.Res(RecordbookStrings.sheet_scores_column, listOf(columnName(cell.column)))

/** The last header segment that is not a number (a max-points row writes «100» under «Сумма»), else [title]. */
fun shortTitle(cell: SheetCell): UiText =
    segments(cell.headerPath).lastOrNull { SheetTotals.number(it) == null }?.let(UiText::Dynamic) ?: title(cell)

/** The segments of the path above [shortTitle] (`Тесты к видеолекциям`), or null. */
fun groupTitle(cell: SheetCell): UiText? {
    val path = segments(cell.headerPath)
    val short = path.indexOfLast { SheetTotals.number(it) == null }
    return if (short > 0) UiText.Dynamic(path.take(short).joinToString(PATH_SEPARATOR)) else null
}

/** `Столбец «ЛР › Итог», лист «P3110»`: one column's place, the same line as on the subject page. */
fun columnSource(cell: SheetCell): UiText = columnSource(cell.tab.name, title(cell))

/** [column] of the tab [tabName] in one line; without a tab name only the column. */
fun columnSource(tabName: String, column: UiText): UiText =
    if (tabName.isBlank()) {
        UiText.Res(RecordbookStrings.sheet_total_column, listOf(column))
    } else {
        UiText.Res(RecordbookStrings.subject_sheet_caption, listOf(column, tabName))
    }

/** A header path for people: its segments joined by ` › `; null for an empty one. */
fun pathText(headerPath: String): UiText? =
    segments(headerPath).takeIf { it.isNotEmpty() }?.let { UiText.Dynamic(it.joinToString(PATH_SEPARATOR)) }

private fun segments(headerPath: String): List<String> = headerPath.split(SheetHeaders.SEPARATOR).filter { it.isNotBlank() }

/** Facts of one caption joined by commas, the first as it is and the rest lowercased: `Найден автоматически, лист «A»`. */
private fun List<UiText>.joined(): UiText? = when (size) {
    0 -> null
    1 -> single()
    else -> UiText.Joined(listOf(first()) + drop(1).map(UiText::LowercaseFirst), ", ")
}

private const val PATH_SEPARATOR = " › "
private const val EMPTY = "—"

/** A saved total that is no single cell: the formula in words and what it reads now. */
data class SheetSavedTotal(val formula: UiText, val value: String?, val official: String? = null)

/** One column of the own row and how it looks among the students; [empty] when nobody has a value in it. */
data class SheetColumnOption(val cell: SheetCell, val stats: SheetColumnStats) {
    val empty: Boolean get() = stats.filled == 0
}

/** `Посчитать с ИИ`: not offered, offered, running, its result, or why there is none. */
sealed interface SheetAiState {
    data object Hidden : SheetAiState
    data object Offer : SheetAiState
    data object Running : SheetAiState
    /**
     * The rule applied to the own row: [formula] in words, the own [value] (null is a dash), the teacher's [official]
     * total when the rule has one, the model's [summary] and [warnings]. Nothing is saved before `Сохранить`.
     */
    data class Proposal(
        val formula: UiText,
        val value: String?,
        val official: String?,
        val hasOfficial: Boolean,
        val summary: String,
        val warnings: List<String>,
    ) : SheetAiState
    data class Failed(val text: UiText) : SheetAiState
}

sealed interface SheetScoresEvent {
    data class SaveFailed(val text: UiText) : SheetScoresEvent
}
