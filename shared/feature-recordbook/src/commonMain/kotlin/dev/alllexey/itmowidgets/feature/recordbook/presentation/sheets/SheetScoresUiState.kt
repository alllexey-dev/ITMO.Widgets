package dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets

import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.recordbook.domain.formatRecordbookNumber
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
    data class PickTabRow(val tab: SheetTab, val rows: List<SheetRowMatch>) : SheetScoresUiState
    /**
     * The total's review. [cells] is the own row by tab with header paths, empty ones too; [selected] is the current
     * total when it is one cell, [decision] the automatic one (null without a total header), [choice] how the current
     * one was chosen and [saved] a current computed total (a sum or an AI rule). The manual pick: [picked] is the cell
     * tapped and not yet saved; in [summing] mode [summed] are the columns to add up and [sum] their own sum. [columns]
     * describe every cell for the column list; [ai] is the AI offer and its result.
     */
    data class PickTotal(
        val cells: List<SheetCell>,
        val selected: SheetCell?,
        val decision: SheetTotalDecision? = null,
        val choice: TotalChoice? = null,
        val saved: SheetSavedTotal? = null,
        val picked: SheetCell? = null,
        val summing: Boolean = false,
        val summed: List<SheetCell> = emptyList(),
        val sum: String? = null,
        val columns: List<SheetColumnOption> = emptyList(),
        val ai: SheetAiState = SheetAiState.Hidden,
    ) : SheetScoresUiState {
        /** The total the card shows: the tapped one, else the current one, else the automatic one unless a sum is saved. */
        val shown: SheetCell? get() = picked ?: selected ?: decision?.total?.takeIf { saved == null }

        /** The candidate columns with a value, the automatic total first, at most [CANDIDATES]. */
        val candidates: List<SheetCell>
            get() = decision?.candidates.orEmpty().filter { it.value.isNotEmpty() }
                .distinctBy { it.tab to it.column }.take(CANDIDATES)

        /** «Вернуть авто» picks the automatic total instead of a hand-picked one that differs. */
        val canReset: Boolean get() = choice != null && choice != TotalChoice.AUTO && choice != TotalChoice.AUTO_CHECK &&
            decision != null && decision.total != shown

        /**
         * «Сохранить» has something new to save: a tapped cell other than the current one (the unsure automatic one
         * tapped again confirms it), or at least two columns to add.
         */
        val canSave: Boolean
            get() = if (summing) {
                summed.size >= MIN_SUM
            } else {
                picked != null && (picked != selected || (choice == TotalChoice.AUTO_CHECK && picked == decision?.total))
            }

        /** The tabs of [cells] in order, for the tab switcher. */
        val tabs: List<SheetTab> get() = cells.map { it.tab }.distinct()

        /** The columns of [tab] with a value for somebody, then those empty for everyone (folded). */
        fun filledColumns(tab: SheetTab): List<SheetColumnOption> = columns.filter { it.cell.tab == tab && !it.empty }
        fun emptyColumns(tab: SheetTab): List<SheetColumnOption> = columns.filter { it.cell.tab == tab && it.empty }

        /** Why [shown] is the total, or that the viewer picked it. */
        val note: UiText?
            get() {
                val shown = shown ?: return null
                if (shown == selected && choice == TotalChoice.AI) return UiText.Res(RecordbookStrings.sheet_scores_ai_chosen)
                if (choice == TotalChoice.MANUAL && shown != decision?.total) return UiText.Res(RecordbookStrings.sheet_scores_manual)
                val decision = decision?.takeIf { it.total == shown } ?: return null
                return UiText.Res(REASONS.getValue(decision.reason), listOf(shortTitle(shown)))
            }

        /** [note] asks the viewer to check the total: it is unsure and was not confirmed. */
        val unsure: Boolean
            get() = decision?.confidence == TotalConfidence.LOW && decision.total == shown && choice != TotalChoice.AUTO

        /** The own row's cells around [shown] in its tab, for the preview: [PREVIEW] on each side. */
        val preview: List<SheetCell>
            get() {
                val shown = shown ?: return emptyList()
                val row = cells.filter { it.tab == shown.tab }
                val at = row.indexOf(shown).coerceAtLeast(0)
                return row.subList(maxOf(0, at - PREVIEW), minOf(row.size, at + PREVIEW + 1))
            }

        /**
         * A chip of [cell]: its short title and value, `авто` on the automatic total; when another candidate has the
         * same title, the whole path in the same tab, else the tab's name (the same column in two tabs).
         */
        fun chip(cell: SheetCell): UiText {
            val short = shortTitle(cell)
            val twins = candidates.filter { it != cell && shortTitle(it) == short }
            val label = when {
                twins.isEmpty() -> short
                twins.any { it.tab == cell.tab } -> title(cell)
                else -> cell.tab.name.takeIf { it.isNotBlank() }?.let(UiText::Dynamic) ?: title(cell)
            }
            val key = if (cell == decision?.total) RecordbookStrings.sheet_scores_chip_auto else RecordbookStrings.sheet_scores_chip
            return UiText.Res(key, listOf(label, cell.value))
        }

        private companion object {
            const val CANDIDATES = 4
            const val PREVIEW = 3
            const val MIN_SUM = 2
            val REASONS = mapOf(
                TotalReason.SUM_MATCHES to RecordbookStrings.sheet_scores_reason_sum,
                TotalReason.HEADER to RecordbookStrings.sheet_scores_reason_header,
                TotalReason.SEVERAL_TABS to RecordbookStrings.sheet_scores_reason_tabs,
                TotalReason.EMPTY_FOR_ALL to RecordbookStrings.sheet_scores_reason_empty,
                TotalReason.WEAK_HEADER to RecordbookStrings.sheet_scores_reason_weak,
                TotalReason.NOT_POINTS to RecordbookStrings.sheet_scores_reason_not_points,
            )
        }
    }

    data class Failed(val status: SheetStatus) : SheetScoresUiState
    data object Done : SheetScoresUiState
}

/** What a failed reading says; null for [SheetStatus.OK]. */
fun SheetStatus.text(): UiText? = when (this) {
    SheetStatus.OK -> null
    SheetStatus.NETWORK -> UiText.Res(RecordbookStrings.sheet_scores_offline)
    SheetStatus.CLOSED -> UiText.Res(RecordbookStrings.sheet_scores_closed)
    SheetStatus.ROW_NOT_FOUND -> UiText.Res(RecordbookStrings.sheet_scores_row_not_found)
    SheetStatus.COLUMN_NOT_FOUND -> UiText.Res(RecordbookStrings.sheet_scores_column_not_found)
    SheetStatus.TOO_LARGE -> UiText.Res(RecordbookStrings.sheet_scores_too_large)
}

/** A column as the sheet names it: its header path, or `Столбец D` without one. */
fun title(cell: SheetCell): UiText =
    cell.headerPath.takeIf { it.isNotEmpty() }?.let(UiText::Dynamic)
        ?: UiText.Res(RecordbookStrings.sheet_scores_column, listOf(columnName(cell.column)))

/** The last header segment that is not a number (a max-points row writes «100» under «Сумма»), else [title]. */
fun shortTitle(cell: SheetCell): UiText =
    cell.headerPath.split(SheetHeaders.SEPARATOR).lastOrNull { segment -> SheetTotals.number(segment) == null && segment.isNotBlank() }
        ?.let(UiText::Dynamic) ?: title(cell)

/** A saved total that is no single cell: the formula in words and what it reads now. */
data class SheetSavedTotal(val formula: UiText, val value: String?, val official: String? = null)

/**
 * One column of the manual pick: the own [cell] and a summary of the column among the students (`есть у 23 из 30 ·
 * до 10`), computed on the fly; [empty] when nobody has a value in it.
 */
data class SheetColumnOption(val cell: SheetCell, val stats: SheetColumnStats) {
    val empty: Boolean get() = stats.filled == 0

    val summary: UiText
        get() {
            val filled = UiText.Res(RecordbookStrings.sheet_scores_column_filled, listOf(stats.filled, stats.students))
            val max = stats.max ?: return filled
            return UiText.Joined(listOf(filled, UiText.Res(RecordbookStrings.sheet_scores_column_max, listOf(formatRecordbookNumber(max)))), " · ")
        }
}

/** `Разобрать с ИИ` in the review: not offered, offered, running, its result, or why there is none. */
sealed interface SheetAiState {
    data object Hidden : SheetAiState
    data object Offer : SheetAiState
    data object Running : SheetAiState
    /**
     * The rule applied to the own row: [formula] in words, the own [value] (null is a dash), the teacher's [official]
     * total when the rule has one, the model's [summary] and [warnings]. Nothing is saved before `Использовать`.
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
