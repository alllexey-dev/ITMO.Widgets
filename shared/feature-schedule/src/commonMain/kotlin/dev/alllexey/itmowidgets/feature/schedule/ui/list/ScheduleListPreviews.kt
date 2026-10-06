package dev.alllexey.itmowidgets.feature.schedule.ui.list

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.schedule.ui.list.preview.ScheduleListPreviewData
import dev.alllexey.itmowidgets.feature.schedule.ui.list.preview.ScheduleListPreviewData.today
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/*
 * One day card per preview, named `ScheduleList<Kind>` so the goldens sit next to the screen's. Together they cover
 * `ScheduleCardsVisualTest`: every timeline shape and the line, the auto-sign anatomy, the changed and link marks,
 * the past-day fade, the summary pill's three texts; `-Pshots.appearance=full` adds 320 dp at font 1.3.
 */

@Composable
private fun DayPreview(date: LocalDate) = ItmoPreview {
    ScheduleDayCard(
        ScheduleListPreviewData.dayOf(date),
        onLessonClick = {},
        onPendingClick = {},
        modifier = Modifier.padding(ItmoTheme.spacing.screenMargin),
    )
}

/** Today: completed, changed, current, a break, next with a link, a waiting and a predicted auto-sign row. */
@Preview(heightDp = TALL_WINDOW_DP)
@Composable
private fun ScheduleListToday() = DayPreview(today)

/** Upcoming lessons, an assessment in the assembly hall. */
@Preview(heightDp = TALL_WINDOW_DP)
@Composable
private fun ScheduleListTomorrow() = DayPreview(today.plus(1, DateTimeUnit.DAY))

/** A past day fades once. */
@Preview(heightDp = TALL_WINDOW_DP)
@Composable
private fun ScheduleListPast() = DayPreview(today.minus(1, DateTimeUnit.DAY))

/** Only an auto-sign row without teacher and place: the pill names auto-sign instead of a count. */
@Preview(heightDp = TALL_WINDOW_DP)
@Composable
private fun ScheduleListAutoSign() = DayPreview(today.plus(2, DateTimeUnit.DAY))

/** A day without lessons. */
@Preview(heightDp = TALL_WINDOW_DP)
@Composable
private fun ScheduleListEmptyDay() = DayPreview(today.plus(3, DateTimeUnit.DAY))

/** A blank subject, an unknown building cut to ten letters, a long title and a room with a part. */
@Preview(heightDp = TALL_WINDOW_DP)
@Composable
private fun ScheduleListLong() = DayPreview(today.plus(4, DateTimeUnit.DAY))

/** A window taller than the longest day at 320 dp and font 1.3, so no card is cut and every row is checked. */
private const val TALL_WINDOW_DP = 2400
