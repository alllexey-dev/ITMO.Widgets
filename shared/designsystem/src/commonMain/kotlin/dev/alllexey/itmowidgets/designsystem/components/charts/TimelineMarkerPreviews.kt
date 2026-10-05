package dev.alllexey.itmowidgets.designsystem.components.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/** The four states side by side on the day surface: upcoming, next, current, completed. */
@Preview
@Composable
private fun TimelineMarkerStatesPreview() = ItmoPreview {
    Row(
        Modifier
            .padding(ItmoTheme.spacing.screenMargin)
            .background(ItmoTheme.colorScheme.surfaceContainerLow, ItmoTheme.shapes.large)
            .padding(ItmoTheme.spacing.cardPadding),
        horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
    ) {
        listOf(
            TimelineMarkerState.Upcoming to "Предстоящая пара",
            TimelineMarkerState.Next to "Следующая пара",
            TimelineMarkerState.Current to "Пара идёт сейчас",
            TimelineMarkerState.Completed to "Пара завершилась",
        ).forEach { (state, description) -> TimelineMarker(state, description) }
    }
}

/** A day's timeline: the markers cover the line that runs under them; the last row is an auto-sign without a label. */
@Preview
@Composable
private fun TimelineMarkerOnLinePreview() = ItmoPreview {
    Column(
        Modifier
            .padding(ItmoTheme.spacing.screenMargin)
            .background(ItmoTheme.colorScheme.surfaceContainerLow, ItmoTheme.shapes.large)
            .padding(vertical = ItmoTheme.spacing.cardPadding),
    ) {
        TimelineRow(TimelineMarkerState.Completed, "08:20", "Пара завершилась")
        TimelineRow(TimelineMarkerState.Current, "10:00", "Пара идёт сейчас")
        TimelineRow(TimelineMarkerState.Next, "11:40", "Следующая пара")
        TimelineRow(TimelineMarkerState.Upcoming, "13:30", description = null)
    }
}

@Composable
private fun TimelineRow(state: TimelineMarkerState, time: String, description: String?) {
    Row(Modifier.height(48.dp), verticalAlignment = Alignment.Top) {
        Text(
            time,
            Modifier.width(48.dp).padding(end = 10.dp),
            style = ItmoTheme.typography.titleSmall,
            color = ItmoTheme.colorScheme.onSurface,
        )
        Box(Modifier.width(14.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            TimelineLine(Modifier.fillMaxHeight())
            TimelineMarker(state, description, Modifier.padding(top = 3.dp))
        }
    }
}
