package dev.alllexey.itmowidgets.feature.schedule.ui.details

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.model.primaryGroup
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.reviews.tone
import dev.alllexey.itmowidgets.core.schedule.ScheduleChange
import dev.alllexey.itmowidgets.core.schedule.lessonTypeName
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.core.text.detailLines
import dev.alllexey.itmowidgets.core.text.roomShortTitle
import dev.alllexey.itmowidgets.core.text.textResource
import dev.alllexey.itmowidgets.core.text.userDisplayName
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButton
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButtonStyle
import dev.alllexey.itmowidgets.designsystem.components.expressive.ItmoLoadingIndicator
import dev.alllexey.itmowidgets.designsystem.components.header.DetailsFact
import dev.alllexey.itmowidgets.designsystem.components.header.DetailsHeader
import dev.alllexey.itmowidgets.designsystem.components.header.DetailsMapAction
import dev.alllexey.itmowidgets.designsystem.components.header.DetailsTeacher
import dev.alllexey.itmowidgets.designsystem.components.rows.UserRow
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.schedule.presentation.details.LessonFriendsState
import dev.alllexey.itmowidgets.feature.schedule.ui.list.lessonTypeColor
import dev.alllexey.itmowidgets.shared.feature.schedule.Res
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_lesson_details_changes
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_lesson_details_link
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_lesson_details_link_password
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_lesson_friends_count
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_lesson_friends_none
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_lesson_friends_title
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_lesson_note_title
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_open_link
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.format
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.core.common_retry
import dev.alllexey.itmowidgets.shared.core.schedule_lesson_details_flow
import dev.alllexey.itmowidgets.shared.core.schedule_unknown_subject
import dev.alllexey.itmowidgets.shared.core.sport_detail_fact_description
import dev.alllexey.itmowidgets.shared.core.sport_details_location
import dev.alllexey.itmowidgets.shared.core.sport_details_teacher
import dev.alllexey.itmowidgets.shared.core.sport_duration
import dev.alllexey.itmowidgets.shared.core.sport_open_map
import dev.alllexey.itmowidgets.shared.core.teacher_open_profile
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes
import dev.alllexey.itmowidgets.shared.designsystem.ic_videocam

/**
 * `view_details_header.xml` as the lesson sheet bound it: the kind line is the type name and the format with the type
 * colour; the teacher row keeps the tone dot's place for a teacher with an ISU, so a tone arriving later moves
 * neither the name nor the chevron; the place is the short room and the building as MyITMO spells it.
 */
@Composable
internal fun LessonDetailsHeader(state: LessonDetailsSheetState, actions: LessonDetailsActions) {
    val lesson = state.lesson
    val date = LocalDate.parse(lesson.date)
    val start = LocalTime.parse(lesson.start)
    val end = LocalTime.parse(lesson.end)
    val minutes = (end.toSecondOfDay() - start.toSecondOfDay()) / SECONDS_PER_MINUTE
    val teacherIsu = UserScreenArgs.profileIsu(lesson.teacherIsu)
    val kind = listOf(stringResource(lessonTypeName(lesson.typeId)), lesson.format)
        .filter(String::isNotBlank)
        .joinToString(SEPARATOR)
    val room = lesson.room?.let { roomShortTitle(it).asString() }
    DetailsHeader(
        title = lesson.subjectName.ifBlank { stringResource(CoreRes.string.schedule_unknown_subject) },
        date = date.format(DateTexts.WEEKDAY_DAY_MONTH_YEAR).replaceFirstChar(Char::uppercaseChar),
        time = "${start.format(DateTexts.TIME)}$TIME_DASH${end.format(DateTexts.TIME)}",
        kind = kind,
        kindColor = lessonTypeColor(lesson.typeId),
        duration = minutes.takeIf { it > 0 }?.let { stringResource(CoreRes.string.sport_duration, it) },
        teacher = lessonTeacher(lesson.teacherFio.orEmpty(), teacherIsu, state.details.teacherLevel, actions),
        flow = DetailsFact(stringResource(CoreRes.string.schedule_lesson_details_flow), lesson.flowName.orEmpty()),
        place = DetailsFact(
            stringResource(CoreRes.string.sport_details_location),
            listOfNotNull(room, lesson.building).joinToString(SEPARATOR),
        ),
        map = if (state.mapAvailable) {
            DetailsMapAction(stringResource(CoreRes.string.sport_open_map), actions.onMap)
        } else {
            null
        },
    )
}

@Composable
private fun lessonTeacher(
    name: String,
    isu: Int?,
    level: TeacherLevel?,
    actions: LessonDetailsActions,
): DetailsTeacher {
    val tone = level?.tone()
    val toneDescription = tone?.let { it.description(it.label.asString()).asString() }
    return DetailsTeacher(
        fact = DetailsFact(stringResource(CoreRes.string.sport_details_teacher), name),
        onClick = isu?.let { { actions.onProfile(it) } },
        clickLabel = isu?.let { stringResource(CoreRes.string.teacher_open_profile) },
        tone = level?.let { teacherLevelColor(it) },
        toneDescription = toneDescription,
        reserveTone = isu != null,
    )
}

/** The tone dot's colour, harmonized towards the palette's primary by the theme (DS-01a). */
@Composable
private fun teacherLevelColor(level: TeacherLevel): Color = with(ItmoTheme.extendedColors) {
    when (level) {
        TeacherLevel.VERY_NEGATIVE -> teacherLevelVeryNegative
        TeacherLevel.NEGATIVE -> teacherLevelNegative
        TeacherLevel.MIXED -> teacherLevelMixed
        TeacherLevel.POSITIVE -> teacherLevelPositive
        TeacherLevel.VERY_POSITIVE -> teacherLevelVeryPositive
    }
}

/** `changes_card`: `было -> стало` per changed field of the latest change of this occurrence. */
@Composable
internal fun LessonChangeSection(change: ScheduleChange) {
    LessonSection(stringResource(Res.string.schedule_lesson_details_changes), Modifier.testTag(LessonDetailsTestTags.CHANGES)) {
        Column(Modifier.padding(top = ItmoTheme.spacing.related)) {
            change.detailLines().forEach { line ->
                Text(
                    line.asString(),
                    Modifier.padding(top = ItmoTheme.spacing.related),
                    color = ItmoTheme.colorScheme.onSurface,
                    style = ItmoTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/**
 * `link_fact` and `link_button`: what the reader has to type or know (MyITMO's meeting info, the password) as a fact
 * row with the camera icon, and the link itself only as `Открыть видеозвонок`.
 */
@Composable
internal fun LessonLinkBlock(lesson: LessonDetailsArgs, onLink: (String) -> Unit) {
    val password = lesson.zoomPassword?.let { stringResource(Res.string.schedule_lesson_details_link_password, it) }
    val info = listOfNotNull(lesson.zoomInfo, password).joinToString("\n")
    if (info.isNotBlank()) LinkFact(stringResource(Res.string.schedule_lesson_details_link), info)
    val url = lesson.zoomUrl ?: return
    ElevatedButton(
        onClick = { onLink(url) },
        modifier = Modifier
            .padding(start = ItmoTheme.spacing.screenMargin)
            .testTag(LessonDetailsTestTags.LINK),
    ) {
        Icon(painterResource(KitRes.drawable.ic_videocam), contentDescription = null, Modifier.size(ButtonIconSize))
        Spacer(Modifier.width(ItmoTheme.spacing.compact))
        Text(stringResource(Res.string.schedule_open_link))
    }
}

/** `item_sport_detail_fact.xml` with `ic_videocam`: the icon carries the category, [label] is only for TalkBack. */
@Composable
private fun LinkFact(label: String, value: String) {
    val style = ItmoTheme.typography.bodyMedium
    val description = stringResource(CoreRes.string.sport_detail_fact_description, label, value)
    val iconTop = with(LocalDensity.current) { ((style.lineHeight.toDp() - FactIconSize) / 2).coerceAtLeast(0.dp) }
    Row(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(vertical = ItmoTheme.spacing.compact),
    ) {
        Icon(
            painterResource(KitRes.drawable.ic_videocam),
            contentDescription = null,
            modifier = Modifier
                .padding(top = iconTop, end = ItmoTheme.spacing.compact)
                .size(FactIconSize),
            tint = ItmoTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, Modifier.weight(1f), color = ItmoTheme.colorScheme.onSurfaceVariant, style = style)
    }
}

/** `note_card`: the lesson's note from MyITMO. */
@Composable
internal fun LessonNoteSection(note: String) {
    LessonSection(stringResource(Res.string.schedule_lesson_note_title), Modifier.testTag(LessonDetailsTestTags.NOTE)) {
        Text(
            note,
            Modifier.padding(top = ItmoTheme.spacing.compact),
            color = ItmoTheme.colorScheme.onSurface,
            style = ItmoTheme.typography.bodyMedium,
        )
    }
}

/**
 * `friends_card`: absent without the opt-in; the loading indicator while loading; the error with `Повторить`; "nobody" for an
 * empty answer; otherwise the count in the heading and a row per friend that opens their profile.
 */
@Composable
internal fun LessonFriendsSection(state: LessonFriendsState, actions: LessonDetailsActions) {
    if (state == LessonFriendsState.Disabled) return
    val title = if (state is LessonFriendsState.Content && state.friends.isNotEmpty()) {
        stringResource(Res.string.schedule_lesson_friends_count, state.friends.size)
    } else {
        stringResource(Res.string.schedule_lesson_friends_title)
    }
    LessonSection(title, Modifier.testTag(LessonDetailsTestTags.FRIENDS)) {
        when (state) {
            LessonFriendsState.Disabled -> Unit
            LessonFriendsState.Loading -> ItmoLoadingIndicator(
                Modifier
                    .padding(top = ItmoTheme.spacing.content)
                    .size(ProgressSize)
                    .testTag(LessonDetailsTestTags.FRIENDS_PROGRESS),
            )
            is LessonFriendsState.Error -> {
                FriendsMessage(stringResource(state.error.textResource()))
                ProgressButton(
                    stringResource(CoreRes.string.common_retry),
                    onClick = actions.onRetryFriends,
                    style = ProgressButtonStyle.Text,
                )
            }
            is LessonFriendsState.Content -> if (state.friends.isEmpty()) {
                FriendsMessage(stringResource(Res.string.schedule_lesson_friends_none))
            } else {
                // The kit's row pads itself to the screen margin, so the list spans the sheet's full width.
                Column(Modifier.padding(top = ItmoTheme.spacing.related).bleed(ItmoTheme.spacing.screenMargin)) {
                    state.friends.forEach { friend ->
                        UserRow(
                            name = userDisplayName(friend.name, friend.isu).asString(),
                            pictureUrl = friend.pictureUrl,
                            modifier = Modifier.testTag(LessonDetailsTestTags.FRIEND),
                            subtitle = friend.primaryGroup()?.name?.takeIf(String::isNotBlank),
                            onClick = { actions.onProfile(friend.isu) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendsMessage(text: String) {
    Text(
        text,
        Modifier.padding(top = ItmoTheme.spacing.compact),
        color = ItmoTheme.colorScheme.onSurfaceVariant,
        style = ItmoTheme.typography.bodyMedium,
    )
}

/** A block under the header: 20 dp above, a divider, then the heading 16 dp under it. */
@Composable
private fun LessonSection(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
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

/** Widens the element by [margin] on both sides, past the padding of the column it sits in. */
private fun Modifier.bleed(margin: Dp): Modifier = layout { measurable, constraints ->
    val extra = margin.roundToPx() * 2
    val placeable = measurable.measure(
        constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra),
    )
    layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
}

private const val SEPARATOR = " \u00B7 "
private const val TIME_DASH = "\u2013"
private const val SECONDS_PER_MINUTE = 60

/** The 20 dp above each section's divider. */
private val SectionGap = 20.dp

/** `item_sport_detail_fact.xml`'s icon. */
private val FactIconSize = 20.dp

/** `Widget.Material3.Button`'s `iconSize`. */
private val ButtonIconSize = 18.dp

/** `friends_progress`'s 24 dp, now the kit's loading indicator for a section wait. */
private val ProgressSize = 24.dp
