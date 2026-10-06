package dev.alllexey.itmowidgets.feature.sport.ui.sign

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingAction
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingAvailability
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportOccupancy
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportSessionTiming
import dev.alllexey.itmowidgets.feature.sport.presentation.common.bookingConditions
import dev.alllexey.itmowidgets.feature.sport.ui.common.SportCardAction
import dev.alllexey.itmowidgets.feature.sport.ui.common.SportCardSurface
import dev.alllexey.itmowidgets.feature.sport.ui.common.SportCardTimeRow
import dev.alllexey.itmowidgets.feature.sport.ui.common.SportCardTitle
import dev.alllexey.itmowidgets.feature.sport.ui.common.SportConditionTone
import dev.alllexey.itmowidgets.feature.sport.ui.common.SportFriendsPreview
import dev.alllexey.itmowidgets.feature.sport.ui.common.SportIntersectionMark
import dev.alllexey.itmowidgets.feature.sport.ui.common.SportMetaRow
import dev.alllexey.itmowidgets.feature.sport.ui.common.SportOccupancyBar
import dev.alllexey.itmowidgets.feature.sport.ui.common.SportOccupancyLabel
import dev.alllexey.itmowidgets.feature.sport.ui.common.accent
import dev.alllexey.itmowidgets.feature.sport.ui.common.sportCardBottomPadding
import dev.alllexey.itmowidgets.feature.sport.ui.common.sportQueuePositionText
import dev.alllexey.itmowidgets.feature.sport.ui.common.text
import dev.alllexey.itmowidgets.feature.sport.ui.common.timeRangeText
import dev.alllexey.itmowidgets.shared.feature.sport.Res
import dev.alllexey.itmowidgets.shared.feature.sport.sport_auto_sign_title
import dev.alllexey.itmowidgets.shared.feature.sport.sport_card_cancel_auto
import dev.alllexey.itmowidgets.shared.feature.sport.sport_lesson_prediction
import dev.alllexey.itmowidgets.shared.feature.sport.sport_lesson_sign_out
import dev.alllexey.itmowidgets.shared.feature.sport.sport_lesson_sign_up
import dev.alllexey.itmowidgets.shared.feature.sport.sport_lesson_unavailable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes
import dev.alllexey.itmowidgets.shared.designsystem.ic_location_on
import dev.alllexey.itmowidgets.shared.designsystem.ic_person

/** What a lesson card asks of its host: open the details, or run the offered booking action. */
@Immutable
class SportLessonActions(
    val onOpen: (SportLesson) -> Unit = {},
    val onAction: (SportLesson, SportBookingAction) -> Unit = { _, _ -> },
)

/**
 * A `Запись` lesson: the title, the time with the kind chip and the intersection mark, the teacher and the place, the
 * friends on it, the occupancy of a real lesson and the one action [SportBookingConditions.evaluate] offers at
 * [AcademicTimeProvider.now]. The offer is evaluated again on tap: when it changed in between (the lesson started,
 * the free queue closed), the card shows the new offer and sends nothing. While [busy] the action is disabled.
 */
@Composable
fun SportLessonCard(
    lesson: SportLesson,
    time: AcademicTimeProvider,
    actions: SportLessonActions,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
) {
    // Bumped when a tap finds a stale offer, so the card evaluates again.
    var evaluation by remember { mutableIntStateOf(0) }
    val availability = remember(lesson, evaluation) { lesson.bookingConditions().evaluate(time.now()) }
    val offer = availability.offer(lesson)
    val occupancy = SportOccupancy.from(lesson.isLessonReal, lesson.available, lesson.limit)
    SportCardSurface(
        onClick = { actions.onOpen(lesson) },
        modifier = modifier.testTag(SportLessonCardTestTags.CARD),
        bottomPadding = sportCardBottomPadding(endsWithAction = offer.button != null),
    ) {
        SportCardTitle(lesson.sectionName.shorten(), Modifier.testTag(SportLessonCardTestTags.TITLE))
        SportCardTimeRow(
            time = SportSessionTiming(lesson.start, lesson.end, time).timeRangeText(),
            kind = lesson.kind,
            modifier = Modifier.padding(vertical = ItmoTheme.spacing.related),
            timeModifier = Modifier.testTag(SportLessonCardTestTags.TIME),
        ) {
            if (lesson.intersection) SportIntersectionMark()
        }
        if (lesson.teacherFio.isNotBlank()) {
            SportMetaRow(
                KitRes.drawable.ic_person,
                lesson.teacherFio,
                Modifier.padding(top = ItmoTheme.spacing.related),
                Modifier.testTag(SportLessonCardTestTags.META),
            )
        }
        if (lesson.roomName.isNotBlank()) {
            SportMetaRow(
                KitRes.drawable.ic_location_on,
                lesson.roomName,
                Modifier.padding(top = ItmoTheme.spacing.related),
                Modifier.testTag(SportLessonCardTestTags.META),
            )
        }
        SportFriendsPreview(lesson.friendsBookings)
        if (occupancy != null) {
            Spacer(Modifier.height(ItmoTheme.spacing.content))
            SportOccupancyBar(occupancy)
        }
        if (occupancy != null || offer.status != null || offer.button != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = ItmoTheme.spacing.related),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    Modifier
                        .weight(1f)
                        .padding(end = ItmoTheme.spacing.compact),
                ) {
                    if (occupancy != null) SportOccupancyLabel(occupancy)
                    offer.status?.let { status -> StatusText(status) }
                }
                offer.button?.let { button ->
                    SportCardAction(
                        label = stringResource(button.label),
                        tone = button.tone,
                        busy = busy,
                        onClick = {
                            val current = lesson.bookingConditions().evaluate(time.now()).action
                            if (current != availability.action) {
                                evaluation++
                            } else {
                                actions.onAction(lesson, availability.action)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.Bottom)
                            .testTag(SportLessonCardTestTags.ACTION),
                    )
                }
            }
        }
    }
}

/** Tags for host tests and the screen's instrumented flow. */
object SportLessonCardTestTags {
    const val CARD = "sport_lesson_card"
    const val TITLE = "sport_lesson_title"
    const val TIME = "sport_lesson_time"
    const val META = "sport_lesson_meta"
    const val STATUS = "sport_lesson_status"
    const val ACTION = "sport_lesson_action"
}

@Composable
private fun StatusText(status: OfferStatus) {
    Text(
        status.text.asString(),
        Modifier.testTag(SportLessonCardTestTags.STATUS),
        color = status.tone?.accent() ?: ItmoTheme.colorScheme.onSurfaceVariant,
        style = ItmoTheme.typography.bodySmall,
    )
}

/** The card's reading of an [SportBookingAvailability]: an optional status line and an optional action. */
private class LessonOffer(val status: OfferStatus?, val button: OfferButton?)

private class OfferStatus(val text: UiText, val tone: SportConditionTone?)

private class OfferButton(val label: StringResource, val tone: SportConditionTone?)

private fun SportBookingAvailability.offer(lesson: SportLesson): LessonOffer {
    if (action == SportBookingAction.NONE) {
        val reason = restrictions.firstOrNull()?.text() ?: UiText.Res(Res.string.sport_lesson_unavailable)
        return LessonOffer(OfferStatus(reason, SportConditionTone.BLOCKED), null)
    }
    val status = when {
        action == SportBookingAction.CANCEL_AUTO -> lesson.signEntry?.let {
            OfferStatus(sportQueuePositionText(it.position, it.total), SportConditionTone.WAITING)
        }
        !lesson.isLessonReal -> OfferStatus(UiText.Res(Res.string.sport_lesson_prediction), null)
        else -> null
    }
    val button = when (action) {
        SportBookingAction.SIGN -> OfferButton(Res.string.sport_lesson_sign_up, SportConditionTone.ALLOWED)
        SportBookingAction.CANCEL -> OfferButton(Res.string.sport_lesson_sign_out, null)
        SportBookingAction.AUTO -> OfferButton(Res.string.sport_auto_sign_title, SportConditionTone.WAITING)
        SportBookingAction.CANCEL_AUTO -> OfferButton(Res.string.sport_card_cancel_auto, SportConditionTone.WAITING)
        SportBookingAction.NONE -> null
    }
    return LessonOffer(status, button)
}
