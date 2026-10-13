package dev.alllexey.itmowidgets.designsystem.kit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_history
import org.jetbrains.compose.resources.painterResource

/** One row of a [LessonTimeline], computed by the shared presentation. */
sealed interface TimelineRow {

    /**
     * A lesson and the rail under its dot, which spans the lesson and the short break after it: [progress] 0..1 of it
     * is elapsed; [reached] colours the rail above the dot. [changeNote] is the quiet line about a change under the
     * lesson (`Изменено вчера в 20:14`).
     */
    @Immutable
    data class Lesson(
        val lesson: LessonView,
        val progress: Float,
        val reached: Boolean,
        val changeNote: String? = null,
    ) : TimelineRow

    /** A gap of an hour or more: [label] `Окно 2 ч 10 мин`, [progress] 0..1 of it elapsed. */
    @Immutable
    data class Gap(val label: String, val progress: Float) : TimelineRow
}

/**
 * The lessons of one day on a time rail, the app's one day list: the time column, a rail with a dot per lesson in
 * its type's colour, the elapsed part of the day in `primary`, and the lesson's lines without a card around them;
 * done lessons are dimmed, the current one sits on `primaryContainer`. With [showNow] the `сейчас` marker stands where
 * the elapsed part ends inside the current lesson or gap (today); without it another day shows no marker.
 */
@Composable
fun LessonTimeline(rows: List<TimelineRow>, modifier: Modifier = Modifier, showNow: Boolean = false) {
    val firstLesson = rows.indexOfFirst { it is TimelineRow.Lesson }
    val lastLesson = rows.indexOfLast { it is TimelineRow.Lesson }
    Column(modifier.fillMaxWidth()) {
        rows.forEachIndexed { index, row ->
            when (row) {
                is TimelineRow.Lesson -> LessonRow(row, index == firstLesson, index == lastLesson, showNow)
                is TimelineRow.Gap -> GapRow(row, showNow)
            }
        }
    }
}

@Composable
private fun LessonRow(row: TimelineRow.Lesson, first: Boolean, last: Boolean, showNow: Boolean) {
    val lesson = row.lesson
    val now = lesson.state == LessonState.Now
    val done = lesson.state == LessonState.Done
    val rail = railColors()
    val type = lesson.accent
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // The column grows with a large font instead of breaking `10:00`.
        Column(
            Modifier.widthIn(min = TimeColumn).padding(top = ItmoTheme.spacing.content).alpha(if (done) DONE_ALPHA else 1f),
        ) {
            Text(
                lesson.start,
                color = if (now) ItmoTheme.colorScheme.primary else ItmoTheme.colorScheme.onSurface,
                style = ItmoTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                softWrap = false,
            )
            Text(
                lesson.end,
                color = ItmoTheme.colorScheme.onSurfaceVariant,
                style = ItmoTheme.typography.bodySmall,
                softWrap = false,
            )
        }
        Box(
            Modifier.width(RailWidth).fillMaxHeight().drawBehind {
                val dotY = DotY.toPx()
                val center = Offset(size.width / 2, dotY)
                if (!first) {
                    drawLine(
                        if (row.reached) rail.passed else rail.ahead,
                        Offset(center.x, 0f),
                        center,
                        RailStroke.toPx(),
                    )
                }
                val bottom = if (last) size.height - RailEndInset.toPx() else size.height
                rail(dotY, bottom, row.progress, showNow, rail)
                if (lesson.cancelled) {
                    drawCircle(type, DotRadius.toPx(), center, style = Stroke(RailStroke.toPx()))
                } else {
                    drawCircle(rail.under, (if (now) NowDotRing else DotRing).toPx(), center)
                    drawCircle(
                        if (done) type.copy(alpha = DONE_DOT_ALPHA) else type,
                        (if (now) NowDotRadius else DotRadius).toPx(),
                        center,
                    )
                }
            },
        )
        Surface(
            Modifier
                .weight(1f)
                .padding(start = ItmoTheme.spacing.related, top = LessonTopGap, bottom = LessonBottomGap)
                .alpha(if (done) DONE_ALPHA else 1f),
            shape = ItmoTheme.shapes.cardContent,
            color = if (now) ItmoTheme.colorScheme.primaryContainer else Color.Transparent,
            contentColor = if (now) ItmoTheme.colorScheme.onPrimaryContainer else ItmoTheme.colorScheme.onSurface,
        ) {
            Column(
                Modifier.padding(horizontal = ItmoTheme.spacing.content, vertical = ItmoTheme.spacing.compact),
                verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.related),
            ) {
                LessonLines(lesson)
                if (row.changeNote != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(NoteIconGap),
                    ) {
                        Icon(
                            painterResource(Res.drawable.ic_history),
                            contentDescription = null,
                            Modifier.size(NoteIconSize),
                            tint = ItmoTheme.extendedColors.warning,
                        )
                        Text(row.changeNote, color = secondary(now), style = ItmoTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun GapRow(row: TimelineRow.Gap, showNow: Boolean) {
    val rail = railColors()
    Row(Modifier.fillMaxWidth().height(GapHeight), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(TimeColumn))
        Box(
            Modifier.width(RailWidth).fillMaxHeight().drawBehind {
                rail(0f, size.height, row.progress, showNow, rail, dashed = true)
            },
        )
        Text(
            row.label,
            Modifier.padding(start = ItmoTheme.spacing.group),
            color = ItmoTheme.colorScheme.onSurfaceVariant,
            style = ItmoTheme.typography.labelMedium,
        )
    }
}

private class RailColors(val passed: Color, val ahead: Color, val under: Color)

@Composable
private fun railColors() = RailColors(ItmoTheme.colorScheme.primary, ItmoTheme.colorScheme.outlineVariant, underSurface())

/** A rail stretch from [top] to [bottom]: elapsed up to [progress], the now marker at the split when [marker]. */
private fun DrawScope.rail(
    top: Float,
    bottom: Float,
    progress: Float,
    marker: Boolean,
    colors: RailColors,
    dashed: Boolean = false,
) {
    val x = size.width / 2
    val width = RailStroke.toPx()
    val dash = if (dashed) PathEffect.dashPathEffect(floatArrayOf(DashOn.toPx(), DashOn.toPx())) else null
    val split = top + (bottom - top) * progress.coerceIn(0f, 1f)
    if (split > top) drawLine(colors.passed, Offset(x, top), Offset(x, split), width, pathEffect = dash)
    if (bottom > split) drawLine(colors.ahead, Offset(x, split), Offset(x, bottom), width, pathEffect = dash)
    if (marker && progress > 0f && progress < 1f) {
        drawCircle(colors.passed, MarkerRadius.toPx(), Offset(x, split))
        drawCircle(colors.under, MarkerHole.toPx(), Offset(x, split))
    }
}

private const val DONE_DOT_ALPHA = 0.5f

private val TimeColumn = 48.dp
private val RailWidth = 24.dp
private val RailStroke = 2.dp
private val RailEndInset = 6.dp
private val DotY = 22.dp
private val DotRadius = 5.dp
private val DotRing = 7.dp
private val NowDotRadius = 7.dp
private val NowDotRing = 9.dp
private val MarkerRadius = 5.dp
private val MarkerHole = 2.dp
private val DashOn = 3.dp
private val GapHeight = 32.dp
private val LessonTopGap = 2.dp
private val LessonBottomGap = 6.dp
private val NoteIconSize = 16.dp
private val NoteIconGap = 6.dp
