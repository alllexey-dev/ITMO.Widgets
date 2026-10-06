package dev.alllexey.itmowidgets.feature.schedule.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import dev.alllexey.itmowidgets.core.schedule.lessonTypeName
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.core.text.buildingShortTitle
import dev.alllexey.itmowidgets.core.text.roomShortTitle
import dev.alllexey.itmowidgets.designsystem.components.charts.TimelineMarker
import dev.alllexey.itmowidgets.designsystem.components.charts.TimelineMarkerState
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.presentation.PendingSportStatus
import dev.alllexey.itmowidgets.feature.schedule.presentation.ScheduleLessonState
import dev.alllexey.itmowidgets.feature.schedule.presentation.ScheduleRowUi
import dev.alllexey.itmowidgets.shared.core.schedule_auto_sign_prediction_description
import dev.alllexey.itmowidgets.shared.core.schedule_auto_sign_waiting_description
import dev.alllexey.itmowidgets.shared.core.schedule_unknown_subject
import dev.alllexey.itmowidgets.shared.designsystem.ic_edit_calendar
import dev.alllexey.itmowidgets.shared.designsystem.ic_location_on
import dev.alllexey.itmowidgets.shared.designsystem.ic_person
import dev.alllexey.itmowidgets.shared.designsystem.ic_pin
import dev.alllexey.itmowidgets.shared.designsystem.ic_videocam
import dev.alllexey.itmowidgets.shared.feature.schedule.Res
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_auto_sign_prediction
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_auto_sign_waiting
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_break_range
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_day_empty
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_lesson_changed
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_lesson_link_indicator
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_timeline_completed
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_timeline_current
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_timeline_next
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_timeline_upcoming
import kotlinx.datetime.LocalTime
import kotlinx.datetime.format
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/**
 * Where a day's timeline runs: [width] from the row's start to the line, sized to the widest `HH:mm` of the day in
 * the current font, and the height of the start time's line, which the marker centres on.
 */
@Immutable
internal data class TimelineGutter(val width: Dp, val startLineHeight: Dp)

/**
 * The gutter of [rows], measured once per day like `LessonAdapter.updateTimelineGuide`: a fixed gutter clips time
 * labels at larger fonts, and one width per day keeps breaks and lessons on the same line.
 */
@Composable
internal fun rememberTimelineGutter(rows: List<ScheduleRowUi>): TimelineGutter {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = startTimeStyle()
    val times = remember(rows) { rows.flatMap { it.times() }.map { it.format(DateTexts.TIME) }.distinct() }
    return remember(times, style, density) {
        val widest = times.maxOfOrNull { measurer.measure(it, style).size.width } ?: 0
        val lineHeight = measurer.measure(TIME_SAMPLE, style).size.height
        with(density) {
            TimelineGutter(
                width = maxOf(MinGutter, widest.toDp() + TimeGap),
                startLineHeight = lineHeight.toDp(),
            )
        }
    }
}

private fun ScheduleRowUi.times(): List<LocalTime> = when (this) {
    is ScheduleRowUi.LessonRow -> listOf(lesson.start, lesson.end)
    is ScheduleRowUi.PendingSportRow -> listOf(start, end)
    is ScheduleRowUi.BreakRow, ScheduleRowUi.NoLessons -> emptyList()
}

@Composable
private fun startTimeStyle(): TextStyle = ItmoTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)

/**
 * An official lesson (`item_schedule_lesson.xml`): times in the gutter, the [TimelineMarker] of its time state, then
 * the subject, the type with its colour, the link and changed marks, the teacher, the note and the place. Only the
 * colours tell a completed lesson apart; the row itself stays opaque.
 */
@Composable
internal fun LessonRow(row: ScheduleRowUi.LessonRow, gutter: TimelineGutter, onClick: () -> Unit) {
    val lesson = row.lesson
    val colors = ItmoTheme.colorScheme
    TimelineEntry(
        gutter = gutter,
        start = lesson.start,
        end = lesson.end,
        startColor = when (row.state) {
            ScheduleLessonState.CURRENT -> colors.primary
            ScheduleLessonState.COMPLETED -> colors.onSurfaceVariant
            ScheduleLessonState.NEXT, ScheduleLessonState.UPCOMING -> colors.onSurface
        },
        marker = row.state.marker(),
        markerDescription = stringResource(row.state.description()),
        isLast = row.isLast,
        onClick = onClick,
        modifier = Modifier.testTag(ScheduleListTestTags.lesson(lesson.pairId)),
    ) {
        RowTitle(
            lesson.subjectName.ifBlank { stringResource(CoreRes.string.schedule_unknown_subject) },
            if (row.state == ScheduleLessonState.COMPLETED) colors.onSurfaceVariant else colors.onSurface,
        )
        MetaLine(leading = { TypeDot(lessonTypeColor(lesson.typeId.raw)) }) {
            MetaText(stringResource(lessonTypeName(lesson.typeId.raw)))
            if (!lesson.zoomUrl.isNullOrBlank()) {
                Mark(KitRes.drawable.ic_videocam, stringResource(Res.string.schedule_lesson_link_indicator), colors.onSurfaceVariant)
            }
            if (row.changed) {
                Mark(KitRes.drawable.ic_edit_calendar, stringResource(Res.string.schedule_lesson_changed), colors.primary)
            }
        }
        lesson.teacherFio?.let { teacher -> IconLine(KitRes.drawable.ic_person, teacher) }
        lesson.note?.let { note -> IconLine(KitRes.drawable.ic_pin, note.trim()) }
        if (lesson.hasLocation()) {
            MetaLine(leading = { MetaIcon(KitRes.drawable.ic_location_on) }) {
                MetaText(lesson.room?.let { roomShortTitle(it.raw).asString() }.orEmpty(), Modifier.padding(end = RoomGap))
                Text(
                    lesson.building?.let { buildingShortTitle(it.raw, BUILDING_MAX_LENGTH).asString() }.orEmpty(),
                    color = colors.onSurfaceVariant,
                    style = ItmoTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * A pending sport auto-sign row: the lesson's anatomy with the outlined marker of a lesson not yet confirmed, the
 * sport colour and the waiting or predicted status in place of the type; no link, change mark, note or building.
 */
@Composable
internal fun PendingSportRow(row: ScheduleRowUi.PendingSportRow, gutter: TimelineGutter, onClick: () -> Unit) {
    val booking = row.booking
    val prediction = row.status == PendingSportStatus.PREDICTION
    val statusDescription = stringResource(
        if (prediction) CoreRes.string.schedule_auto_sign_prediction_description
        else CoreRes.string.schedule_auto_sign_waiting_description,
    )
    TimelineEntry(
        gutter = gutter,
        start = row.start,
        end = row.end,
        startColor = ItmoTheme.colorScheme.onSurface,
        marker = TimelineMarkerState.Upcoming,
        // The status next to it already tells TalkBack what the row is.
        markerDescription = null,
        isLast = row.isLast,
        onClick = onClick,
        modifier = Modifier.testTag(ScheduleListTestTags.pending(booking.queueId)),
    ) {
        RowTitle(booking.sectionName, ItmoTheme.colorScheme.onSurface)
        MetaLine(leading = { TypeDot(ItmoTheme.extendedColors.lessonTypeSport) }) {
            MetaText(
                stringResource(if (prediction) Res.string.schedule_auto_sign_prediction else Res.string.schedule_auto_sign_waiting),
                Modifier.clearAndSetSemantics { contentDescription = statusDescription },
            )
        }
        if (booking.teacherFio.isNotBlank()) IconLine(KitRes.drawable.ic_person, booking.teacherFio)
        if (booking.roomName.isNotBlank()) IconLine(KitRes.drawable.ic_location_on, booking.roomName)
    }
}

/** A gap of over an hour between lessons (`item_schedule_break.xml`): the line runs on behind the text. */
@Composable
internal fun BreakRow(row: ScheduleRowUi.BreakRow, gutter: TimelineGutter) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = BreakMinHeight)
            .timelineLine(gutter.width, ItmoTheme.colorScheme.outlineVariant),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            stringResource(Res.string.schedule_break_range, row.from.format(DateTimeFormat), row.to.format(DateTimeFormat)),
            Modifier.padding(start = gutter.width + BreakTextInset, end = BreakTextEnd),
            color = ItmoTheme.colorScheme.onSurfaceVariant,
            style = ItmoTheme.typography.bodySmall,
        )
    }
}

/** A day without lessons and without auto-sign rows (`item_schedule_empty.xml`). */
@Composable
internal fun EmptyDayRow() {
    Text(
        stringResource(Res.string.schedule_day_empty),
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ItmoTheme.spacing.group, vertical = ItmoTheme.spacing.content),
        color = ItmoTheme.colorScheme.onSurface,
        style = ItmoTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
    )
}

/**
 * A row on the timeline: start and end times right-aligned in the gutter, the line under the whole row and the
 * marker centred on the start time's line, the [content] after it as one touch target of at least 48 dp. Every row
 * but the last keeps 16 dp to the next one; the line runs through that gap.
 */
@Composable
private fun TimelineEntry(
    gutter: TimelineGutter,
    start: LocalTime,
    end: LocalTime,
    startColor: Color,
    marker: TimelineMarkerState,
    markerDescription: String?,
    isLast: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = ItmoTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .timelineLine(gutter.width, colors.outlineVariant),
    ) {
        Column(Modifier.width(gutter.width - TimeGap), horizontalAlignment = Alignment.End) {
            Text(start.format(DateTimeFormat), color = startColor, style = startTimeStyle(), maxLines = 1)
            Text(
                end.format(DateTimeFormat),
                Modifier.padding(top = EndTimeGap),
                color = colors.onSurfaceVariant,
                style = ItmoTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
        TimelineMarker(
            marker,
            markerDescription,
            Modifier.offset(x = gutter.width - MarkerArea / 2, y = (gutter.startLineHeight - MarkerArea) / 2),
        )
        Column(
            modifier
                .padding(start = gutter.width + ContentInset, bottom = if (isLast) 0.dp else RowGap)
                .fillMaxWidth()
                .heightIn(min = ItmoTheme.spacing.touchTarget)
                .clickable(onClick = onClick)
                .padding(horizontal = ContentPadding),
            content = content,
        )
    }
}

/** The 3 dp line at 0.6 alpha of `outlineVariant` (`TimelineLine`'s look), centred on [x], over the full height. */
private fun Modifier.timelineLine(x: Dp, color: Color): Modifier = drawBehind {
    val width = LineWidth.toPx()
    drawRect(
        color.copy(alpha = LINE_ALPHA),
        topLeft = Offset(x.toPx() - width / 2f, 0f),
        size = Size(width, size.height),
    )
}

@Composable
private fun RowTitle(text: String, color: Color) {
    val base = ItmoTheme.typography.titleMedium
    Text(
        text,
        color = color,
        style = base.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = TitleLetterSpacing,
            hyphens = Hyphens.Auto,
            lineBreak = LineBreak.Paragraph,
        ),
    )
}

/** A line of metadata under the title: a 16 dp leading slot, 8 dp, then [content]. */
@Composable
private fun MetaLine(leading: @Composable () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(Modifier.padding(top = MetaGap), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(MetaIconSize), contentAlignment = Alignment.Center) { leading() }
        Spacer(Modifier.width(ItmoTheme.spacing.compact))
        content()
    }
}

@Composable
private fun IconLine(icon: DrawableResource, text: String) {
    MetaLine(leading = { MetaIcon(icon) }) { MetaText(text) }
}

@Composable
private fun MetaIcon(icon: DrawableResource) {
    Icon(painterResource(icon), contentDescription = null, Modifier.size(MetaIconSize), tint = ItmoTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun MetaText(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodySmall)
}

/** The link or changed mark after the type, with what it means for TalkBack. */
@Composable
private fun Mark(icon: DrawableResource, description: String, tint: Color) {
    Icon(
        painterResource(icon),
        contentDescription = description,
        Modifier.padding(start = ItmoTheme.spacing.compact).size(MetaIconSize),
        tint = tint,
    )
}

/** `indicator_circle.xml` at its 0.7 scale in the 16 dp slot. */
@Composable
private fun TypeDot(color: Color) {
    Box(Modifier.size(TypeDotSize).background(color, CircleShape))
}

/** MyITMO lesson type ids as every feature colours them (`core/ui/LessonTypes.kt` for Views). */
@Composable
private fun lessonTypeColor(typeId: Int): Color {
    val colors = ItmoTheme.extendedColors
    return when (typeId) {
        -1 -> colors.lessonTypeFree
        1 -> colors.lessonTypeLecture
        2 -> colors.lessonTypeLab
        3 -> colors.lessonTypePractice
        4, 5, 6, 7, 8, 9 -> colors.lessonTypeAssessment
        10 -> colors.lessonTypeConsultation
        11 -> colors.lessonTypeSport
        else -> colors.lessonTypeDefault
    }
}

private fun ScheduleLessonState.marker(): TimelineMarkerState = when (this) {
    ScheduleLessonState.UPCOMING -> TimelineMarkerState.Upcoming
    ScheduleLessonState.NEXT -> TimelineMarkerState.Next
    ScheduleLessonState.CURRENT -> TimelineMarkerState.Current
    ScheduleLessonState.COMPLETED -> TimelineMarkerState.Completed
}

private fun ScheduleLessonState.description() = when (this) {
    ScheduleLessonState.UPCOMING -> Res.string.schedule_timeline_upcoming
    ScheduleLessonState.NEXT -> Res.string.schedule_timeline_next
    ScheduleLessonState.CURRENT -> Res.string.schedule_timeline_current
    ScheduleLessonState.COMPLETED -> Res.string.schedule_timeline_completed
}

private val DateTimeFormat = DateTexts.TIME

/** The text the start time's line height is measured with; every `HH:mm` has the same height. */
private const val TIME_SAMPLE = "00:00"

/** `LessonAdapter`'s building cut for the row. */
private const val BUILDING_MAX_LENGTH = 10

// `item_schedule_lesson.xml` and `LessonAdapter`: a gutter of at least 50 dp, times 10 dp before the line, the end
// time 2 dp under the start, the content 12 dp after the line with 4 dp padding, 16 dp between rows.
private val MinGutter = 50.dp
private val TimeGap = 10.dp
private val EndTimeGap = 2.dp
private val ContentInset = 12.dp
private val ContentPadding = 4.dp
private val RowGap = 16.dp
private val MetaGap = 4.dp
private val MetaIconSize = 16.dp
private val TypeDotSize = 11.2.dp
private val RoomGap = 4.dp
private val TitleLetterSpacing = 0.01.em

/** `TimelineMarker`'s stable area, centred on the line. */
private val MarkerArea = 14.dp

// `TimelineLine`'s look: 3 dp of `outlineVariant` at 0.6.
private val LineWidth = 3.dp
private const val LINE_ALPHA = 0.6f

// `item_schedule_break.xml`: at least 36 dp, the text 24 dp after the line and 16 dp before the end.
private val BreakMinHeight = 36.dp
private val BreakTextInset = 24.dp
private val BreakTextEnd = 16.dp
