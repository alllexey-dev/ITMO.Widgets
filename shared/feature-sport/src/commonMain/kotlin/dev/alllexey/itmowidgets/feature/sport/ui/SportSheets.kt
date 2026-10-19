package dev.alllexey.itmowidgets.feature.sport.ui

import dev.alllexey.itmowidgets.feature.sport.presentation.my.SportSectionToggleUi
import dev.alllexey.itmowidgets.shared.designsystem.ic_event_repeat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.designsystem.components.dialogs.ConfirmDialog
import dev.alllexey.itmowidgets.designsystem.components.dialogs.InfoDialog
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetClose
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetScaffold
import dev.alllexey.itmowidgets.designsystem.kit.ChipSpec
import dev.alllexey.itmowidgets.designsystem.kit.FilterChips
import dev.alllexey.itmowidgets.designsystem.kit.ListGroup
import dev.alllexey.itmowidgets.designsystem.kit.ListGroupNote
import dev.alllexey.itmowidgets.designsystem.kit.ListRow
import dev.alllexey.itmowidgets.designsystem.kit.SearchField
import dev.alllexey.itmowidgets.designsystem.kit.SectionHeader
import dev.alllexey.itmowidgets.designsystem.kit.SegmentedChoice
import dev.alllexey.itmowidgets.designsystem.kit.StatusPill
import dev.alllexey.itmowidgets.designsystem.kit.SwitchListRow
import dev.alllexey.itmowidgets.designsystem.kit.Tone
import dev.alllexey.itmowidgets.designsystem.kit.container
import dev.alllexey.itmowidgets.designsystem.kit.ink
import dev.alllexey.itmowidgets.designsystem.kit.onContainer
import dev.alllexey.itmowidgets.designsystem.components.avatar.Avatar
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingAction
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportCheckState
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportCommonDetailsArgs
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportLessonSheetPresenter
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportLessonSheetUi
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportPrompt
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportSeatsUi
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportShareTarget
import dev.alllexey.itmowidgets.feature.sport.presentation.my.SportCancelPrompt
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportDayPart
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportFiltersUi
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_check
import dev.alllexey.itmowidgets.shared.designsystem.ic_close
import dev.alllexey.itmowidgets.shared.designsystem.ic_event_busy
import dev.alllexey.itmowidgets.shared.designsystem.ic_group
import dev.alllexey.itmowidgets.shared.designsystem.ic_how_to_reg
import dev.alllexey.itmowidgets.shared.designsystem.ic_keep
import dev.alllexey.itmowidgets.shared.designsystem.ic_location_on
import dev.alllexey.itmowidgets.shared.designsystem.ic_person
import dev.alllexey.itmowidgets.shared.designsystem.ic_schedule
import dev.alllexey.itmowidgets.shared.designsystem.ic_share
import dev.alllexey.itmowidgets.shared.designsystem.ic_edit_note
import dev.alllexey.itmowidgets.shared.feature.sport.SportStrings
import org.jetbrains.compose.resources.painterResource

/** Test tags of the sport sheets. */
object SportSheetTags {
    const val FILTERS = "sport:filters"
    const val JOIN = "sport:join"
    const val JOIN_CONFIRM = "sport:join_confirm"
    const val LESSON = "sport:lesson"
    const val LESSON_ACTION = "sport:lesson_action"
    const val LESSON_CLASH = "sport:lesson_clash"
    const val SECTION = "sport:lesson_section"
}

/** What the more-filters sheet changes. */
data class SportFiltersActions(
    val onClose: () -> Unit = {},
    val onSection: (String) -> Unit = {},
    val onMoreSections: () -> Unit = {},
    val onSectionQuery: (String) -> Unit = {},
    val onBuilding: (String?) -> Unit = {},
    val onTeacher: (String?) -> Unit = {},
    val onDayPart: (SportDayPart) -> Unit = {},
    val onOnlyAvailable: (Boolean) -> Unit = {},
    val onOnlyFree: (Boolean) -> Unit = {},
    val onOnlyPinned: (Boolean) -> Unit = {},
    val onOnlyFriends: (Boolean) -> Unit = {},
    val onReset: () -> Unit = {},
)

/** What the queue's question answers. */
data class SportPromptActions(val onConfirm: (lateSign: Boolean) -> Unit = {}, val onDismiss: () -> Unit = {})

/** What the lesson sheet asks of its host: its one action, the share sheet, the maps, a person's card, closing. */
data class SportLessonSheetActions(
    val onAction: (SportBookingAction) -> Unit = {},
    val onShare: (SportShareTarget) -> Unit = {},
    val onMap: (String) -> Unit = {},
    val onProfile: (Int) -> Unit = {},
    val onClose: () -> Unit = {},
    /** `Сделать секцией` of a booked lesson, on or off; the sheet stays open. */
    val onSection: (Long) -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SportFiltersSheet(filters: SportFiltersUi, actions: SportFiltersActions) {
    ModalBottomSheet(
        onDismissRequest = actions.onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null,
        containerColor = ItmoTheme.colorScheme.surfaceContainerLow,
    ) {
        SportFiltersSheetContent(filters, actions)
    }
}

/** More filters (DECISIONS.md, Запись): sections, building, time, teacher, the switches; `Показать N занятий`. */
@Composable
fun SportFiltersSheetContent(filters: SportFiltersUi, actions: SportFiltersActions, modifier: Modifier = Modifier) {
    SheetScaffold(
        title = text(SportStrings.sport_filters_title),
        modifier = modifier.testTag(SportSheetTags.FILTERS),
        close = SheetClose(text(CoreStrings.common_close), actions.onClose),
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = ItmoTheme.spacing.section),
            verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
        ) {
            SectionHeader(text(SportStrings.sport_filter_type))
            filters.sectionQuery?.let { query ->
                SearchField(
                    query = query,
                    onQueryChange = actions.onSectionQuery,
                    placeholder = text(SportStrings.sport_filter_type_search),
                    clearLabel = text(SportStrings.sport_search_clear),
                )
            }
            val more = filters.moreSections?.asString()
            FilterChips(
                filters.sections.map { ChipSpec(it.label.asString(), it.selected) } + listOfNotNull(more?.let { ChipSpec(it, false) }),
                { index -> filters.sections.getOrNull(index)?.let { actions.onSection(it.value.orEmpty()) } ?: actions.onMoreSections() },
                wrap = true,
            )
            if (filters.buildings.size > 1) {
                SectionHeader(text(SportStrings.sport_filter_building))
                FilterChips(filters.buildings.map { ChipSpec(it.label.asString(), it.selected) }, { actions.onBuilding(filters.buildings[it].value) }, wrap = true)
            }
            if (filters.showDayPart) {
                SectionHeader(text(SportStrings.sport_filter_time))
                val parts = SportDayPart.entries
                SegmentedChoice(parts.map { text(it.labelKey) }, parts.indexOf(filters.dayPart), { actions.onDayPart(parts[it]) })
            }
            filters.teachers?.let { teachers ->
                SectionHeader(text(SportStrings.sport_filter_teacher))
                FilterChips(teachers.map { ChipSpec(it.label.asString(), it.selected) }, { actions.onTeacher(teachers[it].value) }, wrap = true)
            }
            ListGroup(Modifier.padding(top = ItmoTheme.spacing.compact)) {
                item { SwitchListRow(text(SportStrings.sport_filter_only_available), filters.onlyAvailable, actions.onOnlyAvailable, icon = painterResource(Res.drawable.ic_how_to_reg)) }
                item { SwitchListRow(text(SportStrings.sport_filter_only_free), filters.onlyFree, actions.onOnlyFree, icon = painterResource(Res.drawable.ic_check)) }
                filters.onlyPinned?.let { pinned ->
                    item { SwitchListRow(text(SportStrings.sport_filter_only_pinned), pinned, actions.onOnlyPinned, icon = painterResource(Res.drawable.ic_keep)) }
                }
                item { SwitchListRow(text(SportStrings.sport_filter_only_friends), filters.onlyFriends, actions.onOnlyFriends, icon = painterResource(Res.drawable.ic_group)) }
            }
            Row(Modifier.padding(top = ItmoTheme.spacing.related), horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact)) {
                OutlinedButton(onClick = actions.onReset) { Text(text(SportStrings.common_reset)) }
                Button(onClick = actions.onClose, modifier = Modifier.weight(1f)) { Text(filters.shownCount.asString()) }
            }
        }
    }
}

/** The queue's question: the join sheet, or an alert to leave or to say why it cannot. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SportPromptSheet(prompt: SportPrompt, actions: SportPromptActions) {
    when (prompt) {
        is SportPrompt.Join -> ModalBottomSheet(
            onDismissRequest = actions.onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            dragHandle = null,
            containerColor = ItmoTheme.colorScheme.surfaceContainerLow,
        ) { SportJoinSheetContent(prompt, actions) }
        is SportPrompt.Leave -> ConfirmDialog(
            title = null,
            confirmLabel = text(SportStrings.sport_action_leave_queue),
            dismissLabel = text(CoreStrings.common_back),
            onConfirm = { actions.onConfirm(false) },
            onDismiss = actions.onDismiss,
            text = prompt.message.asString(),
            destructive = true,
        )
        is SportPrompt.Info -> InfoDialog(null, prompt.message.asString(), text(SportStrings.common_ok), actions.onDismiss)
    }
}

/**
 * `Встать в очередь?` (DECISIONS.md, Очередь): what and when, the three steps, the late-sign switch of a real lesson,
 * the queues left, `Встать в очередь` and `Не сейчас`.
 */
@Composable
fun SportJoinSheetContent(prompt: SportPrompt.Join, actions: SportPromptActions, modifier: Modifier = Modifier) {
    var lateSign by rememberSaveable(prompt.lessonId) { mutableStateOf(false) }
    SheetScaffold(
        title = text(SportStrings.sport_join_title),
        modifier = modifier.testTag(SportSheetTags.JOIN),
        close = SheetClose(text(CoreStrings.common_close), actions.onDismiss),
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = ItmoTheme.spacing.section),
            verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
        ) {
            Column {
                Text(prompt.what.asString(), style = ItmoTheme.typography.titleMedium)
                Text(prompt.whenText.asString(), color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodyLarge)
            }
            prompt.steps.forEachIndexed { index, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content)) {
                    Box(
                        Modifier.size(SportSizes.checkIcon).background(ItmoTheme.colorScheme.secondaryContainer, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${index + 1}", color = ItmoTheme.colorScheme.onSecondaryContainer, style = ItmoTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                    Text(step.asString(), Modifier.weight(1f), style = ItmoTheme.typography.bodyLarge)
                }
            }
            if (prompt.lateSign) {
                ListGroup {
                    item {
                        SwitchListRow(
                            title = text(SportStrings.sport_join_late),
                            checked = lateSign,
                            onCheckedChange = { lateSign = it },
                            subtitle = prompt.lateSignHint?.asString(),
                            icon = painterResource(Res.drawable.ic_schedule),
                        )
                    }
                }
            }
            prompt.limit?.let { ListGroupNote(it.asString()) }
            Button(onClick = { actions.onConfirm(lateSign) }, modifier = Modifier.fillMaxWidth().testTag(SportSheetTags.JOIN_CONFIRM)) {
                Text(text(SportStrings.sport_action_queue))
            }
            TextButton(onClick = actions.onDismiss, modifier = Modifier.fillMaxWidth()) { Text(text(SportStrings.sport_join_later)) }
        }
    }
}

@Composable
internal fun SportCancelDialog(prompt: SportCancelPrompt, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = null,
        confirmLabel = prompt.confirm.asString(),
        dismissLabel = text(CoreStrings.common_back),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        text = prompt.message.asString(),
        destructive = true,
    )
}

/**
 * The sheet's one booking action: sent at most once. [submit] flips synchronously, so a second tap before the next
 * frame finds it taken; the host saves [submitted] and passes it back after recreation, so a recreated sheet never
 * sends the action again.
 */
@Stable
class SportDetailsSubmission(submitted: Boolean = false) {
    var submitted: Boolean by mutableStateOf(submitted)
        private set

    /** True only for the first call: the caller sends the action then. */
    fun submit(): Boolean {
        if (submitted) return false
        submitted = true
        return true
    }
}

/**
 * The lesson sheet of [item] as it is when it shows: [actionsEnabled] false where it only shows, [busy] while the
 * screen behind it runs an action for the lesson; the action is sent once ([submission]); [teacherLevel] once known.
 */
@Composable
fun SportLessonSheet(
    item: SportCommonDetailsArgs,
    time: AcademicTimeProvider,
    submission: SportDetailsSubmission,
    actions: SportLessonSheetActions,
    modifier: Modifier = Modifier,
    actionsEnabled: Boolean = true,
    busy: Boolean = false,
    section: SportSectionToggleUi? = null,
    teacherLevel: TeacherLevel? = null,
) {
    val submitted = submission.submitted
    val sheet = remember(item, submitted, busy, actionsEnabled, teacherLevel) {
        SportLessonSheetPresenter.present(item, time.now(), time.timeZone, actionsEnabled, busy, submitted, teacherLevel)
    }
    SportLessonSheetContent(sheet, actions.copy(onAction = { if (submission.submit()) actions.onAction(it) }), modifier, section)
}

/**
 * The sport lesson sheet (DECISIONS.md, Спорт): what, the places, the status, an orange clash that does not block,
 * one main action and its note, the conditions; then the teacher, the place, the comment and the friends.
 */
@Composable
fun SportLessonSheetContent(
    sheet: SportLessonSheetUi,
    actions: SportLessonSheetActions,
    modifier: Modifier = Modifier,
    section: SportSectionToggleUi? = null,
) {
    SheetScaffold(
        title = text(SportStrings.sport_lesson_sheet_title),
        modifier = modifier.testTag(SportSheetTags.LESSON),
        close = SheetClose(text(CoreStrings.common_close), actions.onClose),
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = ItmoTheme.spacing.section),
            verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.related)) {
                KindTag(sheet.kind?.asString(), sheet.kindTone, null)
                Text(sheet.section, style = ItmoTheme.typography.headlineSmall)
                Text(sheet.date.asString(), color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodyLarge)
            }
            sheet.seats?.let { Seats(it, sheet.status?.takeIf { status -> status.booked }?.text?.asString()) }
            if (sheet.seats == null) sheet.status?.let { StatusPill(it.text.asString(), tone = if (it.booked) Tone.Success else Tone.Warning) }
            sheet.status?.takeIf { !it.booked && sheet.seats != null }?.let { StatusPill(it.text.asString(), tone = Tone.Warning) }
            sheet.clash?.let { ClashWarning(it.asString()) }
            sheet.action?.let { button ->
                if (button.destructive) {
                    OutlinedButton(
                        onClick = { actions.onAction(button.action) },
                        modifier = Modifier.fillMaxWidth().testTag(SportSheetTags.LESSON_ACTION),
                        enabled = button.enabled,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ItmoTheme.colorScheme.error),
                    ) { Text(button.label.asString()) }
                } else {
                    Button(onClick = { actions.onAction(button.action) }, modifier = Modifier.fillMaxWidth().testTag(SportSheetTags.LESSON_ACTION), enabled = button.enabled) {
                        Text(button.label.asString())
                    }
                }
            }
            sheet.actionNote?.let { ListGroupNote(it.asString()) }
            section?.let { toggle ->
                ListGroup {
                    item {
                        SwitchListRow(
                            title = toggle.label.asString(),
                            checked = toggle.on,
                            onCheckedChange = { actions.onSection(toggle.lessonId) },
                            modifier = Modifier.testTag(SportSheetTags.SECTION),
                            subtitle = toggle.detail?.asString(),
                            icon = painterResource(Res.drawable.ic_event_repeat),
                            enabled = toggle.enabled,
                        )
                    }
                }
            }
            if (sheet.conditions.isNotEmpty()) {
                SectionHeader(text(SportStrings.sport_sheet_conditions))
                sheet.conditions.forEach { Check(it.state, it.text.asString()) }
            }
            SectionHeader(text(SportStrings.sport_sheet_where))
            ListGroup {
                sheet.teacher?.let { teacher ->
                    item {
                        ListRow(
                            title = teacher.name,
                            subtitle = teacher.tone.asString(),
                            icon = painterResource(Res.drawable.ic_person),
                            chevron = teacher.isu != null,
                            onClick = teacher.isu?.let { isu -> { actions.onProfile(isu) } },
                        )
                    }
                }
                sheet.place?.let { place ->
                    item {
                        ListRow(
                            title = place.title,
                            subtitle = place.subtitle,
                            icon = painterResource(Res.drawable.ic_location_on),
                            chevron = place.mapAddress != null,
                            onClick = place.mapAddress?.let { address -> { actions.onMap(address) } },
                        )
                    }
                }
                sheet.comment?.let { comment ->
                    item { ListRow(title = comment, subtitle = text(SportStrings.sport_sheet_comment), icon = painterResource(Res.drawable.ic_edit_note)) }
                }
                sheet.friends.forEach { friend ->
                    item {
                        ListRow(
                            title = friend.name,
                            subtitle = friend.status?.asString(),
                            leading = { Avatar(friend.name, friend.pictureUrl, size = ItmoTheme.spacing.section, initials = friend.initials) },
                            chevron = true,
                            onClick = { actions.onProfile(friend.isu) },
                        )
                    }
                }
                sheet.share?.let { target ->
                    item { ListRow(title = text(SportStrings.sport_sheet_share), icon = painterResource(Res.drawable.ic_share), onClick = { actions.onShare(target) }) }
                }
            }
        }
    }
}

/** The free places large and as a bar of seats, taken ones quiet. */
@Composable
private fun Seats(seats: SportSeatsUi, booked: String?) {
    val tone = seats.occupancy.tone.tone()
    Column(verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content)) {
            Text("${seats.free}", color = tone.ink(), style = ItmoTheme.typography.displayMedium)
            Column(Modifier.weight(1f)) {
                Text(seats.label.asString(), style = ItmoTheme.typography.titleMedium)
                Text(seats.detail.asString(), color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodyMedium)
            }
            booked?.let { StatusPill(it, tone = Tone.Success, icon = painterResource(Res.drawable.ic_check)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.related)) {
            val total = seats.occupancy.total.coerceAtMost(MAX_SEATS)
            val taken = (seats.occupancy.share * total).toInt()
            repeat(total) { index ->
                Box(
                    Modifier.weight(1f).height(SportSizes.seatBar).clip(ItmoTheme.shapes.extraSmall)
                        .background(if (index < taken) ItmoTheme.colorScheme.outlineVariant else tone.ink()),
                )
            }
        }
    }
}

/** The clash with a lesson: an orange line that wraps, never a block. */
@Composable
private fun ClashWarning(label: String) {
    Row(
        Modifier.fillMaxWidth().testTag(SportSheetTags.LESSON_CLASH)
            .background(Tone.Warning.container(), ItmoTheme.shapes.large)
            .padding(horizontal = ItmoTheme.spacing.content, vertical = ItmoTheme.spacing.compact),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact),
    ) {
        Icon(painterResource(Res.drawable.ic_event_busy), null, Modifier.size(SportSizes.icon), tint = Tone.Warning.onContainer())
        Text(label, color = Tone.Warning.onContainer(), style = ItmoTheme.typography.labelLarge)
    }
}

@Composable
private fun Check(state: SportCheckState, label: String) {
    val tone = when (state) {
        SportCheckState.OK -> Tone.Success
        SportCheckState.WAIT -> Tone.Neutral
        SportCheckState.NO -> Tone.Error
    }
    val icon = when (state) {
        SportCheckState.OK -> Res.drawable.ic_check
        SportCheckState.WAIT -> Res.drawable.ic_schedule
        SportCheckState.NO -> Res.drawable.ic_close
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content)) {
        Box(Modifier.size(SportSizes.checkIcon).background(tone.container(), CircleShape), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), null, Modifier.size(SportSizes.smallIcon), tint = tone.onContainer())
        }
        Text(
            label,
            color = when (state) {
                SportCheckState.OK -> ItmoTheme.colorScheme.onSurface
                SportCheckState.WAIT -> ItmoTheme.colorScheme.onSurfaceVariant
                SportCheckState.NO -> ItmoTheme.colorScheme.error
            },
            style = ItmoTheme.typography.bodyLarge,
        )
    }
}

/** A hall of more seats draws them in one bar per seat up to this many. */
private const val MAX_SEATS = 30
