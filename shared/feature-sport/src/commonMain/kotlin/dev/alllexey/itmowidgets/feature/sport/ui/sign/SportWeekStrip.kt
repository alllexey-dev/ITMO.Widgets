package dev.alllexey.itmowidgets.feature.sport.ui.sign

import androidx.compose.animation.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.designsystem.gesture.tabSwipeBlocked
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.tokens.rememberReducedMotion
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.CalendarDay
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignUiState
import dev.alllexey.itmowidgets.shared.feature.sport.Res
import dev.alllexey.itmowidgets.shared.feature.sport.sport_calendar_available
import dev.alllexey.itmowidgets.shared.feature.sport.sport_calendar_empty
import dev.alllexey.itmowidgets.shared.feature.sport.sport_calendar_has_lessons
import dev.alllexey.itmowidgets.shared.feature.sport.sport_calendar_today
import dev.alllexey.itmowidgets.shared.feature.sport.sport_next_week
import dev.alllexey.itmowidgets.shared.feature.sport.sport_previous_week
import kotlin.math.roundToInt
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes
import dev.alllexey.itmowidgets.shared.designsystem.ic_chevron_left
import dev.alllexey.itmowidgets.shared.designsystem.ic_chevron_right

object SportWeekStripTestTags {
    const val STRIP = "sport_week_strip"
    const val PAGER = "sport_week_strip_pager"
    const val MONTH = "sport_week_strip_month"
    const val PREVIOUS = "sport_week_strip_previous"
    const val NEXT = "sport_week_strip_next"

    fun day(date: LocalDate): String = "sport_week_strip_day_$date"

    fun dayCard(date: LocalDate): String = "sport_week_strip_day_card_$date"
}

/**
 * The `Запись` week strip: the month of the shown week, the previous and next week arrows, and the week's seven days.
 * The weeks sit in a [HorizontalPager] that only the arrows move ([SportSignUiState.Content.selectedWeekIndex] after
 * [onPreviousWeek] or [onNextWeek]); a horizontal swipe moves neither the week nor the bottom tabs
 * ([tabSwipeBlocked], design.md "Tab swipe" rule 4). A day is picked by a tap ([onSelectDate]). The month label fades
 * in when the month changes and the newly selected day scales in, on the standard motion scheme's springs like the
 * week change after an arrow; all of it is skipped under reduced motion.
 */
@Composable
fun SportWeekStrip(
    state: SportSignUiState.Content,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .tabSwipeBlocked()
            .padding(bottom = ItmoTheme.spacing.compact)
            .testTag(SportWeekStripTestTags.STRIP),
    ) {
        MonthRow(state, onPreviousWeek, onNextWeek)
        WeekPager(state.calendarWeeks, state.selectedWeekIndex, onSelectDate)
    }
}

@Composable
private fun MonthRow(state: SportSignUiState.Content, onPreviousWeek: () -> Unit, onNextWeek: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ItmoTheme.spacing.screenMargin)
            .padding(top = ItmoTheme.spacing.content, bottom = ItmoTheme.spacing.related),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonthLabel(state.currentMonthName, Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.related)) {
            WeekArrow(
                painterResource(KitRes.drawable.ic_chevron_left),
                stringResource(Res.string.sport_previous_week),
                state.canGoToPrevWeek,
                onPreviousWeek,
                SportWeekStripTestTags.PREVIOUS,
            )
            WeekArrow(
                painterResource(KitRes.drawable.ic_chevron_right),
                stringResource(Res.string.sport_next_week),
                state.canGoToNextWeek,
                onNextWeek,
                SportWeekStripTestTags.NEXT,
            )
        }
    }
}

/** The month in `titleLarge` bold; a new month fades in from [MonthStartAlpha], never the first one. */
@Composable
private fun MonthLabel(month: String, modifier: Modifier) {
    val motion = ItmoTheme.motion
    val animate = !rememberReducedMotion()
    val alpha = remember { Animatable(1f) }
    var shown by remember { mutableStateOf(month) }
    LaunchedEffect(month) {
        val changed = shown.isNotEmpty() && shown != month
        shown = month
        if (changed && animate) {
            alpha.snapTo(MonthStartAlpha)
            alpha.animateTo(1f, motion.scheme.fastEffectsSpec())
        }
    }
    Text(
        month,
        modifier
            .graphicsLayer { this.alpha = alpha.value }
            .testTag(SportWeekStripTestTags.MONTH),
        color = ItmoTheme.colorScheme.onSurface,
        style = ItmoTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
    )
}

/**
 * A 48 dp arrow at half opacity while there is no week that way. Tinted `onSurface`: the View's
 * `colorOnPrimaryContainer` sat on the surface, not on a container, and all but vanished in both themes.
 */
@Composable
private fun WeekArrow(
    icon: Painter,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tag: String,
) {
    val tint = ItmoTheme.colorScheme.onSurface
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(ItmoTheme.spacing.touchTarget).testTag(tag),
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = tint,
            disabledContentColor = tint.copy(alpha = DisabledArrowAlpha),
        ),
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(ItmoTheme.spacing.touchTarget))
    }
}

/**
 * The weeks, one page each, scrolled only from [selectedWeekIndex]: animated after an arrow, at once on the first
 * composition and under reduced motion. The side margins give way to the touch targets: the screen margin stays
 * while seven 48 dp days fit beside it and shrinks to nothing on the way down to 336 dp ([WeekRow] covers less).
 */
@Composable
private fun WeekPager(weeks: List<List<CalendarDay>>, selectedWeekIndex: Int, onSelectDate: (LocalDate) -> Unit) {
    val motion = ItmoTheme.motion
    val animate = !rememberReducedMotion()
    val pagerState = rememberPagerState(initialPage = selectedWeekIndex) { weeks.size }
    LaunchedEffect(selectedWeekIndex, weeks.size) {
        if (weeks.isEmpty() || pagerState.currentPage == selectedWeekIndex) return@LaunchedEffect
        if (animate) {
            pagerState.animateScrollToPage(
                selectedWeekIndex,
                animationSpec = motion.scheme.defaultSpatialSpec(),
            )
        } else {
            pagerState.scrollToPage(selectedWeekIndex)
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val touchTarget = ItmoTheme.spacing.touchTarget
        val margin = ((maxWidth - touchTarget * DAYS_IN_WEEK) / 2).coerceIn(0.dp, ItmoTheme.spacing.screenMargin)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().testTag(SportWeekStripTestTags.PAGER),
            contentPadding = PaddingValues(horizontal = margin),
            pageSpacing = margin * 2,
            userScrollEnabled = false,
            key = { page -> weeks[page].firstOrNull()?.date?.toEpochDays() ?: page.toLong() },
        ) { page ->
            WeekRow { weeks[page].forEach { day -> DayCell(day, onSelectDate) } }
        }
    }
}

/**
 * The days side by side in equal slots, each at least a touch target wide: below seven touch targets the days keep
 * their full width and overlap by a dp or two instead of shrinking, spread so the outer ones stay inside the row.
 */
@Composable
private fun WeekRow(content: @Composable () -> Unit) {
    val minWidth = ItmoTheme.spacing.touchTarget
    Layout(content, Modifier.fillMaxWidth()) { measurables, constraints ->
        val width = constraints.maxWidth
        if (measurables.isEmpty()) return@Layout layout(width, 0) {}
        val cell = maxOf(width / measurables.size, minWidth.roundToPx()).coerceAtMost(width)
        val dayConstraints = constraints.copy(minWidth = cell, maxWidth = cell, minHeight = 0)
        val placeables = measurables.map { it.measure(dayConstraints) }
        val step = if (placeables.size > 1) (width - cell).toFloat() / (placeables.size - 1) else 0f
        layout(width, placeables.maxOf { it.height }) {
            placeables.forEachIndexed { index, day -> day.placeRelative((index * step).roundToInt(), 0) }
        }
    }
}

/**
 * `item_calendar_day`: the weekday over a 40 dp day card, coloured by selection, today and lessons; a day without
 * lessons at half opacity. TalkBack reads the full date and whether the day has (available) lessons.
 */
@Composable
private fun DayCell(day: CalendarDay, onSelectDate: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val colors = ItmoTheme.colorScheme
    val motion = ItmoTheme.motion
    val animate = !rememberReducedMotion()
    val (targetCard, targetText) = when {
        day.isSelected -> colors.primaryContainer to colors.onPrimaryContainer
        day.isToday -> colors.tertiaryContainer to colors.onTertiaryContainer
        day.hasAvailableLessons -> colors.secondaryContainer to colors.onSecondaryContainer
        else -> Color.Transparent to colors.onSurface
    }
    val colorSpec = if (animate) motion.scheme.fastEffectsSpec<Color>() else snap()
    val cardColor by animateContainerColorAsState(targetCard, colorSpec)
    val textColor by animateColorAsState(targetText, colorSpec)
    val scale = remember { Animatable(1f) }
    var wasSelected by remember { mutableStateOf(day.isSelected) }
    LaunchedEffect(day.isSelected) {
        val newlySelected = day.isSelected && !wasSelected
        wasSelected = day.isSelected
        if (newlySelected && animate) {
            scale.snapTo(SelectedStartScale)
            scale.animateTo(1f, motion.scheme.fastSpatialSpec())
        }
    }
    val quiet = !day.isSelected && !day.isToday && !day.hasAvailableLessons && !day.hasLessons
    val contentAlpha = if (quiet) EmptyDayAlpha else 1f
    val description = dayDescription(day)

    Column(
        modifier
            .selectable(
                selected = day.isSelected,
                interactionSource = null,
                indication = null,
                onClick = { onSelectDate(day.date) },
            )
            .semantics { contentDescription = description }
            .testTag(SportWeekStripTestTags.day(day.date))
            .padding(vertical = ItmoTheme.spacing.compact),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            day.dayOfWeek,
            Modifier.alpha(contentAlpha).clearAndSetSemantics {},
            // The weekday sits above the card on the surface, so it never takes the card's text colour.
            color = colors.onSurface,
            style = ItmoTheme.typography.bodySmall,
        )
        Box(
            Modifier
                .padding(top = ItmoTheme.spacing.related)
                .size(DayCardSize)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .background(cardColor, ItmoTheme.shapes.medium)
                .testTag(SportWeekStripTestTags.dayCard(day.date))
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            Text(
                day.dayOfMonth,
                Modifier.alpha(contentAlpha),
                color = textColor,
                style = ItmoTheme.typography.bodyLarge,
            )
        }
    }
}

/**
 * [target] animated with [spec], where a transparent [target] means "no container" rather than a colour to blend
 * with: [Color.Transparent] is transparent black, and half way to it a container turns grey. The colour shown fades
 * its own alpha out instead, and a new colour on an invisible card starts from itself at zero alpha, so every frame
 * is a tint of a real container colour.
 */
@Composable
private fun animateContainerColorAsState(target: Color, spec: AnimationSpec<Color>): State<Color> {
    val color = remember { Animatable(target) }
    val currentSpec by rememberUpdatedState(spec)
    LaunchedEffect(target) {
        when {
            target.alpha == 0f -> color.animateTo(color.value.copy(alpha = 0f), currentSpec)
            color.value.alpha == 0f -> {
                color.snapTo(target.copy(alpha = 0f))
                color.animateTo(target, currentSpec)
            }
            else -> color.animateTo(target, currentSpec)
        }
    }
    return color.asState()
}

@Composable
private fun dayDescription(day: CalendarDay): String {
    val date = day.date.format(DateTexts.LOCALIZED_FULL_DATE)
    val dated = if (day.isToday) stringResource(Res.string.sport_calendar_today, date) else date
    val lessons = when {
        day.hasAvailableLessons -> Res.string.sport_calendar_available
        day.hasLessons -> Res.string.sport_calendar_has_lessons
        else -> Res.string.sport_calendar_empty
    }
    return stringResource(lessons, dated)
}

private const val DAYS_IN_WEEK = 7
private const val MonthStartAlpha = 0.45f
private const val DisabledArrowAlpha = 0.5f
private const val EmptyDayAlpha = 0.5f
private const val SelectedStartScale = 0.82f
private val DayCardSize = 40.dp
