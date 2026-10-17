package dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets

import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetCell
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
     * The cells of the own row by tab with their header paths, empty ones too; [selected] is the current total,
     * [decision] the automatic one (null without a total header) and [choice] how the current one was chosen.
     */
    data class PickTotal(
        val cells: List<SheetCell>,
        val selected: SheetCell?,
        val decision: SheetTotalDecision? = null,
        val choice: TotalChoice? = null,
    ) : SheetScoresUiState {
        /** The total the sheet shows: the current one, else the automatic one; null when there is neither. */
        val shown: SheetCell? get() = selected ?: decision?.total

        /** The candidate columns with a value, the automatic total first, at most [CANDIDATES]. */
        val candidates: List<SheetCell>
            get() = decision?.candidates.orEmpty().filter { it.value.isNotEmpty() }
                .distinctBy { it.tab to it.column }.take(CANDIDATES)

        /** «Вернуть авто» resets a hand-picked total to an automatic one that differs. */
        val canReset: Boolean get() = choice == TotalChoice.MANUAL && decision != null && decision.total != selected

        /** Why [shown] is the total, or that the viewer picked it. */
        val note: UiText?
            get() {
                val shown = shown ?: return null
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
         * A chip of [cell]: its short title and value; when another candidate has the same title, the whole path in the
         * same tab, else the tab's name (the same column in two tabs).
         */
        fun chip(cell: SheetCell): UiText {
            val short = shortTitle(cell)
            val twins = candidates.filter { it != cell && shortTitle(it) == short }
            val label = when {
                twins.isEmpty() -> short
                twins.any { it.tab == cell.tab } -> title(cell)
                else -> cell.tab.name.takeIf { it.isNotBlank() }?.let(UiText::Dynamic) ?: title(cell)
            }
            return UiText.Res(RecordbookStrings.sheet_scores_chip, listOf(label, cell.value))
        }

        private companion object {
            const val CANDIDATES = 4
            const val PREVIEW = 3
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

/** A column as the sheet names it: its header path, or `Столбец D` without one. */
fun title(cell: SheetCell): UiText =
    cell.headerPath.takeIf { it.isNotEmpty() }?.let(UiText::Dynamic)
        ?: UiText.Res(RecordbookStrings.sheet_scores_column, listOf(columnName(cell.column)))

/** The last header segment that is not a number (a max-points row writes «100» under «Сумма»), else [title]. */
fun shortTitle(cell: SheetCell): UiText =
    cell.headerPath.split(SheetHeaders.SEPARATOR).lastOrNull { segment -> SheetTotals.number(segment) == null && segment.isNotBlank() }
        ?.let(UiText::Dynamic) ?: title(cell)

sealed interface SheetScoresEvent {
    data class SaveFailed(val text: UiText) : SheetScoresEvent
}
