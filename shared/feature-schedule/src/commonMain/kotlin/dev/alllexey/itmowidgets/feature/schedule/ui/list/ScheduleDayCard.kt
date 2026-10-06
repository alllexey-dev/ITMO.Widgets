package dev.alllexey.itmowidgets.feature.schedule.ui.list

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.presentation.ScheduleDaySummary
import dev.alllexey.itmowidgets.feature.schedule.presentation.ScheduleDayUi
import dev.alllexey.itmowidgets.feature.schedule.presentation.ScheduleRowUi
import dev.alllexey.itmowidgets.shared.core.schedule_lesson_count
import dev.alllexey.itmowidgets.shared.core.schedule_no_lessons
import dev.alllexey.itmowidgets.shared.feature.schedule.Res
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_auto_sign_label
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format
import kotlinx.datetime.isoDayNumber
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes

/** Test tags of the schedule list; a host finds a day by its date and a row by its lesson or queue. */
object ScheduleListTestTags {
    fun day(date: LocalDate): String = "schedule_day_$date"

    fun lesson(pairId: Long): String = "schedule_lesson_$pairId"

    fun pending(queueId: Long): String = "schedule_pending_$queueId"

    /** The day's lesson count, auto-sign or "no lessons" pill. */
    const val SUMMARY = "schedule_day_summary"
}

/**
 * One day of the schedule (`Card.ScheduleDay`, port of `item_day_schedule.xml`): the weekday and the date, the
 * summary pill, then [day]'s rows on one timeline. Today has a 2 dp `primary` stroke, a `primary` title and pill; a
 * past day fades its content once to 0.72 and its rows add no alpha of their own. Rendered only from [day].
 */
@Composable
fun ScheduleDayCard(
    day: ScheduleDayUi,
    onLessonClick: (Lesson) -> Unit,
    onPendingClick: (PendingSportBooking) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ItmoTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth().testTag(ScheduleListTestTags.day(day.date)),
        shape = ItmoTheme.shapes.scheduleDay,
        color = colors.surfaceContainerLow,
        border = if (day.isToday) BorderStroke(TodayStroke, colors.primary) else null,
    ) {
        Column(
            Modifier
                .graphicsLayer { alpha = if (day.isPast) PAST_DAY_ALPHA else 1f }
                .padding(ItmoTheme.spacing.cardPadding),
        ) {
            DayHeader(day)
            Spacer(Modifier.height(ItmoTheme.spacing.content))
            ScheduleDayRows(day.rows, onLessonClick, onPendingClick)
        }
    }
}

@Composable
private fun DayHeader(day: ScheduleDayUi) {
    val colors = ItmoTheme.colorScheme
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(
                weekdayTitle(day.title.date),
                Modifier.semantics { heading() },
                color = if (day.isToday) colors.primary else colors.onSurface,
                style = ItmoTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                day.title.date.format(DateTexts.DAY_MONTH),
                color = colors.onSurfaceVariant,
                style = ItmoTheme.typography.bodyLarge,
            )
        }
        Text(
            summaryText(day.summary),
            Modifier
                .testTag(ScheduleListTestTags.SUMMARY)
                .border(PillStroke, if (day.isToday) colors.primary else colors.outline, ItmoTheme.shapes.small)
                .padding(horizontal = PillHorizontalPadding, vertical = PillVerticalPadding),
            color = colors.onSurfaceVariant,
            style = ItmoTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun summaryText(summary: ScheduleDaySummary): String = when (summary) {
    is ScheduleDaySummary.Lessons ->
        pluralStringResource(CoreRes.plurals.schedule_lesson_count, summary.count, summary.count)
    ScheduleDaySummary.AutoSignOnly -> stringResource(Res.string.schedule_auto_sign_label)
    ScheduleDaySummary.NoLessons -> stringResource(CoreRes.string.schedule_no_lessons)
}

/** The format's lowercase weekday name with a capital letter, as a card title. */
private fun weekdayTitle(date: LocalDate): String =
    DateTexts.Names.DAYS_FULL.names[date.dayOfWeek.isoDayNumber - 1].replaceFirstChar(Char::uppercaseChar)

/** The rows of one day; [ScheduleRowUi.NoLessons] is the only row of a day without any. */
@Composable
internal fun ScheduleDayRows(
    rows: List<ScheduleRowUi>,
    onLessonClick: (Lesson) -> Unit,
    onPendingClick: (PendingSportBooking) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gutter = rememberTimelineGutter(rows)
    Column(modifier.fillMaxWidth()) {
        rows.forEach { row ->
            when (row) {
                is ScheduleRowUi.LessonRow -> LessonRow(row, gutter, onClick = { onLessonClick(row.lesson) })
                is ScheduleRowUi.PendingSportRow -> PendingSportRow(row, gutter, onClick = { onPendingClick(row.booking) })
                is ScheduleRowUi.BreakRow -> BreakRow(row, gutter)
                ScheduleRowUi.NoLessons -> EmptyDayRow()
            }
        }
    }
}

/** `item_day_schedule.xml`'s past-day fade (design.md, past days 0.72). */
private const val PAST_DAY_ALPHA = 0.72f

/** Today's card stroke. */
private val TodayStroke = 2.dp

// `shape_pill_outline.xml`: a 1 dp stroke on 8 dp corners, 10 x 6 dp padding.
private val PillStroke = 1.dp
private val PillHorizontalPadding = 10.dp
private val PillVerticalPadding = 6.dp
