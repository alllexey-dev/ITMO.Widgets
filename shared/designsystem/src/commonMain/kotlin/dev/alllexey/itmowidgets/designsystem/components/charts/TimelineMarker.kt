package dev.alllexey.itmowidgets.designsystem.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/** Where an item of a timeline stands relative to "now", which the caller decides. */
enum class TimelineMarkerState {
    /** Not started yet: a hollow neutral circle. */
    Upcoming,

    /** The next one to start: a `primary` circle with an inner dot. */
    Next,

    /** In progress: a filled `primary` marker. */
    Current,

    /** Over: a small filled neutral dot. */
    Completed,
}

/**
 * A timeline symbol for [state], not an action: 12 dp in a stable 14 dp area whose [surface] backdrop covers a
 * [TimelineLine] running under it. [contentDescription] is what TalkBack says for the state; `null` when a text next
 * to it already says it (an auto-sign row).
 */
@Composable
fun TimelineMarker(
    state: TimelineMarkerState,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    surface: Color = ItmoTheme.colorScheme.surfaceContainerLow,
) {
    val ink = when (state) {
        TimelineMarkerState.Current, TimelineMarkerState.Next -> ItmoTheme.colorScheme.primary
        TimelineMarkerState.Upcoming, TimelineMarkerState.Completed -> ItmoTheme.colorScheme.outline
    }
    val semantics = if (contentDescription == null) {
        Modifier.clearAndSetSemantics {}
    } else {
        Modifier.semantics { this.contentDescription = contentDescription }
    }
    Canvas(modifier.size(MarkerArea).then(semantics)) {
        drawCircle(surface)
        val ring = MarkerSize.toPx() / 2f
        if (state == TimelineMarkerState.Upcoming || state == TimelineMarkerState.Next) {
            val stroke = RingStroke.toPx()
            drawCircle(ink, ring - stroke / 2f, style = Stroke(stroke))
        }
        val fill = when (state) {
            TimelineMarkerState.Current -> ring
            TimelineMarkerState.Completed -> CompletedDot.toPx() / 2f
            TimelineMarkerState.Next -> NextDot.toPx() / 2f
            TimelineMarkerState.Upcoming -> 0f
        }
        if (fill > 0f) drawCircle(ink, fill)
    }
}

/** The vertical line a column of [TimelineMarker]s sits on; the caller gives it its height. */
@Composable
fun TimelineLine(modifier: Modifier = Modifier) {
    Box(
        modifier
            .width(LineWidth)
            .background(ItmoTheme.colorScheme.outlineVariant.copy(alpha = LINE_ALPHA))
            .clearAndSetSemantics {},
    )
}

// `renderTimelineMarker`'s layers: a 14 dp backdrop, a 12 dp ring of 2 dp (inset 1), dots inset 3 and 5 dp.
private val MarkerArea = 14.dp
private val MarkerSize = 12.dp
private val RingStroke = 2.dp
private val CompletedDot = 8.dp
private val NextDot = 4.dp

// `item_schedule_lesson.xml`'s `timeline_line`: 3 dp of `colorOutlineVariant` at alpha 0.6.
private val LineWidth = 3.dp
private const val LINE_ALPHA = 0.6f
