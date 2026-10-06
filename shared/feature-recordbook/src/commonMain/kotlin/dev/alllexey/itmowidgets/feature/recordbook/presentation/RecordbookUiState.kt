package dev.alllexey.itmowidgets.feature.recordbook.presentation

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportState
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookProgram
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookPeriod
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookRate
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject

data class RecordbookSelection(val program: RecordbookProgram, val period: RecordbookPeriod)

sealed interface RecordbookUiState {
    /** The BARS chip: whether the list overlays BARS values. The chip shows in every state, so every state has it. */
    val barsEnabled: Boolean

    data class Loading(
        val programs: List<RecordbookProgram> = emptyList(),
        val selection: RecordbookSelection? = null,
        override val barsEnabled: Boolean = false
    ) : RecordbookUiState
    data class Content(
        val programs: List<RecordbookProgram>,
        val selection: RecordbookSelection,
        val subjects: List<RecordbookSubject>,
        val sport: RecordbookSportState? = null,
        val refreshing: Boolean = false,
        val refreshError: AppError? = null,
        /** BARS overlay failed; the list still holds MyITMO values. */
        val barsError: AppError? = null,
        /** BARS answered for this period, so subjects without a journal are genuinely absent there. */
        val barsApplied: Boolean = false,
        /** Subjects for «Требуют внимания» by `entryId`; everything else is the regular list. */
        val attention: Map<Long, RecordbookAttentionReason> = emptyMap(),
        /** `subjectNameKey`s of unread new or changed marks in the selected period's half-year. */
        val newSubjects: Set<String> = emptySet(),
        /** Totals of connected sheets in the selected period by `disciplineId`; the list never downloads them. */
        val sheetTotals: Map<Long, String> = emptyMap(),
        override val barsEnabled: Boolean = false
    ) : RecordbookUiState {
        /** The pass count means something only once a final grade or credit exists. */
        val showSummary: Boolean get() = subjects.any { it.normalizedRate != RecordbookRate.InProgress }
    }
    data class Empty(override val barsEnabled: Boolean = false) : RecordbookUiState
    data class Error(
        val error: AppError,
        val programs: List<RecordbookProgram> = emptyList(),
        val selection: RecordbookSelection? = null,
        override val barsEnabled: Boolean = false
    ) : RecordbookUiState
}
