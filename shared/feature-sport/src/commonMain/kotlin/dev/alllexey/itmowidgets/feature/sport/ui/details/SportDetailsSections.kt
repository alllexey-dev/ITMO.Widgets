package dev.alllexey.itmowidgets.feature.sport.ui.details

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.designsystem.components.avatar.Avatar
import dev.alllexey.itmowidgets.designsystem.components.charts.ScoreRing
import dev.alllexey.itmowidgets.designsystem.components.charts.ScoreRingSector
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportDetailsCondition
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportFriendRegistration
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportFriendStatus
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportOccupancy
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportQueueDetails
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportQueueFact
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportQueueFactKind
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportRegistrationDetails
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportRegistrationStatus
import dev.alllexey.itmowidgets.feature.sport.ui.common.SportConditionTone
import dev.alllexey.itmowidgets.feature.sport.ui.common.accent
import dev.alllexey.itmowidgets.feature.sport.ui.common.conditionTone
import dev.alllexey.itmowidgets.feature.sport.ui.common.labelResource
import dev.alllexey.itmowidgets.feature.sport.ui.common.tone
import dev.alllexey.itmowidgets.feature.sport.ui.common.titleResource
import dev.alllexey.itmowidgets.shared.feature.sport.Res
import dev.alllexey.itmowidgets.shared.feature.sport.sport_booking_allowed
import dev.alllexey.itmowidgets.shared.feature.sport.sport_booking_auto_checks
import dev.alllexey.itmowidgets.shared.feature.sport.sport_booking_future_checks
import dev.alllexey.itmowidgets.shared.feature.sport.sport_booking_late_auto
import dev.alllexey.itmowidgets.shared.feature.sport.sport_booking_late_auto_hint
import dev.alllexey.itmowidgets.shared.feature.sport.sport_booking_no_bypass
import dev.alllexey.itmowidgets.shared.feature.sport.sport_booking_no_bypass_hint
import dev.alllexey.itmowidgets.shared.feature.sport.sport_booking_prediction_rules
import dev.alllexey.itmowidgets.shared.feature.sport.sport_booking_uncertain
import dev.alllexey.itmowidgets.shared.feature.sport.sport_booking_wait
import dev.alllexey.itmowidgets.shared.feature.sport.sport_booking_wait_place
import dev.alllexey.itmowidgets.shared.feature.sport.sport_booking_warning
import dev.alllexey.itmowidgets.shared.feature.sport.sport_booking_warning_hint
import dev.alllexey.itmowidgets.shared.feature.sport.sport_capacity_description
import dev.alllexey.itmowidgets.shared.feature.sport.sport_capacity_free_label
import dev.alllexey.itmowidgets.shared.feature.sport.sport_capacity_none
import dev.alllexey.itmowidgets.shared.feature.sport.sport_capacity_occupied
import dev.alllexey.itmowidgets.shared.feature.sport.sport_capacity_occupied_label
import dev.alllexey.itmowidgets.shared.feature.sport.sport_card_queue
import dev.alllexey.itmowidgets.shared.feature.sport.sport_details_comment_title
import dev.alllexey.itmowidgets.shared.feature.sport.sport_friend_not_signed
import dev.alllexey.itmowidgets.shared.feature.sport.sport_friend_signed
import dev.alllexey.itmowidgets.shared.feature.sport.sport_friends_count_label
import dev.alllexey.itmowidgets.shared.feature.sport.sport_prediction_matching
import dev.alllexey.itmowidgets.shared.feature.sport.sport_queue_attempts
import dev.alllexey.itmowidgets.shared.feature.sport.sport_queue_cancelled
import dev.alllexey.itmowidgets.shared.feature.sport.sport_queue_completed
import dev.alllexey.itmowidgets.shared.feature.sport.sport_queue_created
import dev.alllexey.itmowidgets.shared.feature.sport.sport_queue_expired
import dev.alllexey.itmowidgets.shared.feature.sport.sport_queue_last_request
import dev.alllexey.itmowidgets.shared.feature.sport.sport_queue_place
import dev.alllexey.itmowidgets.shared.feature.sport.sport_ratio
import dev.alllexey.itmowidgets.shared.feature.sport.sport_rule_started
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.core.sport_booking_conditions
import dev.alllexey.itmowidgets.shared.core.sport_prediction_hint
import dev.alllexey.itmowidgets.shared.core.sport_prediction_waiting
import dev.alllexey.itmowidgets.shared.core.sport_queue_free_hint
import dev.alllexey.itmowidgets.shared.core.sport_queue_future_hint
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes
import dev.alllexey.itmowidgets.shared.designsystem.ic_check
import dev.alllexey.itmowidgets.shared.designsystem.ic_error
import dev.alllexey.itmowidgets.shared.designsystem.ic_info
import dev.alllexey.itmowidgets.shared.designsystem.ic_schedule

/*
 * The sections of the sport details sheet under its header, in the order of `fragment_sport_common_details.xml`:
 * the outlined registration card, then the conditions, the comment and the friends, each under a divider and a
 * heading.
 */

/** `registration_card`: the status, the occupancy ring of a real lesson, the queue entry and its history. */
@Composable
internal fun SportRegistrationCard(registration: SportRegistrationDetails, timeZone: TimeZone) {
    Surface(
        Modifier
            .padding(top = ItmoTheme.spacing.group)
            .fillMaxWidth()
            .testTag(SportDetailsSheetTestTags.REGISTRATION),
        shape = ItmoTheme.shapes.cardContent,
        color = ItmoTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(ItmoTheme.shapes.cardStroke, ItmoTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(horizontal = ItmoTheme.spacing.content, vertical = ItmoTheme.spacing.group)) {
            registration.status?.let { RegistrationStatus(it) }
            registration.occupancy?.let { Capacity(it) }
            registration.queue?.let { Queue(it, timeZone) }
        }
    }
}

/** The status line with its icon, both in the status tone, or neutral while the status carries no outcome. */
@Composable
private fun RegistrationStatus(status: SportRegistrationStatus) {
    val accent = status.conditionTone()?.accent() ?: ItmoTheme.colorScheme.onSurfaceVariant
    val icon = when (status) {
        SportRegistrationStatus.SIGNED, SportRegistrationStatus.AUTO_SIGNED -> KitRes.drawable.ic_check
        SportRegistrationStatus.WAITING, SportRegistrationStatus.NOTIFIED -> KitRes.drawable.ic_schedule
        SportRegistrationStatus.FAILED, SportRegistrationStatus.EXPIRED -> KitRes.drawable.ic_error
        SportRegistrationStatus.CANCELLED, SportRegistrationStatus.NOT_SIGNED -> KitRes.drawable.ic_info
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            modifier = Modifier
                .padding(end = ItmoTheme.spacing.compact)
                .size(StatusIconSize),
            tint = accent,
        )
        Text(stringResource(status.labelResource()), color = accent, style = ItmoTheme.typography.titleSmall)
    }
}

/**
 * The ring owns occupied over the limit, the column beside it the free count: no number twice. One TalkBack stop
 * reads all three.
 */
@Composable
private fun Capacity(occupancy: SportOccupancy) {
    val accent = occupancy.tone().accent()
    val description = stringResource(
        Res.string.sport_capacity_description,
        occupancy.occupied,
        occupancy.limit,
        occupancy.available,
    )
    Row(
        Modifier
            .padding(top = ItmoTheme.spacing.compact)
            .fillMaxWidth()
            .testTag(SportDetailsSheetTestTags.CAPACITY)
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(RingFrame), contentAlignment = Alignment.Center) {
            val share = occupancy.occupied * PERCENT / occupancy.limit
            ScoreRing(
                sectors = if (occupancy.occupied > 0) listOf(ScoreRingSector(accent, share)) else emptyList(),
                modifier = Modifier.size(RingSize),
                thickness = RingThickness,
                trackColor = ItmoTheme.colorScheme.outlineVariant,
                gapAngle = 0f,
            ) {
                Column(Modifier.width(RingLabelWidth), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(Res.string.sport_capacity_occupied, occupancy.occupied, occupancy.limit),
                        color = ItmoTheme.colorScheme.onSurface,
                        style = ItmoTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        stringResource(Res.string.sport_capacity_occupied_label),
                        color = ItmoTheme.colorScheme.onSurfaceVariant,
                        style = ItmoTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = ItmoTheme.spacing.group),
        ) {
            Text(
                if (occupancy.available == 0) {
                    stringResource(Res.string.sport_capacity_none)
                } else {
                    occupancy.available.toString()
                },
                Modifier.testTag(SportDetailsSheetTestTags.CAPACITY_FREE),
                color = accent,
                style = ItmoTheme.typography.headlineSmall,
            )
            Text(
                pluralStringResource(Res.plurals.sport_capacity_free_label, occupancy.available),
                Modifier.testTag(SportDetailsSheetTestTags.CAPACITY_LABEL),
                color = ItmoTheme.colorScheme.onSurfaceVariant,
                style = ItmoTheme.typography.bodySmall,
            )
        }
    }
}

/** The queue hint while the entry waits, the place and the requests, then the entry's history. */
@Composable
private fun Queue(queue: SportQueueDetails, timeZone: TimeZone) {
    if (queue.waiting) {
        Text(
            stringResource(
                if (queue.autoSign) CoreRes.string.sport_queue_future_hint else CoreRes.string.sport_queue_free_hint,
            ),
            Modifier.padding(top = ItmoTheme.spacing.compact),
            color = ItmoTheme.colorScheme.onSurfaceVariant,
            style = ItmoTheme.typography.bodySmall,
        )
    }
    Row(Modifier.padding(top = ItmoTheme.spacing.group).fillMaxWidth()) {
        if (queue.positionVisible) {
            QueueFigure(
                stringResource(Res.string.sport_ratio, queue.position, queue.total),
                stringResource(Res.string.sport_queue_place),
                Modifier
                    .weight(1f)
                    .padding(end = ItmoTheme.spacing.content),
            )
        }
        QueueFigure(
            stringResource(Res.string.sport_ratio, queue.notificationAttempts, queue.maxNotificationAttempts),
            stringResource(Res.string.sport_queue_attempts),
            Modifier
                .weight(1f)
                .testTag(SportDetailsSheetTestTags.ATTEMPTS),
        )
    }
    Column(Modifier.padding(top = ItmoTheme.spacing.content)) {
        queue.history.forEach { HistoryRow(it, timeZone) }
    }
}

@Composable
private fun QueueFigure(value: String, label: String, modifier: Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Text(value, color = ItmoTheme.colorScheme.onSurface, style = ItmoTheme.typography.headlineSmall)
        Text(label, color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodySmall)
    }
}

/** Queue history is a label/value table: a clock icon repeated on every row would carry nothing. */
@Composable
private fun HistoryRow(fact: SportQueueFact, timeZone: TimeZone) {
    val label = when (fact.kind) {
        SportQueueFactKind.CREATED -> Res.string.sport_queue_created
        SportQueueFactKind.LAST_REQUEST -> Res.string.sport_queue_last_request
        SportQueueFactKind.COMPLETED -> Res.string.sport_queue_completed
        SportQueueFactKind.CANCELLED -> Res.string.sport_queue_cancelled
        SportQueueFactKind.EXPIRED -> Res.string.sport_queue_expired
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = HistoryRowPadding)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(
            stringResource(label),
            Modifier
                .weight(1f)
                .padding(end = ItmoTheme.spacing.content),
            color = ItmoTheme.colorScheme.onSurfaceVariant,
            style = ItmoTheme.typography.bodySmall,
        )
        Text(
            fact.at.toLocalDateTime(timeZone).format(DateTexts.DAY_SHORT_MONTH_TIME),
            color = ItmoTheme.colorScheme.onSurface,
            style = ItmoTheme.typography.bodySmall,
            textAlign = TextAlign.End,
        )
    }
}

/** `attention_card`: one tinted row per condition, in the presenter's order. */
@Composable
internal fun SportConditionsSection(conditions: List<SportDetailsCondition>) {
    DetailsSection(
        stringResource(CoreRes.string.sport_booking_conditions),
        Modifier.testTag(SportDetailsSheetTestTags.CONDITIONS),
    ) {
        conditions.forEach { ConditionRow(it.look()) }
    }
}

/** What one condition row says: its tone and icon, a title, an optional body and an optional note. */
private class ConditionLook(
    val tone: SportConditionTone,
    val icon: DrawableResource,
    val title: StringResource,
    val description: String? = null,
    val note: String? = null,
)

@Composable
private fun SportDetailsCondition.look(): ConditionLook = when (this) {
    SportDetailsCondition.Allowed ->
        ConditionLook(SportConditionTone.ALLOWED, KitRes.drawable.ic_check, Res.string.sport_booking_allowed)
    is SportDetailsCondition.Waiting -> ConditionLook(
        tone = SportConditionTone.WAITING,
        icon = KitRes.drawable.ic_schedule,
        title = if (predicted) CoreRes.string.sport_prediction_waiting else Res.string.sport_booking_wait,
        description = stringResource(
            if (predicted) CoreRes.string.sport_prediction_hint else Res.string.sport_booking_wait_place,
        ),
        note = listOfNotNull(
            stringResource(Res.string.sport_booking_auto_checks),
            if (predicted) stringResource(Res.string.sport_booking_future_checks) else null,
        ).joinToString("\n"),
    )
    SportDetailsCondition.Started ->
        ConditionLook(SportConditionTone.BLOCKED, KitRes.drawable.ic_error, Res.string.sport_rule_started)
    SportDetailsCondition.Uncertain ->
        ConditionLook(SportConditionTone.WARNING, KitRes.drawable.ic_error, Res.string.sport_booking_uncertain)
    is SportDetailsCondition.Restricted -> ConditionLook(
        tone = SportConditionTone.BLOCKED,
        icon = KitRes.drawable.ic_error,
        title = Res.string.sport_booking_no_bypass,
        description = restrictions.map { restriction ->
            restriction.detail?.takeIf { it.isNotBlank() } ?: stringResource(restriction.kind.titleResource())
        }.distinct().joinToString("\n"),
        note = stringResource(Res.string.sport_booking_no_bypass_hint),
    )
    SportDetailsCondition.LateAuto -> ConditionLook(
        tone = SportConditionTone.WARNING,
        icon = KitRes.drawable.ic_schedule,
        title = Res.string.sport_booking_late_auto,
        description = stringResource(Res.string.sport_booking_late_auto_hint),
    )
    SportDetailsCondition.ScheduleOverlap -> ConditionLook(
        tone = SportConditionTone.WARNING,
        icon = KitRes.drawable.ic_error,
        title = Res.string.sport_booking_warning,
        description = stringResource(Res.string.sport_booking_warning_hint),
    )
    is SportDetailsCondition.PredictionMatching -> ConditionLook(
        tone = SportConditionTone.WAITING,
        icon = KitRes.drawable.ic_schedule,
        title = Res.string.sport_prediction_matching,
        description = stringResource(CoreRes.string.sport_prediction_hint),
        note = if (withRules) stringResource(Res.string.sport_booking_prediction_rules) else null,
    )
}

/** `item_sport_condition.xml`: the tone's container, a 20 dp icon on the title's first line, title, body, note. */
@Composable
private fun ConditionRow(look: ConditionLook) {
    val accent = look.tone.accent()
    val titleStyle = ItmoTheme.typography.titleSmall
    Row(
        Modifier
            .padding(top = ItmoTheme.spacing.compact)
            .fillMaxWidth()
            .background(look.tone.container(), ItmoTheme.shapes.large)
            .padding(ItmoTheme.spacing.content)
            .semantics(mergeDescendants = true) {},
    ) {
        Icon(
            painterResource(look.icon),
            contentDescription = null,
            modifier = Modifier
                .padding(top = railIconTop(titleStyle), end = ItmoTheme.spacing.content)
                .size(ConditionIconSize),
            tint = accent,
        )
        Column(Modifier.weight(1f)) {
            Text(stringResource(look.title), color = accent, style = titleStyle)
            look.description?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    Modifier.padding(top = ItmoTheme.spacing.related),
                    color = ItmoTheme.colorScheme.onSurface,
                    style = ItmoTheme.typography.bodyMedium,
                )
            }
            look.note?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    Modifier.padding(top = ItmoTheme.spacing.compact),
                    color = ItmoTheme.colorScheme.onSurfaceVariant,
                    style = ItmoTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** `comment_card`: the lesson's comment from MyITMO. */
@Composable
internal fun SportCommentSection(comment: String) {
    DetailsSection(stringResource(Res.string.sport_details_comment_title)) {
        Text(
            comment,
            Modifier.padding(top = ItmoTheme.spacing.compact),
            color = ItmoTheme.colorScheme.onSurface,
            style = ItmoTheme.typography.bodyMedium,
        )
    }
}

/** `friends_card`: each friend with their registration; a row opens the friend's profile. */
@Composable
internal fun SportFriendsSection(friends: List<SportFriendStatus>, onProfile: (Int) -> Unit) {
    DetailsSection(stringResource(Res.string.sport_friends_count_label, friends.size)) {
        Column(Modifier.padding(top = ItmoTheme.spacing.related)) {
            friends.forEach { FriendRow(it, onProfile) }
        }
    }
}

/** `item_sport_booking_friend_status.xml`: a 40 dp avatar, the name and the registration under it. */
@Composable
private fun FriendRow(friend: SportFriendStatus, onProfile: (Int) -> Unit) {
    val status = when (val registration = friend.registration) {
        SportFriendRegistration.Signed -> stringResource(Res.string.sport_friend_signed)
        is SportFriendRegistration.Queued ->
            stringResource(Res.string.sport_card_queue, registration.position, registration.total)
        SportFriendRegistration.NotSigned -> stringResource(Res.string.sport_friend_not_signed)
        null -> null
    }
    Row(
        Modifier
            .fillMaxWidth()
            .testTag(SportDetailsSheetTestTags.FRIEND)
            .clickable { onProfile(friend.isu) }
            .padding(vertical = ItmoTheme.spacing.content),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(friend.name, friend.pictureUrl, size = FriendAvatarSize)
        Column(
            Modifier
                .weight(1f)
                .padding(start = ItmoTheme.spacing.content),
        ) {
            Text(friend.name, color = ItmoTheme.colorScheme.onSurface, style = ItmoTheme.typography.bodyMedium)
            if (status != null) {
                Text(
                    status,
                    Modifier.padding(top = ItmoTheme.spacing.related),
                    color = ItmoTheme.colorScheme.onSurfaceVariant,
                    style = ItmoTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** A section under the registration card: 20 dp above, a divider, then the heading 16 dp under it. */
@Composable
private fun DetailsSection(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .padding(top = SectionGap)
            .fillMaxWidth(),
    ) {
        HorizontalDivider(color = ItmoTheme.colorScheme.outlineVariant)
        Text(
            title,
            Modifier
                .padding(top = ItmoTheme.spacing.group)
                .semantics { heading() },
            color = ItmoTheme.colorScheme.onSurfaceVariant,
            style = ItmoTheme.typography.titleSmall,
        )
        content()
    }
}

/** `ConditionTone.container`: the accent 12 % over `surfaceContainerLowest`, from the theme's extended colours. */
@Composable
private fun SportConditionTone.container(): Color = with(ItmoTheme.extendedColors) {
    when (this@container) {
        SportConditionTone.ALLOWED -> sportConditionAllowedContainer
        SportConditionTone.WAITING -> sportConditionWaitingContainer
        SportConditionTone.WARNING -> sportConditionWarningContainer
        SportConditionTone.BLOCKED -> sportConditionBlockedContainer
    }
}

/** Centres a [ConditionIconSize] icon on the first line of text in [style] at any font scale (`alignRailIcon`). */
@Composable
private fun railIconTop(style: TextStyle): Dp {
    val line = with(LocalDensity.current) { style.lineHeight.toDp() }
    return ((line - ConditionIconSize) / 2).coerceAtLeast(0.dp)
}

private const val PERCENT = 100f

/** `capacity_progress`: a 100 dp frame, the 96 dp indicator with an 8 dp track, the 76 dp label column. */
private val RingFrame = 100.dp
private val RingSize = 96.dp
private val RingThickness = 8.dp
private val RingLabelWidth = 76.dp

/** The status line's 16 dp compound drawable. */
private val StatusIconSize = 16.dp

/** `item_sport_condition.xml`'s icon. */
private val ConditionIconSize = 20.dp

/** `item_sport_history_fact.xml`'s 6 dp above and below a row. */
private val HistoryRowPadding = 6.dp

/** The 20 dp above each section's divider. */
private val SectionGap = 20.dp

/** `item_sport_booking_friend_status.xml`'s avatar. */
private val FriendAvatarSize = 40.dp
