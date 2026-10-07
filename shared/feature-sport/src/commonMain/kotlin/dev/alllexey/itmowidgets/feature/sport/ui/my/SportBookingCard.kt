package dev.alllexey.itmowidgets.feature.sport.ui.my

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.designsystem.components.menus.ItmoMenu
import dev.alllexey.itmowidgets.designsystem.components.menus.ItmoMenuItem
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportRegistrationStatus
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportSessionTiming
import dev.alllexey.itmowidgets.feature.sport.ui.common.SportCardSurface
import dev.alllexey.itmowidgets.feature.sport.ui.common.SportCardTitle
import dev.alllexey.itmowidgets.feature.sport.ui.common.SportFriendsPreview
import dev.alllexey.itmowidgets.feature.sport.ui.common.SportMetaRow
import dev.alllexey.itmowidgets.feature.sport.ui.common.accent
import dev.alllexey.itmowidgets.feature.sport.ui.common.conditionTone
import dev.alllexey.itmowidgets.feature.sport.ui.common.dateText
import dev.alllexey.itmowidgets.feature.sport.ui.common.labelResource
import dev.alllexey.itmowidgets.feature.sport.ui.common.sportQueuePositionText
import dev.alllexey.itmowidgets.feature.sport.ui.common.timeRangeText
import dev.alllexey.itmowidgets.feature.sport.ui.common.weekdayText
import dev.alllexey.itmowidgets.shared.feature.sport.Res
import dev.alllexey.itmowidgets.shared.feature.sport.common_options
import kotlinx.datetime.format
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.core.sport_cancel_booking_action
import dev.alllexey.itmowidgets.shared.core.sport_open_map
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes
import dev.alllexey.itmowidgets.shared.designsystem.ic_check
import dev.alllexey.itmowidgets.shared.designsystem.ic_error
import dev.alllexey.itmowidgets.shared.designsystem.ic_info
import dev.alllexey.itmowidgets.shared.designsystem.ic_location_on
import dev.alllexey.itmowidgets.shared.designsystem.ic_more_vert
import dev.alllexey.itmowidgets.shared.designsystem.ic_person
import dev.alllexey.itmowidgets.shared.designsystem.ic_schedule

/** What a booking card asks of its host: open the details, cancel the booking or its queue entry, open the map. */
@Immutable
class SportBookingActions(
    val onOpen: (SportBooking) -> Unit = {},
    val onCancel: (SportBooking) -> Unit = {},
    val onOpenMap: (SportBooking) -> Unit = {},
)

/**
 * A `Мой спорт` booking (`item_sport_booking.xml`): the title, the date block beside the weekday, the time and the
 * registration status, the teacher and the place, the friends on it. The whole card opens the details; the more
 * button, shown only for a booking or a queue entry, offers the cancellation and, when the place has an address, the
 * map. A booking carries no capacity, so a predicted one shows no places either.
 *
 * [readOnly] is another user's booking: the card opens nothing and shows no friends, and the more button, shown only
 * when the place has an address, offers the map alone.
 */
@Composable
fun SportBookingCard(
    booking: SportBooking,
    time: AcademicTimeProvider,
    actions: SportBookingActions,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
) {
    val timing = SportSessionTiming(booking.start, booking.end, time)
    SportCardSurface(
        onClick = if (readOnly) null else ({ actions.onOpen(booking) }),
        modifier = modifier.testTag(SportBookingCardTestTags.CARD),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                SportCardTitle(
                    booking.sectionName.shorten(),
                    Modifier
                        .padding(end = ItmoTheme.spacing.touchTarget + ItmoTheme.spacing.compact)
                        .testTag(SportBookingCardTestTags.TITLE),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = ItmoTheme.spacing.compact),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DateBlock(timing)
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(start = ItmoTheme.spacing.content),
                    ) {
                        val date = timing.dateText().asString()
                        Text(
                            timing.weekdayText().asString(),
                            Modifier.semantics { contentDescription = date },
                            color = ItmoTheme.colorScheme.onSurfaceVariant,
                            style = ItmoTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            timing.timeRangeText(),
                            Modifier
                                .padding(top = TimeGap)
                                .testTag(SportBookingCardTestTags.TIME),
                            color = ItmoTheme.colorScheme.onSurface,
                            style = ItmoTheme.typography.titleSmall,
                        )
                        StatusLine(booking, Modifier.padding(top = StatusGap))
                    }
                }
                if (booking.teacherFio.isNotBlank()) {
                    SportMetaRow(
                        KitRes.drawable.ic_person,
                        booking.teacherFio,
                        Modifier.padding(top = ItmoTheme.spacing.content),
                        Modifier.testTag(SportBookingCardTestTags.META),
                    )
                }
                if (booking.roomName.isNotBlank()) {
                    SportMetaRow(
                        KitRes.drawable.ic_location_on,
                        booking.roomName,
                        Modifier.padding(top = ItmoTheme.spacing.related),
                        Modifier.testTag(SportBookingCardTestTags.META),
                    )
                }
                if (!readOnly) SportFriendsPreview(booking.friendsBookings)
            }
            val hasMenu = if (readOnly) {
                booking.extractBuildingAddress() != null
            } else {
                booking.signed || booking.signEntry != null
            }
            if (hasMenu) {
                MoreButton(
                    booking,
                    actions,
                    readOnly,
                    // The View put the button 8 dp inside the card's corner, half the card padding.
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = ItmoTheme.spacing.compact, y = -ItmoTheme.spacing.compact),
                )
            }
        }
    }
}

/** The status text of [booking]: the queue position while it waits, otherwise the registration status. */
fun sportBookingStatusText(booking: SportBooking): UiText {
    val status = SportRegistrationStatus.from(booking.signed, booking.signEntry)
    val entry = booking.signEntry
    return if (entry != null && (status == SportRegistrationStatus.WAITING || status == SportRegistrationStatus.NOTIFIED)) {
        sportQueuePositionText(entry.position, entry.total)
    } else {
        UiText.Res(status.labelResource())
    }
}

/** Tags for host tests and the screen's instrumented flow. */
object SportBookingCardTestTags {
    const val CARD = "sport_booking_card"
    const val TITLE = "sport_booking_title"
    const val TIME = "sport_booking_time"
    const val STATUS = "sport_booking_status"
    const val META = "sport_booking_meta"
    const val MORE = "sport_booking_more"
}

/** The day over the short month on `secondaryContainer`; decorative, the weekday line reads the date aloud. */
@Composable
private fun DateBlock(timing: SportSessionTiming) {
    Column(
        Modifier
            .width(DateBlockSize)
            .heightIn(min = DateBlockSize)
            .background(ItmoTheme.colorScheme.secondaryContainer, ItmoTheme.shapes.large)
            .padding(vertical = ItmoTheme.spacing.compact)
            .clearAndSetSemantics {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            timing.start.date.format(DateTexts.DAY),
            color = ItmoTheme.colorScheme.onSecondaryContainer,
            style = ItmoTheme.typography.titleLarge,
            maxLines = 1,
        )
        Text(
            timing.start.date.format(DateTexts.MONTH_SHORT).trimEnd('.').uppercase(),
            Modifier.padding(top = TimeGap),
            color = ItmoTheme.colorScheme.onSecondaryContainer,
            style = ItmoTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

/** The status behind its 16 dp icon, both in the status tone (`onSurfaceVariant` while it has no outcome). */
@Composable
private fun StatusLine(booking: SportBooking, modifier: Modifier = Modifier) {
    val status = SportRegistrationStatus.from(booking.signed, booking.signEntry)
    val color = status.conditionTone()?.accent() ?: ItmoTheme.colorScheme.onSurfaceVariant
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(status.icon()),
            contentDescription = null,
            modifier = Modifier.size(StatusIconSize),
            tint = color,
        )
        Spacer(Modifier.width(ItmoTheme.spacing.compact))
        Text(
            sportBookingStatusText(booking).asString(),
            Modifier.testTag(SportBookingCardTestTags.STATUS),
            color = color,
            style = ItmoTheme.typography.labelMedium,
        )
    }
}

/**
 * The 48 dp more button with its menu: the cancellation (not on a [readOnly] card), then the map when the place has an
 * address.
 */
@Composable
private fun MoreButton(
    booking: SportBooking,
    actions: SportBookingActions,
    readOnly: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val description = stringResource(Res.string.common_options)
    val items = buildList {
        if (!readOnly) {
            add(ItmoMenuItem(stringResource(CoreRes.string.sport_cancel_booking_action), { actions.onCancel(booking) }))
        }
        if (booking.extractBuildingAddress() != null) {
            add(ItmoMenuItem(stringResource(CoreRes.string.sport_open_map), { actions.onOpenMap(booking) }))
        }
    }
    Box(
        modifier
            .size(ItmoTheme.spacing.touchTarget)
            .testTag(SportBookingCardTestTags.MORE)
            .clickable(
                role = Role.Button,
                interactionSource = null,
                indication = ripple(bounded = false, radius = MoreRippleRadius),
                onClick = { expanded = true },
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(KitRes.drawable.ic_more_vert),
            contentDescription = null,
            modifier = Modifier.size(MoreIconSize),
            tint = ItmoTheme.colorScheme.onSurfaceVariant,
        )
        ItmoMenu(expanded, onDismissRequest = { expanded = false }, groups = listOf(items))
    }
}

private fun SportRegistrationStatus.icon(): DrawableResource = when (this) {
    SportRegistrationStatus.SIGNED, SportRegistrationStatus.AUTO_SIGNED -> KitRes.drawable.ic_check
    SportRegistrationStatus.WAITING, SportRegistrationStatus.NOTIFIED -> KitRes.drawable.ic_schedule
    SportRegistrationStatus.FAILED, SportRegistrationStatus.EXPIRED -> KitRes.drawable.ic_error
    SportRegistrationStatus.CANCELLED, SportRegistrationStatus.NOT_SIGNED -> KitRes.drawable.ic_info
}

/** `item_sport_booking.xml`'s date card: 56 dp wide and at least as high. */
private val DateBlockSize = 56.dp

/** 2 dp between the weekday and the time, and between the day and the month, off the 4 dp grid. */
private val TimeGap = 2.dp

/** 6 dp above the status, off the 4 dp grid. */
private val StatusGap = 6.dp

private val StatusIconSize = 16.dp

/** `ic_more_vert` inside the 48 dp target: 12 dp padding on each side. */
private val MoreIconSize = 24.dp
private val MoreRippleRadius = 20.dp
