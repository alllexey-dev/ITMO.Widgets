package dev.alllexey.itmowidgets.feature.schedule.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.text.DateTexts
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
import dev.alllexey.itmowidgets.shared.feature.schedule.Res
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_auto_sign_prediction
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_auto_sign_waiting
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_open_sport
import kotlinx.datetime.format
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.core.common_close
import dev.alllexey.itmowidgets.shared.core.sport_booking_conditions
import dev.alllexey.itmowidgets.shared.core.sport_details_location
import dev.alllexey.itmowidgets.shared.core.sport_details_teacher
import dev.alllexey.itmowidgets.shared.core.sport_details_title
import dev.alllexey.itmowidgets.shared.core.sport_duration
import dev.alllexey.itmowidgets.shared.core.sport_open_map
import dev.alllexey.itmowidgets.shared.core.sport_prediction_hint
import dev.alllexey.itmowidgets.shared.core.sport_prediction_waiting
import dev.alllexey.itmowidgets.shared.core.sport_queue_free_hint
import dev.alllexey.itmowidgets.shared.core.sport_queue_future_hint
import dev.alllexey.itmowidgets.shared.core.sport_registration_waiting
import dev.alllexey.itmowidgets.shared.core.teacher_open_profile
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes
import dev.alllexey.itmowidgets.shared.designsystem.ic_schedule

/**
 * The body of the pending sport sheet (`fragment_pending_sport_details.xml`): a queued or predicted sport booking
 * from the schedule, laid out like the sport tab's own details. The toolbar with close, the kit's [DetailsHeader]
 * (section, the status as the kind line, date and time, teacher, place and the map), one `Условия записи` card, and
 * `Открыть в спорте` pinned under the scrolling content: managing the queue happens on the sport tab. Stateless; the
 * host owns the sheet's container (`colorSurfaceContainerLowest`, 90 % of the screen) and every effect.
 */
@Composable
fun PendingSportDetailsContent(
    state: PendingSportDetailsSheetState,
    actions: PendingSportDetailsActions,
    modifier: Modifier = Modifier,
) {
    SheetScaffold(
        title = stringResource(CoreRes.string.sport_details_title),
        modifier = modifier,
        close = SheetClose(stringResource(CoreRes.string.common_close), actions.onClose),
        closePlacement = SheetClosePlacement.Start,
        footer = { OpenSportButton(actions.onOpenSport) },
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag(PendingSportDetailsTestTags.SCROLL)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = ItmoTheme.spacing.screenMargin,
                    end = ItmoTheme.spacing.screenMargin,
                    bottom = ItmoTheme.spacing.section,
                ),
        ) {
            PendingSportHeader(state, actions)
            PendingSportConditions(state)
        }
    }
}

/**
 * `view_details_header.xml` as the pending sheet bound it: no type colour and no flow, the status as the kind line,
 * the teacher opens their profile only with a usable ISU, the map only for a named room.
 */
@Composable
private fun PendingSportHeader(state: PendingSportDetailsSheetState, actions: PendingSportDetailsActions) {
    val booking = state.booking
    val start = state.start
    val end = state.end
    val minutes = (end.time.toSecondOfDay() - start.time.toSecondOfDay()) / SECONDS_PER_MINUTE
    val teacherIsu = UserScreenArgs.profileIsu(booking.teacherIsu?.toLong())
    DetailsHeader(
        title = booking.sectionName,
        date = start.date.format(DateTexts.WEEKDAY_DAY_MONTH_YEAR).replaceFirstChar(Char::uppercaseChar),
        time = "${start.time.format(DateTexts.TIME)}$TIME_DASH${end.time.format(DateTexts.TIME)}",
        kind = stringResource(
            if (booking.isPrediction) Res.string.schedule_auto_sign_prediction else Res.string.schedule_auto_sign_waiting,
        ),
        duration = minutes.takeIf { it > 0 }?.let { stringResource(CoreRes.string.sport_duration, it) },
        teacher = DetailsTeacher(
            fact = DetailsFact(stringResource(CoreRes.string.sport_details_teacher), booking.teacherFio),
            onClick = teacherIsu?.let { isu -> { actions.onProfile(isu) } },
            clickLabel = teacherIsu?.let { stringResource(CoreRes.string.teacher_open_profile) },
        ),
        place = DetailsFact(stringResource(CoreRes.string.sport_details_location), booking.roomName),
        map = if (state.mapAvailable) {
            DetailsMapAction(stringResource(CoreRes.string.sport_open_map), actions.onMap)
        } else {
            null
        },
    )
}

/**
 * `attention_card`: the one waiting card the sport tab shows for a queue or a prediction, in the fixed waiting tone
 * (`ConditionTone.WAITING`), with the hint of what the queue waits for.
 */
@Composable
private fun PendingSportConditions(state: PendingSportDetailsSheetState) {
    val booking = state.booking
    val title = if (booking.isPrediction) CoreRes.string.sport_prediction_waiting else CoreRes.string.sport_registration_waiting
    val hint = when {
        booking.isPrediction -> CoreRes.string.sport_prediction_hint
        booking.autoSign -> CoreRes.string.sport_queue_future_hint
        else -> CoreRes.string.sport_queue_free_hint
    }
    Column(
        Modifier
            .padding(top = SectionGap)
            .fillMaxWidth()
            .testTag(PendingSportDetailsTestTags.CONDITIONS),
    ) {
        HorizontalDivider(color = ItmoTheme.colorScheme.outlineVariant)
        Text(
            stringResource(CoreRes.string.sport_booking_conditions),
            Modifier
                .padding(top = ItmoTheme.spacing.group)
                .semantics { heading() },
            color = ItmoTheme.colorScheme.onSurfaceVariant,
            style = ItmoTheme.typography.titleSmall,
        )
        WaitingCondition(stringResource(title), stringResource(hint))
    }
}

/** `item_sport_condition.xml` in the waiting tone: the tone's container, a 20 dp icon on the title's first line. */
@Composable
private fun WaitingCondition(title: String, body: String) {
    val accent = ItmoTheme.extendedColors.sportConditionWaiting
    val titleStyle = ItmoTheme.typography.titleSmall
    val iconTop = with(LocalDensity.current) { ((titleStyle.lineHeight.toDp() - ConditionIconSize) / 2).coerceAtLeast(0.dp) }
    Row(
        Modifier
            .padding(top = ItmoTheme.spacing.compact)
            .fillMaxWidth()
            .background(ItmoTheme.extendedColors.sportConditionWaitingContainer, ItmoTheme.shapes.large)
            .padding(ItmoTheme.spacing.content)
            .semantics(mergeDescendants = true) {},
    ) {
        Icon(
            painterResource(KitRes.drawable.ic_schedule),
            contentDescription = null,
            modifier = Modifier
                .padding(top = iconTop, end = ItmoTheme.spacing.content)
                .size(ConditionIconSize),
            tint = accent,
        )
        Column(Modifier.weight(1f)) {
            Text(title, color = accent, style = titleStyle)
            Text(
                body,
                Modifier.padding(top = ItmoTheme.spacing.related),
                color = ItmoTheme.colorScheme.onSurface,
                style = ItmoTheme.typography.bodyMedium,
            )
        }
    }
}

/** `open_sport`: a full-width tonal button 8 dp under the content. */
@Composable
private fun OpenSportButton(onOpenSport: () -> Unit) {
    ProgressButton(
        label = stringResource(Res.string.schedule_open_sport),
        onClick = onOpenSport,
        modifier = Modifier
            .padding(horizontal = ItmoTheme.spacing.screenMargin)
            .padding(top = ItmoTheme.spacing.compact)
            .fillMaxWidth()
            .testTag(PendingSportDetailsTestTags.OPEN_SPORT),
        style = ProgressButtonStyle.Tonal,
    )
}

private const val TIME_DASH = "\u2013"
private const val SECONDS_PER_MINUTE = 60

/** The 20 dp above the conditions' divider. */
private val SectionGap = 20.dp

/** `item_sport_condition.xml`'s icon. */
private val ConditionIconSize = 20.dp
