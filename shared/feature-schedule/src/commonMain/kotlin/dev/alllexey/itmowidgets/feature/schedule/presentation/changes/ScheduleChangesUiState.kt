package dev.alllexey.itmowidgets.feature.schedule.presentation.changes

import dev.alllexey.itmowidgets.core.schedule.ScheduleChange
import kotlinx.datetime.LocalDate

/** The history of the last 30 days. There is no error: a broken file is reset by the store. */
sealed interface ScheduleChangesUiState {
    /** The local file is not read yet; the screen stays blank instead of flashing a skeleton. */
    data object Loading : ScheduleChangesUiState
    data object Empty : ScheduleChangesUiState
    data class Content(val days: List<ScheduleChangeDay>) : ScheduleChangesUiState
}

/** Changes found on one Moscow day, newest first. */
data class ScheduleChangeDay(val date: LocalDate, val relative: RelativeDay, val rows: List<ScheduleChangeRow>)

/** How a day title reads: [OTHER] is a date of the current year, [OTHER_YEAR] spells the year out. */
enum class RelativeDay { TODAY, YESTERDAY, OTHER, OTHER_YEAR }

/** [isNew] survives marking read: the row was unread when the screen showed it. */
data class ScheduleChangeRow(val change: ScheduleChange, val isNew: Boolean)
