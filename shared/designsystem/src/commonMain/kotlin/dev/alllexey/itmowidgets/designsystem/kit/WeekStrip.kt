package dev.alllexey.itmowidgets.designsystem.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/**
 * A day of a [WeekStrip]: [weekday] `пн` over [number] `7`, a dot per lesson up to [lessons] (at most five), the last
 * in the warning colour when [changed]; [today] in `primary`, [past] quieter.
 */
@Immutable
data class WeekStripDay(
    val weekday: String,
    val number: String,
    val lessons: Int,
    val changed: Boolean,
    val today: Boolean,
    val past: Boolean,
)

/**
 * One week, Monday to Sunday, as seven equal targets; the [selected] day sits on `primary`. A pager of these is the
 * schedule's week strip, which swipes by weeks; a tap reports the day's index. Below seven touch targets (a 320 dp
 * phone) the days keep a touch target's width and overlap by a few dp instead of shrinking, as the sport strip does.
 */
@Composable
fun WeekStrip(days: List<WeekStripDay>, selected: Int?, onDayClick: (Int) -> Unit, modifier: Modifier = Modifier) {
    val minWidth = ItmoTheme.spacing.touchTarget
    Layout(
        content = { days.forEachIndexed { index, day -> StripDayCell(day, index == selected, { onDayClick(index) }) } },
        modifier = modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        if (measurables.isEmpty()) return@Layout layout(width, 0) {}
        val cell = maxOf(width / measurables.size, minWidth.roundToPx()).coerceAtMost(width)
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = cell, maxWidth = cell, minHeight = 0)) }
        val step = if (placeables.size > 1) (width - cell).toFloat() / (placeables.size - 1) else 0f
        layout(width, placeables.maxOf { it.height }) {
            placeables.forEachIndexed { index, day -> day.placeRelative((index * step).roundToInt(), 0) }
        }
    }
}

@Composable
private fun StripDayCell(day: WeekStripDay, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ItmoTheme.colorScheme
    val content = when {
        selected -> colors.onPrimary
        day.today -> colors.primary
        day.past -> colors.onSurfaceVariant
        else -> colors.onSurface
    }
    Column(
        modifier
            .padding(horizontal = DayGap / 2)
            .clip(ItmoTheme.shapes.large)
            .background(if (selected) colors.primary else Color.Transparent)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .heightIn(min = ItmoTheme.spacing.touchTarget)
            .padding(vertical = ItmoTheme.spacing.compact),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(day.weekday, color = content, style = ItmoTheme.typography.labelMedium, maxLines = 1)
        Text(
            day.number,
            color = content,
            style = ItmoTheme.typography.titleLarge,
            fontWeight = if (day.today || selected) FontWeight.SemiBold else null,
            maxLines = 1,
            softWrap = false,
        )
        Row(
            Modifier.height(DotSize),
            horizontalArrangement = Arrangement.spacedBy(DotGap),
        ) {
            val dots = day.lessons.coerceAtMost(MAX_DOTS)
            repeat(dots) { index ->
                val warn = day.changed && index == dots - 1
                Box(
                    Modifier.size(DotSize).clip(CircleShape).background(
                        when {
                            warn -> ItmoTheme.extendedColors.warning
                            selected -> colors.onPrimary
                            else -> colors.primary.copy(alpha = if (day.past) PAST_DOT_ALPHA else DOT_ALPHA)
                        },
                    ),
                )
            }
            if (dots == 0 && day.changed) Box(Modifier.size(DotSize).clip(CircleShape).background(ItmoTheme.extendedColors.warning))
        }
    }
}

private const val MAX_DOTS = 5
private const val DOT_ALPHA = 0.8f
private const val PAST_DOT_ALPHA = 0.4f
private val DotSize = 5.dp
private val DotGap = 2.dp
private val DayGap = 4.dp
