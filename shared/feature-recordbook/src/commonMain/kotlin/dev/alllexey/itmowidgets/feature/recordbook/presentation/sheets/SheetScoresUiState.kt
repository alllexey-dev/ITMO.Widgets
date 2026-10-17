package dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets

import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetCell
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetRowMatch
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetStatus
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetTab
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetTotalDecision
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.TotalChoice

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
        /** The candidate columns with a value, the automatic total first, at most [CANDIDATES]. */
        val candidates: List<SheetCell>
            get() = decision?.candidates.orEmpty().filter { it.value.isNotEmpty() }
                .distinctBy { it.tab to it.column }.take(CANDIDATES)

        /** «Вернуть авто» resets a hand-picked total to an automatic one that differs. */
        val canReset: Boolean get() = choice == TotalChoice.MANUAL && decision != null && decision.total != selected

        private companion object {
            const val CANDIDATES = 4
        }
    }
    data class Failed(val status: SheetStatus) : SheetScoresUiState
    data object Done : SheetScoresUiState
}

sealed interface SheetScoresEvent {
    data class SaveFailed(val text: UiText) : SheetScoresEvent
}
