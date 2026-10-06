package dev.alllexey.itmowidgets.feature.home.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.home.HomeHint
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.designsystem.components.buttons.Pill
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButton
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButtonStyle
import dev.alllexey.itmowidgets.designsystem.components.rows.UserRow
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.home.presentation.HomeCardUi
import dev.alllexey.itmowidgets.feature.home.presentation.HomeRowTarget
import dev.alllexey.itmowidgets.feature.home.presentation.HomeScheduleRowUi
import dev.alllexey.itmowidgets.feature.home.presentation.HomeSportRowUi
import dev.alllexey.itmowidgets.shared.core.marks_new_title
import dev.alllexey.itmowidgets.shared.core.schedule_changes_title
import dev.alllexey.itmowidgets.shared.core.sport_score_remaining_status
import dev.alllexey.itmowidgets.shared.designsystem.ic_close
import dev.alllexey.itmowidgets.shared.designsystem.ic_edit_calendar
import dev.alllexey.itmowidgets.shared.designsystem.ic_exercise
import dev.alllexey.itmowidgets.shared.designsystem.ic_group
import dev.alllexey.itmowidgets.shared.designsystem.ic_menu_book
import dev.alllexey.itmowidgets.shared.designsystem.ic_notification
import dev.alllexey.itmowidgets.shared.designsystem.ic_schedule
import dev.alllexey.itmowidgets.shared.designsystem.ic_star_shine
import dev.alllexey.itmowidgets.shared.designsystem.ic_widgets
import dev.alllexey.itmowidgets.shared.feature.home.Res
import dev.alllexey.itmowidgets.shared.feature.home.home_friends_all
import dev.alllexey.itmowidgets.shared.feature.home.home_friends_title
import dev.alllexey.itmowidgets.shared.feature.home.home_hint_dismiss
import dev.alllexey.itmowidgets.shared.feature.home.home_hint_notifications_action
import dev.alllexey.itmowidgets.shared.feature.home.home_hint_notifications_description
import dev.alllexey.itmowidgets.shared.feature.home.home_hint_notifications_title
import dev.alllexey.itmowidgets.shared.feature.home.home_hint_services_action
import dev.alllexey.itmowidgets.shared.feature.home.home_hint_services_description
import dev.alllexey.itmowidgets.shared.feature.home.home_hint_services_title
import dev.alllexey.itmowidgets.shared.feature.home.home_hint_widgets_action
import dev.alllexey.itmowidgets.shared.feature.home.home_hint_widgets_description
import dev.alllexey.itmowidgets.shared.feature.home.home_hint_widgets_title
import dev.alllexey.itmowidgets.shared.feature.home.home_marks_description
import dev.alllexey.itmowidgets.shared.feature.home.home_marks_dismiss
import dev.alllexey.itmowidgets.shared.feature.home.home_schedule_changes_description
import dev.alllexey.itmowidgets.shared.feature.home.home_schedule_changes_dismiss
import dev.alllexey.itmowidgets.shared.feature.home.home_sport_more
import dev.alllexey.itmowidgets.shared.feature.home.home_sport_score
import dev.alllexey.itmowidgets.shared.feature.home.home_sport_title
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/** One card of the feed, in the feed's margins. */
@Composable
internal fun HomeCard(card: HomeCardUi, actions: HomeActions, modifier: Modifier = Modifier) {
    when (card) {
        is HomeCardUi.Schedule -> ScheduleCard(card, actions, modifier)
        is HomeCardUi.ScheduleChanges -> {
            val latest = card.latest.asString()
            ClosableCard(
                icon = KitRes.drawable.ic_edit_calendar,
                title = stringResource(CoreRes.string.schedule_changes_title),
                count = card.unread,
                body = latest,
                description = Res.string.home_schedule_changes_description,
                dismissLabel = stringResource(Res.string.home_schedule_changes_dismiss),
                onOpen = actions.onOpenScheduleChanges,
                onDismiss = actions.onDismissScheduleChanges,
                modifier = modifier,
            )
        }
        is HomeCardUi.Marks -> ClosableCard(
            icon = KitRes.drawable.ic_menu_book,
            title = stringResource(CoreRes.string.marks_new_title),
            count = card.count,
            body = card.subjects.asString(),
            description = Res.string.home_marks_description,
            dismissLabel = stringResource(Res.string.home_marks_dismiss),
            onOpen = actions.onOpenMarks,
            onDismiss = actions.onDismissMarks,
            modifier = modifier,
        )
        is HomeCardUi.Sport -> SportCard(card, actions, modifier)
        is HomeCardUi.FriendRequests -> FriendRequestsCard(card, actions, modifier)
        is HomeCardUi.Hint -> HintCard(card.hint, actions, modifier)
    }
}

/**
 * A `Card.Content` of the feed: `surfaceContainerLow` in the content corners, 16 dp from the screen's sides and
 * 4 dp from its neighbours; [outlined] adds the hint cards' 1 dp `outlineVariant` stroke.
 */
@Composable
private fun FeedCard(
    modifier: Modifier,
    onClick: (() -> Unit)? = null,
    outlined: Boolean = false,
    content: @Composable () -> Unit,
) {
    val outer = modifier
        .fillMaxWidth()
        .padding(horizontal = ItmoTheme.spacing.screenMargin, vertical = ItmoTheme.spacing.related)
    val shape = ItmoTheme.shapes.cardContent
    val color = ItmoTheme.colorScheme.surfaceContainerLow
    val border = if (outlined) BorderStroke(CardStroke, ItmoTheme.colorScheme.outlineVariant) else null
    if (onClick == null) {
        Surface(outer, shape = shape, color = color, border = border, content = content)
    } else {
        Surface(onClick, outer, shape = shape, color = color, border = border, content = content)
    }
}

@Composable
private fun CardHeader(icon: DrawableResource, title: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(HeaderIconSize),
            tint = ItmoTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(ItmoTheme.spacing.compact))
        Text(
            title,
            Modifier.weight(1f),
            color = ItmoTheme.colorScheme.onSurface,
            style = ItmoTheme.typography.titleMedium,
        )
        trailing()
    }
}

/**
 * A row's label (`сейчас`, `ждём запись`): the kit's neutral [Pill] in `labelSmall`, and `primary` for the lesson in
 * progress ([focused]), which the kit has no variant for.
 */
@Composable
private fun RowBadge(text: String, modifier: Modifier = Modifier, focused: Boolean = false) {
    val colors = ItmoTheme.colorScheme
    Text(
        text,
        modifier
            .clip(BadgeShape)
            .background(if (focused) colors.primary else colors.secondaryContainer)
            .padding(horizontal = ItmoTheme.spacing.compact, vertical = BadgePaddingVertical),
        color = if (focused) colors.onPrimary else colors.onSecondaryContainer,
        style = ItmoTheme.typography.labelSmall,
        maxLines = 1,
    )
}

@Composable
private fun CloseButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick, modifier.size(ItmoTheme.spacing.touchTarget).testTag(HomeTestTags.DISMISS)) {
        Icon(painterResource(KitRes.drawable.ic_close), label, tint = ItmoTheme.colorScheme.onSurfaceVariant)
    }
}

// region Schedule

@Composable
private fun ScheduleCard(card: HomeCardUi.Schedule, actions: HomeActions, modifier: Modifier) {
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

/** The lesson in progress is told by its time, its badge and how far it has run, not by a fill. */
@Composable
private fun ScheduleRow(row: HomeScheduleRowUi, actions: HomeActions) {
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
            .testTag(HomeTestTags.SCHEDULE_ROW)
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
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().padding(top = RowPaddingVertical).height(RowProgressHeight),
                )
            }
        }
        row.badge?.let { RowBadge(it.asString(), Modifier.padding(start = ItmoTheme.spacing.compact), focused = focused) }
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

// endregion

/**
 * Schedule changes and new marks: the whole card opens, the 48 dp close button marks everything read, and the body
 * wraps without truncation. TalkBack reads `<title>, <count>. <body>` from [description].
 */
@Composable
private fun ClosableCard(
    icon: DrawableResource,
    title: String,
    count: Int,
    body: String,
    description: StringResource,
    dismissLabel: String,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier,
) {
    val spoken = stringResource(description, title, count, body)
    FeedCard(modifier.semantics { contentDescription = spoken }, onClick = onOpen) {
        Column(
            Modifier.padding(
                start = ItmoTheme.spacing.cardPadding,
                top = ItmoTheme.spacing.related,
                end = ItmoTheme.spacing.related,
                bottom = ItmoTheme.spacing.cardPadding,
            ),
        ) {
            CardHeader(icon, title, Modifier.clearAndSetSemantics {}) {
                Pill(count.toString(), Modifier.padding(start = ItmoTheme.spacing.compact))
                Spacer(Modifier.size(ItmoTheme.spacing.touchTarget))
            }
            Text(
                body,
                Modifier.padding(end = ItmoTheme.spacing.content).clearAndSetSemantics {},
                color = ItmoTheme.colorScheme.onSurface,
                style = ItmoTheme.typography.bodyMedium,
            )
        }
        // Over the header's placeholder, outside its cleared semantics, so TalkBack still finds it.
        Box(Modifier.fillMaxWidth().padding(top = ItmoTheme.spacing.related, end = ItmoTheme.spacing.related)) {
            CloseButton(dismissLabel, onDismiss, Modifier.align(Alignment.TopEnd))
        }
    }
}

// region Sport

@Composable
private fun SportCard(card: HomeCardUi.Sport, actions: HomeActions, modifier: Modifier) {
    FeedCard(modifier, onClick = actions.onOpenSport) {
        Column(Modifier.padding(horizontal = ItmoTheme.spacing.compact, vertical = ItmoTheme.spacing.cardPadding)) {
            val score = card.score
            CardHeader(
                KitRes.drawable.ic_exercise,
                stringResource(Res.string.home_sport_title),
                Modifier.padding(horizontal = ItmoTheme.spacing.compact),
            ) {
                if (score != null) {
                    Text(
                        stringResource(Res.string.home_sport_score, score.total),
                        color = ItmoTheme.colorScheme.primary,
                        style = ItmoTheme.typography.labelLarge,
                    )
                }
            }
            if (score != null) {
                LinearProgressIndicator(
                    progress = { score.total / SCORE_MAX },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ItmoTheme.spacing.compact)
                        .padding(top = ItmoTheme.spacing.compact)
                        .height(ScoreProgressHeight),
                )
                Text(
                    pluralStringResource(CoreRes.plurals.sport_score_remaining_status, score.remaining, score.remaining),
                    Modifier.padding(top = ItmoTheme.spacing.related).padding(horizontal = ItmoTheme.spacing.compact),
                    color = ItmoTheme.colorScheme.onSurfaceVariant,
                    style = ItmoTheme.typography.bodySmall,
                )
            }
            if (card.queue.isNotEmpty()) {
                Column(Modifier.padding(top = ItmoTheme.spacing.compact)) {
                    card.queue.forEach { row -> SportRow(row, actions) }
                }
            }
            if (card.more > 0) {
                Text(
                    pluralStringResource(Res.plurals.home_sport_more, card.more, card.more),
                    Modifier.padding(top = ItmoTheme.spacing.related).padding(horizontal = ItmoTheme.spacing.compact),
                    color = ItmoTheme.colorScheme.onSurfaceVariant,
                    style = ItmoTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun SportRow(row: HomeSportRowUi, actions: HomeActions) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RowShape)
            .clickable { actions.onPendingSport(row.args) }
            .testTag(HomeTestTags.SPORT_ROW)
            .heightIn(min = ItmoTheme.spacing.touchTarget)
            .padding(horizontal = ItmoTheme.spacing.compact, vertical = RowPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                row.title,
                color = ItmoTheme.colorScheme.onSurface,
                style = ItmoTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                row.subtitle,
                color = ItmoTheme.colorScheme.onSurfaceVariant,
                style = ItmoTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        RowBadge(row.badge.asString(), Modifier.padding(start = ItmoTheme.spacing.compact))
    }
}

// endregion

@Composable
private fun FriendRequestsCard(card: HomeCardUi.FriendRequests, actions: HomeActions, modifier: Modifier) {
    FeedCard(modifier) {
        Column(Modifier.padding(top = ItmoTheme.spacing.cardPadding, bottom = ItmoTheme.spacing.compact)) {
            CardHeader(
                KitRes.drawable.ic_group,
                stringResource(Res.string.home_friends_title),
                Modifier.padding(horizontal = ItmoTheme.spacing.cardPadding),
            ) {
                Pill(card.count.toString())
            }
            Column(Modifier.padding(top = ItmoTheme.spacing.compact)) {
                card.incoming.forEach { user ->
                    UserRow(
                        name = user.name,
                        pictureUrl = user.pictureUrl,
                        modifier = Modifier.testTag(HomeTestTags.FRIEND_ROW),
                        subtitle = user.group,
                        onClick = { actions.onOpenUser(user.isu) },
                    )
                }
            }
            ProgressButton(
                label = stringResource(Res.string.home_friends_all),
                onClick = actions.onOpenFriends,
                modifier = Modifier.padding(start = ItmoTheme.spacing.compact).testTag(HomeTestTags.FRIENDS_ALL),
                style = ProgressButtonStyle.Text,
            )
        }
    }
}

@Composable
private fun HintCard(hint: HomeHint, actions: HomeActions, modifier: Modifier) {
    val content = hintContent(hint)
    FeedCard(modifier, outlined = true) {
        Row(
            Modifier.padding(
                start = ItmoTheme.spacing.cardPadding,
                top = ItmoTheme.spacing.cardPadding,
                end = ItmoTheme.spacing.related,
                bottom = ItmoTheme.spacing.cardPadding,
            ),
        ) {
            Icon(
                content.icon,
                contentDescription = null,
                modifier = Modifier.size(HintIconSize),
                tint = ItmoTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f).padding(start = ItmoTheme.spacing.content)) {
                Text(
                    stringResource(content.title),
                    color = ItmoTheme.colorScheme.onSurface,
                    style = ItmoTheme.typography.titleMedium,
                )
                Text(
                    stringResource(content.text),
                    Modifier.padding(top = ItmoTheme.spacing.related),
                    color = ItmoTheme.colorScheme.onSurfaceVariant,
                    style = ItmoTheme.typography.bodySmall,
                )
                ProgressButton(
                    label = stringResource(content.action),
                    onClick = { actions.onHint(hint) },
                    modifier = Modifier.padding(top = ItmoTheme.spacing.compact).testTag(HomeTestTags.HINT_ACTION),
                    style = ProgressButtonStyle.Tonal,
                )
            }
            CloseButton(
                stringResource(Res.string.home_hint_dismiss),
                { actions.onDismissHint(hint) },
                Modifier.offset(y = -ItmoTheme.spacing.compact),
            )
        }
    }
}

private class HintContent(val icon: Painter, val title: StringResource, val text: StringResource, val action: StringResource)

@Composable
private fun hintContent(hint: HomeHint): HintContent = when (hint) {
    HomeHint.WIDGETS -> HintContent(
        painterResource(KitRes.drawable.ic_widgets),
        Res.string.home_hint_widgets_title,
        Res.string.home_hint_widgets_description,
        Res.string.home_hint_widgets_action,
    )
    HomeHint.NOTIFICATIONS -> HintContent(
        painterResource(KitRes.drawable.ic_notification),
        Res.string.home_hint_notifications_title,
        Res.string.home_hint_notifications_description,
        Res.string.home_hint_notifications_action,
    )
    HomeHint.SERVICES -> HintContent(
        painterResource(KitRes.drawable.ic_star_shine),
        Res.string.home_hint_services_title,
        Res.string.home_hint_services_description,
        Res.string.home_hint_services_action,
    )
}

private const val SCORE_MAX = 100f
private val HeaderIconSize = 20.dp
private val HintIconSize = 24.dp
private val CardStroke = 1.dp
private val BadgeShape = RoundedCornerShape(8.dp)
private val BadgePaddingVertical = 2.dp
private val RowShape = RoundedCornerShape(12.dp)
private val RowPaddingVertical = 6.dp
private val TimeColumnMinWidth = 48.dp
private val TypeBarWidth = 4.dp
private val TypeBarShape = RoundedCornerShape(2.dp)
private val RowProgressHeight = 3.dp
private val ScoreProgressHeight = 8.dp
