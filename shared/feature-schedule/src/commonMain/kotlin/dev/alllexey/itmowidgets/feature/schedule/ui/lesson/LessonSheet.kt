package dev.alllexey.itmowidgets.feature.schedule.ui.lesson

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.designsystem.components.avatar.Avatar
import dev.alllexey.itmowidgets.designsystem.components.buttons.tonalButtonColors
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetClose
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetScaffold
import dev.alllexey.itmowidgets.designsystem.kit.ListGroup
import dev.alllexey.itmowidgets.designsystem.kit.ListGroupNote
import dev.alllexey.itmowidgets.designsystem.kit.ListRow
import dev.alllexey.itmowidgets.designsystem.kit.SectionHeader
import dev.alllexey.itmowidgets.designsystem.kit.StatusPill
import dev.alllexey.itmowidgets.designsystem.kit.Tone
import dev.alllexey.itmowidgets.designsystem.kit.toPillSpec
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.schedule.presentation.lesson.LessonAction
import dev.alllexey.itmowidgets.feature.schedule.presentation.lesson.LessonActionKind
import dev.alllexey.itmowidgets.feature.schedule.presentation.lesson.LessonSheetChanges
import dev.alllexey.itmowidgets.feature.schedule.presentation.lesson.LessonSheetUiState
import dev.alllexey.itmowidgets.feature.schedule.presentation.lesson.LessonSheetViewModel
import dev.alllexey.itmowidgets.shared.compose.strings.common_close
import dev.alllexey.itmowidgets.shared.compose.strings.schedule_lesson_details_flow
import dev.alllexey.itmowidgets.shared.designsystem.ic_arrow_forward
import dev.alllexey.itmowidgets.shared.designsystem.ic_edit_note
import dev.alllexey.itmowidgets.shared.designsystem.ic_group
import dev.alllexey.itmowidgets.shared.designsystem.ic_link
import dev.alllexey.itmowidgets.shared.designsystem.ic_menu_book
import dev.alllexey.itmowidgets.shared.designsystem.ic_sprint
import dev.alllexey.itmowidgets.shared.designsystem.ic_videocam
import dev.alllexey.itmowidgets.shared.feature.schedule.Res
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_sheet_changes
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_sheet_friends_title
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_sheet_note
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_sheet_online
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_sheet_password
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.compose.strings.Res as CoreRes
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/** Test tags of [LessonSheetContent]. */
object LessonSheetTestTags {
    const val ROOM = "lesson_sheet:room"
    const val TEACHER = "lesson_sheet:teacher"
    const val SPORT = "lesson_sheet:sport"
    const val FRIENDS = "lesson_sheet:friends"

    fun action(kind: LessonActionKind) = "lesson_sheet:action:${kind.name.lowercase()}"

    fun friend(isu: Int) = "lesson_sheet:friend:$isu"
}

/** Where the lesson sheet leads; the host closes the sheet before a screen opens in its place. */
data class LessonSheetActions(
    val onVideo: (url: String) -> Unit = {},
    val onRoute: (AppRoute) -> Unit = {},
    val onProfile: (isu: Int) -> Unit = {},
    val onSport: () -> Unit = {},
    val onClose: () -> Unit = {},
)

/** The lesson sheet with its ViewModel, one per opening. */
@Composable
fun LessonSheetRoute(viewModel: LessonSheetViewModel, actions: LessonSheetActions) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LessonSheetContent(state, actions)
}

/**
 * `Лист пары` (docs/features/schedule.md): the lesson's type and day with its status, the subject, the room big with
 * the time and the building's address (`Онлайн` for an online lesson), the relevant actions (Видео, Друзья, Ссылки,
 * Предмет), then the change `было → стало`, the note, the teacher with the tone of the reviews in words, the flow, the
 * sport queue as a row to Спорт and the friends on the lesson. Stateless.
 */
@Composable
fun LessonSheetContent(state: LessonSheetUiState, actions: LessonSheetActions, modifier: Modifier = Modifier) {
    val friends = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    SheetScaffold(
        title = state.over.asString(),
        modifier = modifier,
        close = SheetClose(stringResource(CoreRes.string.common_close), actions.onClose),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ItmoTheme.spacing.screenMargin)
                .padding(bottom = ItmoTheme.spacing.group),
            verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
        ) {
            Hero(state)
            if (state.actions.isNotEmpty()) {
                ActionTiles(state.actions) { action ->
                    when (action.kind) {
                        LessonActionKind.VIDEO -> action.url?.let(actions.onVideo)
                        LessonActionKind.FRIENDS -> scope.launch { friends.bringIntoView() }
                        LessonActionKind.LINKS, LessonActionKind.SUBJECT -> action.route?.let(actions.onRoute)
                    }
                }
            }
            state.changes?.let { Changes(it) }
            Details(state, actions)
            if (state.friends.isNotEmpty()) {
                Column(Modifier.bringIntoViewRequester(friends).testTag(LessonSheetTestTags.FRIENDS)) {
                    SectionHeader(stringResource(Res.string.schedule_sheet_friends_title), count = "${state.friends.size}")
                    ListGroup {
                        state.friends.forEach { friend ->
                            item {
                                ListRow(
                                    friend.name,
                                    Modifier.testTag(LessonSheetTestTags.friend(friend.isu)),
                                    subtitle = friend.groups.firstOrNull()?.name,
                                    leading = { Avatar(friend.name, friend.pictureUrl, initials = friend.initials) },
                                    chevron = true,
                                    onClick = { actions.onProfile(friend.isu) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The status, the subject, then the big room (or `Онлайн`) with the time and the address beside it or under it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Hero(state: LessonSheetUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.related)) {
        state.status?.let { StatusPill(it.toPillSpec()) }
        Text(state.subject, style = ItmoTheme.emphasizedTypography.headlineSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
            verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.related),
        ) {
            val big = if (state.online) stringResource(Res.string.schedule_sheet_online) else state.room?.asString()
            if (big != null) {
                Text(
                    big,
                    Modifier.align(Alignment.Bottom).testTag(LessonSheetTestTags.ROOM),
                    color = ItmoTheme.colorScheme.primary,
                    style = ItmoTheme.emphasizedTypography.displayMedium,
                )
            }
            Column(Modifier.align(Alignment.Bottom).padding(bottom = ItmoTheme.spacing.compact)) {
                Text(state.time.asString(), style = ItmoTheme.typography.titleSmall)
                state.address?.let {
                    Text(it, color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** Equal tiles, the first filled, the rest in the kit's tonal colours; only the actions the lesson has. */
@Composable
private fun ActionTiles(actions: List<LessonAction>, onAction: (LessonAction) -> Unit) {
    val tonal = tonalButtonColors()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.related)) {
        actions.forEachIndexed { index, action ->
            val first = index == 0
            Column(
                Modifier
                    .weight(1f)
                    .clip(ItmoTheme.shapes.large)
                    .clickable(role = Role.Button) { onAction(action) }
                    .testTag(LessonSheetTestTags.action(action.kind)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth().heightIn(min = ItmoTheme.spacing.touchTarget),
                    shape = ItmoTheme.shapes.large,
                    color = if (first) ItmoTheme.colorScheme.primary else tonal.containerColor,
                    contentColor = if (first) ItmoTheme.colorScheme.onPrimary else tonal.contentColor,
                ) {
                    Box(Modifier.padding(ItmoTheme.spacing.content), contentAlignment = Alignment.Center) {
                        Icon(action.kind.icon(), contentDescription = null)
                    }
                }
                Text(action.label.asString(), style = ItmoTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}

@Composable
private fun LessonActionKind.icon(): Painter = painterResource(
    when (this) {
        LessonActionKind.VIDEO -> KitRes.drawable.ic_videocam
        LessonActionKind.FRIENDS -> KitRes.drawable.ic_group
        LessonActionKind.LINKS -> KitRes.drawable.ic_link
        LessonActionKind.SUBJECT -> KitRes.drawable.ic_menu_book
    },
)

/** `было` struck through, then `стало` in the warning colour, per changed field; when it changed under them. */
@Composable
private fun Changes(changes: LessonSheetChanges) {
    Column {
        SectionHeader(stringResource(Res.string.schedule_sheet_changes))
        ListGroup {
            changes.summary?.let { summary -> item { ListRow(summary.asString()) } }
            changes.lines.forEach { line ->
                item {
                    Column(Modifier.fillMaxWidth().padding(ItmoTheme.spacing.cardPadding)) {
                        Text(line.field.asString(), color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodyMedium)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact),
                        ) {
                            Text(
                                line.from.asString(),
                                Modifier.weight(1f, fill = false),
                                color = ItmoTheme.colorScheme.onSurfaceVariant,
                                style = ItmoTheme.typography.bodyLarge,
                                textDecoration = TextDecoration.LineThrough,
                            )
                            Icon(
                                painterResource(KitRes.drawable.ic_arrow_forward),
                                contentDescription = null,
                                tint = ItmoTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                line.to.asString(),
                                Modifier.weight(1f, fill = false),
                                color = ItmoTheme.extendedColors.warning,
                                style = ItmoTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
        ListGroupNote(changes.note.asString())
    }
}

@Composable
private fun Details(state: LessonSheetUiState, actions: LessonSheetActions) {
    val hasRows = state.note != null || state.password != null || state.teacher != null || state.flow != null || state.sport != null
    if (!hasRows) return
    ListGroup {
        state.note?.let { note ->
            item {
                ListRow(note, subtitle = stringResource(Res.string.schedule_sheet_note), icon = painterResource(KitRes.drawable.ic_edit_note))
            }
        }
        state.password?.let { password ->
            item { ListRow(stringResource(Res.string.schedule_sheet_password, password), icon = painterResource(KitRes.drawable.ic_videocam)) }
        }
        state.teacher?.let { teacher ->
            item {
                ListRow(
                    teacher.name,
                    Modifier.testTag(LessonSheetTestTags.TEACHER),
                    subtitle = teacher.tone.asString(),
                    leading = { Avatar(teacher.name, null) },
                    chevron = teacher.isu != null,
                    onClick = teacher.isu?.let { isu -> { actions.onProfile(isu) } },
                )
            }
        }
        state.flow?.let { flow ->
            item {
                ListRow(flow, subtitle = stringResource(CoreRes.string.schedule_lesson_details_flow), icon = painterResource(KitRes.drawable.ic_group))
            }
        }
        state.sport?.let { sport ->
            item {
                ListRow(
                    sport.title.asString(),
                    Modifier.testTag(LessonSheetTestTags.SPORT),
                    subtitle = sport.text.asString(),
                    icon = painterResource(KitRes.drawable.ic_sprint),
                    iconTone = Tone.Warning,
                    chevron = true,
                    onClick = actions.onSport,
                )
            }
        }
    }
}
