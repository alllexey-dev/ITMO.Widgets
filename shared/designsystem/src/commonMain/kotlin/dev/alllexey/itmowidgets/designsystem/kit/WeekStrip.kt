package dev.alllexey.itmowidgets.designsystem.kit

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.tokens.rememberReducedMotion

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
 * One week, Monday to Sunday, as seven equal targets; the [selected] day sits on a `primary` indicator. A pager of
 * these is the schedule's week strip, which swipes by weeks; a tap reports the day's index.
 *
 * The indicator slides to a newly selected day on the spatial spring of the motion scheme and the colours crossfade;
 * a week that gains or loses the selection fades it in or out in place. Under reduced motion everything snaps.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WeekStrip(days: List<WeekStripDay>, selected: Int?, onDayClick: (Int) -> Unit, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    val motion = MaterialTheme.motionScheme
    val position = remember { Animatable((selected ?: 0).toFloat()) }
    val shown by animateFloatAsState(if (selected == null) 0f else 1f, if (reduced) snap() else motion.defaultEffectsSpec())
    val previous = remember { mutableStateOf(selected) }
    LaunchedEffect(selected) {
        val from = previous.value
        previous.value = selected
        val target = selected?.toFloat() ?: return@LaunchedEffect
        if (reduced || from == null) position.snapTo(target) else position.animateTo(target, motion.defaultSpatialSpec())
    }
    val indicator = ItmoTheme.colorScheme.primary
    val shape = ItmoTheme.shapes.large
    Row(
        modifier.fillMaxWidth().drawBehind {
            val gap = DayGap.toPx()
            val cell = (size.width - gap * (days.size - 1)) / days.size
            translate(left = position.value * (cell + gap)) {
                drawOutline(shape.createOutline(Size(cell, size.height), layoutDirection, this), indicator, alpha = shown)
            }
        },
        horizontalArrangement = Arrangement.spacedBy(DayGap),
    ) {
        days.forEachIndexed { index, day ->
            StripDayCell(day, index == selected, { onDayClick(index) }, Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StripDayCell(day: WeekStripDay, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = ItmoTheme.colorScheme
    val fade = if (rememberReducedMotion()) snap() else MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val content by animateColorAsState(
        when {
            selected -> colors.onPrimary
            day.today -> colors.primary
            day.past -> colors.onSurface.copy(alpha = PAST_LABEL_ALPHA)
            else -> colors.onSurface
        },
        fade,
    )
    val dot by animateColorAsState(if (selected) colors.onPrimary else colors.primary.copy(alpha = if (day.past) PAST_DOT_ALPHA else DOT_ALPHA), fade)
    Column(
        modifier
            .clip(ItmoTheme.shapes.large)
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
                Box(Modifier.size(DotSize).clip(CircleShape).background(if (warn) ItmoTheme.extendedColors.warning else dot))
            }
            if (dots == 0 && day.changed) Box(Modifier.size(DotSize).clip(CircleShape).background(ItmoTheme.extendedColors.warning))
        }
    }
}

private const val MAX_DOTS = 5
private const val DOT_ALPHA = 0.8f
private const val PAST_DOT_ALPHA = 0.4f
private const val PAST_LABEL_ALPHA = 0.65f
private val DotSize = 5.dp
private val DotGap = 2.dp
private val DayGap = 4.dp
