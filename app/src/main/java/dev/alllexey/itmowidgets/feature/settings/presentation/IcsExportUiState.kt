package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.schedule.IcsFile
import dev.alllexey.itmowidgets.core.text.UiText

/** A row of the range choice: its title and the days it covers from today. */
data class IcsRangeOption(val kind: IcsRangeKind, val title: UiText, val dates: UiText)

enum class IcsRangeKind { WEEK, TWO_WEEKS, SEMESTER, CUSTOM }

sealed interface IcsExportUiState {
    data class Choose(val options: List<IcsRangeOption>) : IcsExportUiState

    data object Preparing : IcsExportUiState

    /** [dates] names the exported days, as «5–11 октября». */
    data class Ready(val file: IcsFile, val dates: UiText) : IcsExportUiState

    data object Empty : IcsExportUiState

    data class Failed(val error: AppError) : IcsExportUiState
}

sealed interface IcsExportEvent {
    /** Opens the date range picker; its answer comes back through `onDates`. */
    data object PickDates : IcsExportEvent
}
