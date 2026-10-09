package dev.alllexey.itmowidgets.feature.schedule.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.home.HomeCardActions
import dev.alllexey.itmowidgets.core.home.HomeCardKind
import dev.alllexey.itmowidgets.core.home.HomeCardRenderer
import dev.alllexey.itmowidgets.core.home.HomeCardTestTags
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.designsystem.components.cards.ClosableFeedCard
import dev.alllexey.itmowidgets.designsystem.components.cards.FeedCard
import dev.alllexey.itmowidgets.designsystem.components.cards.FeedRowBadge
import dev.alllexey.itmowidgets.designsystem.components.expressive.ItmoWavyProgress
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.shared.core.schedule_changes_title
import dev.alllexey.itmowidgets.shared.designsystem.ic_edit_calendar
import dev.alllexey.itmowidgets.shared.designsystem.ic_schedule
import dev.alllexey.itmowidgets.shared.feature.schedule.Res
import dev.alllexey.itmowidgets.shared.feature.schedule.home_schedule_changes_description
import dev.alllexey.itmowidgets.shared.feature.schedule.home_schedule_changes_dismiss
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/** The schedule's home cards: today's or tomorrow's lessons and the unread schedule changes, in [timeZone]. */
class ScheduleHomeCardRenderer(timeZone: TimeZone) : HomeCardRenderer {

    private val formatter = ScheduleHomeCardFormatter(timeZone)

    override val kinds: Set<HomeCardKind> = setOf(HomeCardKind.SCHEDULE, HomeCardKind.SCHEDULE_CHANGES)

    @Composable
    override fun Content(card: HomeCard, actions: HomeCardActions, modifier: Modifier) {
        when (card) {
            is HomeCard.Schedule -> ScheduleCard(remember(card) { formatter.schedule(card) }, actions, modifier)
            is HomeCard.ScheduleChanges ->
                ScheduleChangesCard(remember(card) { formatter.changes(card) }, actions, modifier)
            else -> error("${card.kind} is not a schedule card")
        }
    }
}

@Composable
private fun ScheduleCard(card: HomeScheduleCardUi, actions: HomeCardActions, modifier: Modifier) {
    FeedCard(modifier) {
        Column(Modifier.padding(horizontal = ItmoTheme.spacing.compact, vertical = ItmoTheme.spacing.cardPadding)) {
            Row(
                Modifier.padding(horizontal = ItmoTheme.spacing.compact),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(KitRes.drawable.ic_schedule),
                    contentDescription = null,
                    modifier = Modifier.size(HeaderIconSize),
                    tint = ItmoTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(ItmoTheme.spacing.compact))
                Text(card.title.asString(), color = ItmoTheme.colorScheme.onSurface, style = ItmoTheme.typography.titleMedium)
                Text(
                    card.date,
                    Modifier.weight(1f).padding(start = ItmoTheme.spacing.compact),
                    color = ItmoTheme.colorScheme.onSurfaceVariant,
                    style = ItmoTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (card.rows.isNotEmpty()) {
                Column(Modifier.padding(top = ItmoTheme.spacing.compact)) {
                    card.rows.forEach { row -> ScheduleRow(row, actions) }
                }
            }
            card.footer?.let { footer ->
                Text(
                    footer.asString(),
                    Modifier.fillMaxWidth().padding(top = ItmoTheme.spacing.compact).padding(horizontal = ItmoTheme.spacing.compact),
                    color = ItmoTheme.colorScheme.onSurfaceVariant,
                    style = ItmoTheme.typography.bodySmall,
                )
            }
        }
    }
}

/**
 * The lesson in progress is told by its time, its badge and how far it has run, not by a fill. It is the home flow's
 * hero next to the QR pass (design.md, Expressive components), so its progress is [ItmoWavyProgress]; every other row
 * stays plain.
 */
@Composable
private fun ScheduleRow(row: HomeScheduleRowUi, actions: HomeCardActions) {
    val focused = row.progress != null
    val open = when (val target = row.target) {
        is HomeRowTarget.Lesson -> { { actions.onLesson(target.args) } }
        is HomeRowTarget.PendingSport -> { { actions.onPendingSport(target.args) } }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RowShape)
            .clickable(onClick = open)
            .testTag(HomeCardTestTags.SCHEDULE_ROW)
            .heightIn(min = ItmoTheme.spacing.touchTarget)
            .height(IntrinsicSize.Min)
            .padding(horizontal = ItmoTheme.spacing.compact, vertical = RowPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.widthIn(min = TimeColumnMinWidth)) {
            Text(
                row.start,
                color = if (focused) ItmoTheme.colorScheme.primary else ItmoTheme.colorScheme.onSurface,
                style = ItmoTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(row.end, color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.labelSmall)
        }
        Box(
            Modifier
                .padding(horizontal = ItmoTheme.spacing.compact)
                .width(TypeBarWidth)
                .fillMaxHeight()
                .clip(TypeBarShape)
                .background(lessonTypeColor(row.typeId)),
        )
        Column(Modifier.weight(1f)) {
            Text(
                row.title,
                color = ItmoTheme.colorScheme.onSurface,
                style = ItmoTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                row.subtitle.asString(),
                color = ItmoTheme.colorScheme.onSurfaceVariant,
                style = ItmoTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            row.progress?.let { progress ->
                ItmoWavyProgress(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().padding(top = RowPaddingVertical),
                )
            }
        }
        row.badge?.let { FeedRowBadge(it.asString(), Modifier.padding(start = ItmoTheme.spacing.compact), focused = focused) }
    }
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

/**
 * The unread schedule changes: the whole card opens the changes screen, the close button marks them read. TalkBack
 * reads `Изменения в расписании, <count>. <latest>`.
 */
@Composable
private fun ScheduleChangesCard(card: HomeScheduleChangesCardUi, actions: HomeCardActions, modifier: Modifier) {
    val title = stringResource(CoreRes.string.schedule_changes_title)
    val latest = card.latest.asString()
    ClosableFeedCard(
        icon = painterResource(KitRes.drawable.ic_edit_calendar),
        title = title,
        count = card.unread,
        body = latest,
        description = stringResource(Res.string.home_schedule_changes_description, title, card.unread, latest),
        dismissLabel = stringResource(Res.string.home_schedule_changes_dismiss),
        onOpen = actions.onOpenScheduleChanges,
        onDismiss = { actions.onDismiss(HomeCardKind.SCHEDULE_CHANGES) },
        modifier = modifier,
        closeModifier = Modifier.testTag(HomeCardTestTags.DISMISS),
    )
}

private val HeaderIconSize = 20.dp
private val RowShape = RoundedCornerShape(12.dp)
private val RowPaddingVertical = 6.dp
private val TimeColumnMinWidth = 48.dp
private val TypeBarWidth = 4.dp
private val TypeBarShape = RoundedCornerShape(2.dp)
