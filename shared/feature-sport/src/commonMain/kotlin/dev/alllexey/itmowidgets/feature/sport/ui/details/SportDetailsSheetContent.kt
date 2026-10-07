package dev.alllexey.itmowidgets.feature.sport.ui.details

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButton
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButtonStyle
import dev.alllexey.itmowidgets.designsystem.components.header.DetailsFact
import dev.alllexey.itmowidgets.designsystem.components.header.DetailsHeader
import dev.alllexey.itmowidgets.designsystem.components.header.DetailsMapAction
import dev.alllexey.itmowidgets.designsystem.components.header.DetailsTeacher
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetClose
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetClosePlacement
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetScaffold
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingAction
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportCommonDetailsArgs
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportDetailsAction
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportShareTarget
import dev.alllexey.itmowidgets.feature.sport.presentation.common.bookingAction
import dev.alllexey.itmowidgets.feature.sport.ui.common.fullDate
import dev.alllexey.itmowidgets.feature.sport.ui.common.timeRangeText
import dev.alllexey.itmowidgets.feature.sport.ui.common.titleResource
import dev.alllexey.itmowidgets.shared.feature.sport.Res
import dev.alllexey.itmowidgets.shared.feature.sport.sport_auto_sign_title
import dev.alllexey.itmowidgets.shared.feature.sport.sport_card_cancel_auto
import dev.alllexey.itmowidgets.shared.feature.sport.sport_lesson_sign_out
import dev.alllexey.itmowidgets.shared.feature.sport.sport_lesson_sign_up
import dev.alllexey.itmowidgets.shared.feature.sport.sport_lesson_unavailable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.core.common_close
import dev.alllexey.itmowidgets.shared.core.share_action
import dev.alllexey.itmowidgets.shared.core.sport_details_location
import dev.alllexey.itmowidgets.shared.core.sport_details_teacher
import dev.alllexey.itmowidgets.shared.core.sport_details_title
import dev.alllexey.itmowidgets.shared.core.sport_duration
import dev.alllexey.itmowidgets.shared.core.sport_open_map
import dev.alllexey.itmowidgets.shared.core.teacher_open_profile
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes
import dev.alllexey.itmowidgets.shared.designsystem.ic_share

/**
 * The sport details sheet of [item] at [AcademicTimeProvider.now]: the host-facing entry the Fragment host and the
 * SwiftUI host call. The offer is evaluated again on tap: when it changed while the sheet was open (the lesson
 * started, the free queue closed), the sheet shows the new offer and conditions and sends nothing. Otherwise the
 * action goes to [SportDetailsActions.onAction] once per [submission]. [actionsEnabled] is false where the sheet is
 * read-only; [busy] while the screen behind it runs an action.
 */
@Composable
fun SportDetailsSheet(
    item: SportCommonDetailsArgs,
    time: AcademicTimeProvider,
    submission: SportDetailsSubmission,
    actions: SportDetailsActions,
    modifier: Modifier = Modifier,
    actionsEnabled: Boolean = true,
    busy: Boolean = false,
) {
    // Bumped when a tap finds a stale offer, so the sheet evaluates again.
    var evaluation by remember { mutableIntStateOf(0) }
    val submitted = submission.submitted
    val state = remember(item, time, actionsEnabled, busy, submitted, evaluation) {
        SportDetailsSheetState.at(item, time, actionsEnabled, busy, submitted)
    }
    val guarded = SportDetailsActions(
        onAction = { offer ->
            when {
                busy || submission.submitted -> Unit
                item.bookingAction(time.now()) != offer -> evaluation++
                submission.submit() -> actions.onAction(offer)
            }
        },
        onShare = actions.onShare,
        onMap = actions.onMap,
        onProfile = actions.onProfile,
        onClose = actions.onClose,
    )
    SportDetailsSheetContent(state, guarded, modifier)
}

/**
 * The body of the sport details sheet (`fragment_sport_common_details.xml`): the toolbar with close and share, the
 * kit's [DetailsHeader], the registration card (status, occupancy ring, queue and its history), the booking
 * conditions, the lesson's comment, the friends on it, and the booking action pinned under the scrolling content.
 * Stateless: [SportDetailsSheet] evaluates the state and guards the action. The host owns the sheet's container
 * (`colorSurfaceContainerLowest`, 90 % of the screen) and every effect.
 */
@Composable
fun SportDetailsSheetContent(
    state: SportDetailsSheetState,
    actions: SportDetailsActions,
    modifier: Modifier = Modifier,
) {
    val details = state.details
    Box(modifier) {
        SheetScaffold(
            title = stringResource(CoreRes.string.sport_details_title),
            close = SheetClose(stringResource(CoreRes.string.common_close), actions.onClose),
            closePlacement = SheetClosePlacement.Start,
            footer = details.action?.let { offer -> { BookingButton(offer, actions) } },
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag(SportDetailsSheetTestTags.SCROLL)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = ItmoTheme.spacing.screenMargin,
                        end = ItmoTheme.spacing.screenMargin,
                        bottom = ItmoTheme.spacing.section,
                    ),
            ) {
                Header(state, actions)
                if (details.registration.visible) SportRegistrationCard(details.registration, state.timeZone)
                if (details.conditions.isNotEmpty()) SportConditionsSection(details.conditions)
                state.item.comment?.takeIf { it.isNotBlank() }?.let { SportCommentSection(it) }
                if (details.friends.isNotEmpty()) SportFriendsSection(details.friends, actions.onProfile)
            }
        }
        details.share?.let { target -> ShareAction(target, actions.onShare, Modifier.align(Alignment.TopEnd)) }
    }
}

@Composable
private fun Header(state: SportDetailsSheetState, actions: SportDetailsActions) {
    val item = state.item
    val timing = state.timing
    val teacherIsu = UserScreenArgs.profileIsu(item.teacherIsu.toLong())
    val teacherFact = DetailsFact(stringResource(CoreRes.string.sport_details_teacher), item.teacherFio)
    DetailsHeader(
        title = item.sectionName,
        date = timing.fullDate(),
        time = timing.timeRangeText(),
        kind = item.kind?.let { stringResource(it.titleResource()) },
        duration = timing.durationMinutes?.let { stringResource(CoreRes.string.sport_duration, it) },
        teacher = DetailsTeacher(
            fact = teacherFact,
            onClick = teacherIsu?.let { isu -> { actions.onProfile(isu) } },
            clickLabel = teacherIsu?.let { stringResource(CoreRes.string.teacher_open_profile) },
        ),
        place = DetailsFact(stringResource(CoreRes.string.sport_details_location), item.roomName),
        map = item.mapAddress?.let { address ->
            DetailsMapAction(stringResource(CoreRes.string.sport_open_map)) { actions.onMap(address) }
        },
    )
}

/**
 * The toolbar's share action over the scaffold's header, flush with the end of its 56 dp bar under the handle, as
 * the toolbar's action menu sat. The kit's
 * scaffold has no slot for bar actions yet (hand-in to L08); until it has, the action sits over the header, which a
 * short fixed title leaves free.
 */
@Composable
private fun ShareAction(target: SportShareTarget, onShare: (SportShareTarget) -> Unit, modifier: Modifier) {
    IconButton(
        onClick = { onShare(target) },
        modifier = modifier
            .padding(top = ShareTop)
            .testTag(SportDetailsSheetTestTags.SHARE),
    ) {
        Icon(
            painterResource(KitRes.drawable.ic_share),
            contentDescription = stringResource(CoreRes.string.share_action),
            tint = ItmoTheme.colorScheme.onSurface,
        )
    }
}

/** `booking_action`: a full-width tonal button 8 dp under the content, disabled while busy or once sent. */
@Composable
private fun BookingButton(offer: SportDetailsAction, actions: SportDetailsActions) {
    ProgressButton(
        label = stringResource(offer.action.labelResource()),
        onClick = { actions.onAction(offer.action) },
        modifier = Modifier
            .padding(horizontal = ItmoTheme.spacing.screenMargin)
            .padding(top = ItmoTheme.spacing.compact)
            .fillMaxWidth()
            .testTag(SportDetailsSheetTestTags.ACTION),
        style = ProgressButtonStyle.Tonal,
        enabled = offer.enabled,
    )
}

private fun SportBookingAction.labelResource(): StringResource = when (this) {
    SportBookingAction.SIGN -> Res.string.sport_lesson_sign_up
    SportBookingAction.CANCEL -> Res.string.sport_lesson_sign_out
    SportBookingAction.AUTO -> Res.string.sport_auto_sign_title
    SportBookingAction.CANCEL_AUTO -> Res.string.sport_card_cancel_auto
    SportBookingAction.NONE -> Res.string.sport_lesson_unavailable
}

/** The handle (8 dp and 4 dp) and the 56 dp bar centre a 48 dp button 16 dp below the sheet's edge. */
private val ShareTop = 16.dp

