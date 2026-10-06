package dev.alllexey.itmowidgets.feature.schedule.ui.changes

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.feature.schedule.presentation.changes.ScheduleChangesUiState
import dev.alllexey.itmowidgets.feature.schedule.ui.changes.preview.ScheduleChangesPreviewData

/*
 * The harness names a baseline `<function>_<@Preview name>`, and LS-0 recorded the XML references as
 * `ScheduleChangesScreen_<state>`. Each state therefore is a function called `ScheduleChangesScreen` in a holder
 * class of its own; the scanner instantiates each holder by reflection.
 */

@Composable
private fun ScheduleChangesPreview(state: ScheduleChangesUiState) = ItmoPreview {
    ScheduleChangesScreen(state = state, onBack = {})
}

/** Today, yesterday and an earlier day; new and read rows; added, cancelled, one-field and several-field changes. */
internal class ScheduleChangesScreenListPreview {
    @Preview(name = "list")
    @Composable
    fun ScheduleChangesScreen() = ScheduleChangesPreview(ScheduleChangesUiState.Content(ScheduleChangesPreviewData.history))
}

internal class ScheduleChangesScreenEmptyPreview {
    @Preview(name = "empty")
    @Composable
    fun ScheduleChangesScreen() = ScheduleChangesPreview(ScheduleChangesUiState.Empty)
}
