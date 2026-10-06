package dev.alllexey.itmowidgets.feature.sport.ui.sign

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview

/** This week with today selected: the previous arrow off, days with and without lessons. */
@Preview(name = "first-week")
@Composable
private fun SportWeekStripFirstWeekPreview() = ItmoPreview {
    SportWeekStrip(SportSignFiltersSamples.week(0), onPreviousWeek = {}, onNextWeek = {}, onSelectDate = {})
}

/** The last bookable week with its Monday selected: the next arrow off, the month changed. */
@Preview(name = "last-week")
@Composable
private fun SportWeekStripLastWeekPreview() = ItmoPreview {
    SportWeekStrip(
        SportSignFiltersSamples.week(SportSignFiltersSamples.LAST_WEEK),
        onPreviousWeek = {},
        onNextWeek = {},
        onSelectDate = {},
    )
}
