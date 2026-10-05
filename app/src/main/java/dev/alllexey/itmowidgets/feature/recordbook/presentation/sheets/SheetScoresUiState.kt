package dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets

import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetCell
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetRowMatch
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetStatus
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetTab

sealed interface SheetScoresUiState {
    data object Loading : SheetScoresUiState
    /** Several rows look like the viewer's. */
    data class PickRow(val candidates: List<SheetRowMatch>) : SheetScoresUiState
    /** No row was found: the tabs with students to look in. */
    data class PickTab(val tabs: List<SheetTab>) : SheetScoresUiState
    data class PickTabRow(val tab: SheetTab, val rows: List<SheetRowMatch>) : SheetScoresUiState
    /** The cells of the own row by tab; [selected] is the current total. */
    data class PickTotal(val cells: List<SheetCell>, val selected: SheetCell?) : SheetScoresUiState
    data class Failed(val status: SheetStatus) : SheetScoresUiState
    data object Done : SheetScoresUiState
}

sealed interface SheetScoresEvent {
    data class SaveFailed(val text: UiText) : SheetScoresEvent
}
