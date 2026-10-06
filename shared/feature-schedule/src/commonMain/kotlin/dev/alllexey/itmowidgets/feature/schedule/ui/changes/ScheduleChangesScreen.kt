package dev.alllexey.itmowidgets.feature.schedule.ui.changes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.schedule.lessonTypeName
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.core.text.listLines
import dev.alllexey.itmowidgets.core.text.listSummary
import dev.alllexey.itmowidgets.designsystem.components.bars.AppTopBar
import dev.alllexey.itmowidgets.designsystem.components.bars.AppTopBarBack
import dev.alllexey.itmowidgets.designsystem.components.state.ContentState
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.schedule.presentation.changes.RelativeDay
import dev.alllexey.itmowidgets.feature.schedule.presentation.changes.ScheduleChangeDay
import dev.alllexey.itmowidgets.feature.schedule.presentation.changes.ScheduleChangeRow
import dev.alllexey.itmowidgets.feature.schedule.presentation.changes.ScheduleChangesUiState
import dev.alllexey.itmowidgets.shared.core.common_back
import dev.alllexey.itmowidgets.shared.core.home_schedule_today
import dev.alllexey.itmowidgets.shared.core.schedule_changes_title
import dev.alllexey.itmowidgets.shared.core.schedule_unknown_subject
import dev.alllexey.itmowidgets.shared.designsystem.ic_history
import dev.alllexey.itmowidgets.shared.feature.schedule.Res
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_change_new
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_changes_empty_description
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_changes_empty_title
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_changes_yesterday
import kotlinx.datetime.format
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/** Test tags of [ScheduleChangesScreen]. */
object ScheduleChangesTestTags {
    const val LIST = "schedule_changes_list"

    /** The empty state in the list's place. */
    const val EMPTY = "schedule_changes_empty"

    const val DAY = "schedule_changes_day"

    /** A row, one node whose description starts with the "new" label while its dot shows. */
    fun row(id: String): String = "schedule_changes_row:$id"
}

/**
 * The schedule changes of the last 30 days by the day they were found, newest first. The rows are information only:
 * nothing reacts to a tap and TalkBack reads each row as one node. While the local file is read the area stays
 * blank instead of flashing a placeholder. Stateless; [ScheduleChangesRoute] feeds it.
 */
@Composable
fun ScheduleChangesScreen(
    state: ScheduleChangesUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(ItmoTheme.colorScheme.surface)) {
        AppTopBar(
            title = stringResource(CoreRes.string.schedule_changes_title),
            navigation = { AppTopBarBack(stringResource(CoreRes.string.common_back), onBack) },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (state) {
                ScheduleChangesUiState.Loading -> Unit
                ScheduleChangesUiState.Empty -> ContentState(
                    title = stringResource(Res.string.schedule_changes_empty_title),
                    modifier = Modifier.fillMaxSize().testTag(ScheduleChangesTestTags.EMPTY),
                    icon = painterResource(KitRes.drawable.ic_history),
                    description = stringResource(Res.string.schedule_changes_empty_description),
                )
                is ScheduleChangesUiState.Content -> ChangeList(state.days)
            }
        }
    }
}

@Composable
private fun ChangeList(days: List<ScheduleChangeDay>) {
    LazyColumn(
        Modifier.fillMaxSize().testTag(ScheduleChangesTestTags.LIST),
        contentPadding = PaddingValues(bottom = ItmoTheme.spacing.group),
    ) {
        days.forEach { day ->
            item(key = "day-${day.date}", contentType = CONTENT_DAY) { DayTitle(day) }
            items(day.rows, key = { "change-${it.change.id}" }, contentType = { CONTENT_CHANGE }) { ChangeRow(it) }
        }
    }
}

/** Today, yesterday or a date such as `30 сентября`; another year is spelled out. */
@Composable
private fun DayTitle(day: ScheduleChangeDay) {
    val title = when (day.relative) {
        RelativeDay.TODAY -> stringResource(CoreRes.string.home_schedule_today)
        RelativeDay.YESTERDAY -> stringResource(Res.string.schedule_changes_yesterday)
        RelativeDay.OTHER -> day.date.format(DateTexts.DAY_MONTH)
        RelativeDay.OTHER_YEAR -> day.date.format(DateTexts.DAY_MONTH_YEAR)
    }
    Text(
        title,
        Modifier
            .fillMaxWidth()
            .padding(
                start = ItmoTheme.spacing.screenMargin,
                top = ItmoTheme.spacing.group,
                end = ItmoTheme.spacing.screenMargin,
                bottom = ItmoTheme.spacing.compact,
            )
            .semantics { heading() }
            .testTag(ScheduleChangesTestTags.DAY),
        color = ItmoTheme.colorScheme.onSurfaceVariant,
        style = ItmoTheme.typography.titleSmall,
    )
}

/**
 * A `Card.Content` row: the subject with the "new" dot, the main line, one `было -> стало` line per changed field
 * when there are several, then the lesson type and the flow.
 */
@Composable
private fun ChangeRow(row: ScheduleChangeRow) {
    val change = row.change
    val subject = change.subjectName.trim().ifEmpty { stringResource(CoreRes.string.schedule_unknown_subject) }
    val summary = change.listSummary().asString()
    val lines = change.listLines().map { it.asString() }
    val meta = listOfNotNull(
        stringResource(lessonTypeName(change.typeId)),
        change.flowName?.trim()?.takeIf(String::isNotEmpty),
    ).joinToString(" · ")
    val newLabel = if (row.isNew) stringResource(Res.string.schedule_change_new) else null
    val description = (listOfNotNull(newLabel, subject, summary) + lines + meta).joinToString(". ")
    val colors = ItmoTheme.colorScheme
    val spacing = ItmoTheme.spacing

    Surface(
        Modifier
            .fillMaxWidth()
            .padding(start = spacing.screenMargin, end = spacing.screenMargin, bottom = spacing.compact)
            .clearAndSetSemantics {
                contentDescription = description
                testTag = ScheduleChangesTestTags.row(change.id)
            },
        shape = ItmoTheme.shapes.cardContent,
        color = colors.surfaceContainerLow,
    ) {
        Column(Modifier.padding(spacing.cardPadding)) {
            Row {
                Text(
                    subject,
                    Modifier.weight(1f),
                    color = colors.onSurface,
                    style = ItmoTheme.typography.titleSmall,
                )
                if (row.isNew) {
                    Spacer(Modifier.width(spacing.compact))
                    NewMark(Modifier.padding(top = centredOnFirstLine(NewMarkSize)))
                }
            }
            Text(
                summary,
                Modifier.fillMaxWidth().padding(top = spacing.related),
                color = colors.onSurface,
                style = ItmoTheme.typography.bodyMedium,
            )
            if (lines.isNotEmpty()) {
                // One text per changed field, so a wrapped field does not run into the next one.
                Column(Modifier.padding(top = spacing.related), verticalArrangement = Arrangement.Top) {
                    lines.forEach { line ->
                        Text(
                            line,
                            Modifier.fillMaxWidth().padding(top = spacing.related),
                            color = colors.onSurfaceVariant,
                            style = ItmoTheme.typography.bodySmall.copy(
                                hyphens = Hyphens.Auto,
                                lineBreak = LineBreak.Paragraph,
                            ),
                        )
                    }
                }
            }
            Text(
                meta,
                Modifier.fillMaxWidth().padding(top = spacing.compact),
                color = colors.onSurfaceVariant,
                style = ItmoTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun NewMark(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(NewMarkSize)
            .background(ItmoTheme.colorScheme.primary, CircleShape),
    )
}

/** The top inset that centres an item of [size] on the subject's first line, as `alignRailIcon` did. */
@Composable
private fun centredOnFirstLine(size: Dp): Dp {
    val lineHeight = with(LocalDensity.current) { ItmoTheme.typography.titleSmall.lineHeight.toDp() }
    return ((lineHeight - size) / 2).coerceAtLeast(0.dp)
}

/** `item_schedule_change.xml`'s 8 dp `shape_circle_filled`. */
private val NewMarkSize = 8.dp

private const val CONTENT_DAY = "day"
private const val CONTENT_CHANGE = "change"
