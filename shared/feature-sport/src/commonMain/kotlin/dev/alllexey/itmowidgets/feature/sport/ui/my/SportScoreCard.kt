package dev.alllexey.itmowidgets.feature.sport.ui.my

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.components.charts.ScoreRing
import dev.alllexey.itmowidgets.designsystem.components.charts.ScoreRingSector
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.tokens.rememberReducedMotion
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportScore
import dev.alllexey.itmowidgets.shared.feature.sport.Res
import dev.alllexey.itmowidgets.shared.feature.sport.sport_score_attendance
import dev.alllexey.itmowidgets.shared.feature.sport.sport_score_bonus
import dev.alllexey.itmowidgets.shared.feature.sport.sport_score_bonus_over_limit
import dev.alllexey.itmowidgets.shared.feature.sport.sport_score_bonus_value
import dev.alllexey.itmowidgets.shared.feature.sport.sport_score_passed_status
import dev.alllexey.itmowidgets.shared.feature.sport.sport_score_title
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.core.sport_score_goal
import dev.alllexey.itmowidgets.shared.core.sport_score_remaining_status
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes
import dev.alllexey.itmowidgets.shared.designsystem.ic_check
import dev.alllexey.itmowidgets.shared.designsystem.ic_history

/**
 * The `Мой спорт` score card: the title and the status chip, then the ring with the total and the attendance and
 * bonus lines. The total is attendance plus at most 40 bonus points, a UI formula rather than an official rule.
 *
 * With [animated], the counters count up from their previous values (from zero at first) over
 * `ItmoTheme.motion.progressMillis`, the ring fills alike, and the status chip scales in after a short delay whenever
 * it first shows or flips; under reduced motion, and without [animated], everything shows its end state at once.
 *
 * With a [collapse] state the card shrinks into a compact bar of the title and the status as the list under it
 * scrolls (see [SportScoreCollapsingLayout]); its measured size stays the expanded one and only the clip, the
 * translations and the colours follow the scroll.
 */
@Composable
fun SportScoreCard(
    score: SportScore,
    modifier: Modifier = Modifier,
    collapse: SportScoreCollapseState? = null,
    animated: Boolean = true,
) {
    val animate = animated && !rememberReducedMotion()
    val colors = ItmoTheme.colorScheme
    val resting = colors.surfaceContainerLow
    val raised = colors.surfaceContainerHigh
    val cardShape = ItmoTheme.shapes.cardSummary
    val summaryPadding = ItmoTheme.spacing.summaryPadding
    val barPadding = ItmoTheme.spacing.content
    Box(
        modifier
            .fillMaxWidth()
            .testTag(SportScoreCardTestTags.CARD)
            .graphicsLayer {
                shape = TopPartShape(cardShape, collapse?.visibleCardHeight(size.height) ?: size.height)
                clip = true
            }
            // List rows share the card's surface, so the bar takes its own step to stay readable over them.
            .drawBehind { drawRect(lerp(resting, raised, collapse?.fraction ?: 0f)) },
    ) {
        Column(
            Modifier
                .padding(summaryPadding)
                .graphicsLayer { translationY = collapse?.contentShift() ?: 0f },
        ) {
            ScoreHeader(score, animate)
            Box(
                Modifier
                    .testTag(SportScoreCardTestTags.DETAILS)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        collapse?.apply {
                            detailsHeight = placeable.height
                            paddingSpan = ((summaryPadding - barPadding) * 2).roundToPx()
                        }
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    }
                    .graphicsLayer {
                        shape = TopPartShape(RectangleShape, collapse?.visibleDetailsHeight() ?: size.height)
                        clip = true
                    },
            ) {
                ScoreDetails(
                    score,
                    animate,
                    Modifier.graphicsLayer {
                        translationY = -size.height * (collapse?.fraction ?: 0f)
                        alpha = collapse?.detailsAlpha() ?: 1f
                    },
                )
            }
        }
    }
}

/** Tags for host tests and the screen's instrumented flow. */
object SportScoreCardTestTags {
    const val CARD = "sport_score_card"
    const val HEADER = "sport_score_header"
    const val STATUS = "sport_score_status"
    const val DETAILS = "sport_score_details"
    const val TOTAL = "sport_score_total"
    const val ATTENDANCE = "sport_score_attendance"
    const val BONUS = "sport_score_bonus"
    const val BACKDROP = "sport_score_backdrop"
}

/**
 * The title and the status chip in one row, the chip at the end. When the chip leaves the title too little room for
 * its longest word (320 dp at font 1.3 with `Ещё 32 балла`), the chip moves under the title instead of the title
 * breaking inside a word.
 */
@Composable
private fun ScoreHeader(score: SportScore, animate: Boolean) {
    val gap = ItmoTheme.spacing.content
    val stackedGap = ItmoTheme.spacing.compact
    Layout(
        content = {
            Text(
                stringResource(Res.string.sport_score_title),
                style = ItmoTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = ItmoTheme.colorScheme.onSurface,
            )
            StatusChip(score, animate)
        },
        modifier = Modifier.fillMaxWidth().testTag(SportScoreCardTestTags.HEADER),
    ) { measurables, constraints ->
        val (titleMeasurable, chipMeasurable) = measurables
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val width = constraints.maxWidth
        val chip = chipMeasurable.measure(loose)
        val titleWidth = (width - chip.width - gap.roundToPx()).coerceAtLeast(0)
        if (titleMeasurable.minIntrinsicWidth(Constraints.Infinity) <= titleWidth) {
            val title = titleMeasurable.measure(Constraints.fixedWidth(titleWidth).copy(minHeight = 0))
            val height = maxOf(title.height, chip.height)
            layout(width, height) {
                title.place(0, (height - title.height) / 2)
                chip.place(width - chip.width, (height - chip.height) / 2)
            }
        } else {
            val title = titleMeasurable.measure(loose)
            val chipTop = title.height + stackedGap.roundToPx()
            layout(width, chipTop + chip.height) {
                title.place(0, 0)
                chip.place(0, chipTop)
            }
        }
    }
}

@Composable
private fun StatusChip(score: SportScore, animate: Boolean) {
    val passed = score.passed
    val motion = ItmoTheme.motion
    val appearance = remember { Animatable(if (animate) 0f else 1f) }
    // Keyed on the status itself: the chip comes in when it first shows and when it flips, not on every new score.
    LaunchedEffect(passed, animate) {
        if (!animate) {
            appearance.snapTo(1f)
            return@LaunchedEffect
        }
        appearance.snapTo(0f)
        delay(STATUS_DELAY_MILLIS)
        appearance.animateTo(1f, tween(motion.emphasisMillis, easing = motion.easing))
    }
    val colors = ItmoTheme.colorScheme
    val container = if (passed) colors.primaryContainer else colors.secondaryContainer
    val content = if (passed) colors.onPrimaryContainer else colors.onSecondaryContainer
    Row(
        Modifier
            .testTag(SportScoreCardTestTags.STATUS)
            .graphicsLayer {
                val progress = appearance.value
                alpha = progress
                scaleX = STATUS_START_SCALE + (1f - STATUS_START_SCALE) * progress
                scaleY = scaleX
            }
            .background(container, StatusShape)
            .padding(horizontal = ItmoTheme.spacing.content, vertical = StatusVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StatusIconGap),
    ) {
        Icon(
            painterResource(if (passed) KitRes.drawable.ic_check else KitRes.drawable.ic_history),
            contentDescription = null,
            modifier = Modifier.size(StatusIconSize),
            tint = content,
        )
        Text(
            if (passed) {
                stringResource(Res.string.sport_score_passed_status)
            } else {
                pluralStringResource(CoreRes.plurals.sport_score_remaining_status, score.need, score.need)
            },
            style = ItmoTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = content,
        )
    }
}

@Composable
private fun ScoreDetails(score: SportScore, animate: Boolean, modifier: Modifier) {
    val counts = rememberAnimatedCounts(score.counts(), animate)
    val shown = counts.shown
    val extended = ItmoTheme.extendedColors
    val shares = score.ringShares()
    Row(
        modifier.fillMaxWidth().padding(top = ItmoTheme.spacing.group),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.summaryPadding),
    ) {
        ScoreRing(
            sectors = if (shares == null) {
                emptyList()
            } else {
                listOf(
                    ScoreRingSector(extended.sportScoreAttendance, shares.attendance),
                    ScoreRingSector(extended.sportScoreBonus, shares.bonus),
                )
            },
            modifier = Modifier.size(RingSize),
            animated = animate,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    shown.total.toString(),
                    Modifier.testTag(SportScoreCardTestTags.TOTAL),
                    style = ItmoTheme.emphasizedTypography.headlineLarge.copy(fontFeatureSettings = TABULAR_FIGURES),
                    color = ItmoTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(CoreRes.string.sport_score_goal),
                    Modifier.layout { measurable, constraints ->
                        // `layout_marginTop="-2dp"` of `view_sport_circle.xml`.
                        val placeable = measurable.measure(constraints)
                        val pull = GoalPull.roundToPx()
                        layout(placeable.width, placeable.height - pull) { placeable.place(0, -pull) }
                    },
                    style = ItmoTheme.typography.labelMedium,
                    color = ItmoTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact)) {
            ScoreLine(
                color = extended.sportScoreAttendance,
                value = shown.attendances.toString(),
                label = stringResource(Res.string.sport_score_attendance),
                tag = SportScoreCardTestTags.ATTENDANCE,
            )
            ScoreLine(
                color = extended.sportScoreBonus,
                value = if (score.showsBonusOverLimit(counts.settled)) {
                    stringResource(Res.string.sport_score_bonus_over_limit, score.otherCapped, score.other)
                } else {
                    stringResource(Res.string.sport_score_bonus_value, shown.bonus)
                },
                label = stringResource(Res.string.sport_score_bonus),
                tag = SportScoreCardTestTags.BONUS,
            )
        }
    }
}

/**
 * A dot, the number and its label in one row, as in the View. When the label does not fit beside the number (320 dp
 * at font 1.3), it moves under the line, from the dot, instead of breaking inside a word.
 */
@Composable
private fun ScoreLine(color: Color, value: String, label: String, tag: String) {
    val gap = ItmoTheme.spacing.compact
    Layout(
        content = {
            Box(Modifier.size(IndicatorSize).background(color, CircleShape))
            Text(
                value,
                Modifier.testTag(tag),
                style = ItmoTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = ItmoTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(label, style = ItmoTheme.typography.bodyMedium, color = ItmoTheme.colorScheme.onSurfaceVariant)
        },
    ) { measurables, constraints ->
        val (dotMeasurable, valueMeasurable, labelMeasurable) = measurables
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val maxWidth = if (loose.hasBoundedWidth) loose.maxWidth else Constraints.Infinity
        val gapPx = gap.roundToPx()
        val dot = dotMeasurable.measure(loose)
        val valueX = dot.width + gapPx
        val number = valueMeasurable.measure(loose.copy(maxWidth = (maxWidth - valueX).coerceAtLeast(0)))
        val labelX = valueX + number.width + gapPx
        val inline = !loose.hasBoundedWidth ||
            labelMeasurable.maxIntrinsicWidth(Constraints.Infinity) <= maxWidth - labelX
        if (inline) {
            val text = labelMeasurable.measure(loose.copy(maxWidth = (maxWidth - labelX).coerceAtLeast(0)))
            val height = maxOf(dot.height, number.height, text.height)
            layout(labelX + text.width, height) {
                dot.place(0, (height - dot.height) / 2)
                number.place(valueX, (height - number.height) / 2)
                text.place(labelX, (height - text.height) / 2)
            }
        } else {
            // The full width under the dot, so a long word such as `посещение` keeps one line.
            val text = labelMeasurable.measure(loose)
            val row = maxOf(dot.height, number.height)
            layout(maxOf(valueX + number.width, text.width), row + text.height) {
                dot.place(0, (row - dot.height) / 2)
                number.place(valueX, (row - number.height) / 2)
                text.place(0, row)
            }
        }
    }
}

private class AnimatedCounts(val shown: SportScoreCounts, val settled: Boolean)

/**
 * The counters on screen. A new target starts from what is shown now, so a refresh mid-count does not jump; the first
 * target starts from zero.
 */
@Composable
private fun rememberAnimatedCounts(target: SportScoreCounts, animate: Boolean): AnimatedCounts {
    val motion = ItmoTheme.motion
    var from by remember { mutableStateOf(if (animate) SportScoreCounts.Zero else target) }
    var to by remember { mutableStateOf(target) }
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(target, animate) {
        if (!animate) {
            from = target
            to = target
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        from = from.towards(to, progress.value)
        to = target
        progress.snapTo(0f)
        progress.animateTo(1f, tween(motion.progressMillis, easing = motion.easing))
    }
    val fraction = progress.value
    return AnimatedCounts(from.towards(to, fraction), settled = fraction >= 1f && to == target)
}

/** `view_sport_circle.xml`'s 112 dp ring. */
private val RingSize = 112.dp

/** `indicator_circle` at 12 dp. */
private val IndicatorSize = 12.dp

private val GoalPull: Dp = 2.dp

/** `score_status_card`: 14 dp corners, 7 dp vertical padding, an 18 dp icon 7 dp from the text. */
private val StatusShape = RoundedCornerShape(14.dp)
private val StatusVerticalPadding = 7.dp
private val StatusIconSize = 18.dp
private val StatusIconGap = 7.dp

/** The chip waits for the counters to get going before it scales in (`SportMyFragment.STATUS_ANIMATION_DELAY`). */
private const val STATUS_DELAY_MILLIS = 380L
private const val STATUS_START_SCALE = 0.84f

private const val TABULAR_FIGURES = "tnum"
