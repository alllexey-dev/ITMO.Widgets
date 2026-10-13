package dev.alllexey.itmowidgets.feature.sport.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetClose
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetScaffold
import dev.alllexey.itmowidgets.designsystem.components.state.AppRefreshBox
import dev.alllexey.itmowidgets.designsystem.components.state.ContentState
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateAction
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateLoading
import dev.alllexey.itmowidgets.designsystem.kit.GateCard
import dev.alllexey.itmowidgets.designsystem.kit.IconTile
import dev.alllexey.itmowidgets.designsystem.kit.ItmoDetailScreen
import dev.alllexey.itmowidgets.designsystem.kit.ListGroup
import dev.alllexey.itmowidgets.designsystem.kit.ListGroupNote
import dev.alllexey.itmowidgets.designsystem.kit.ListRow
import dev.alllexey.itmowidgets.designsystem.kit.StatusPill
import dev.alllexey.itmowidgets.designsystem.kit.SwitchListRow
import dev.alllexey.itmowidgets.designsystem.kit.Tone
import dev.alllexey.itmowidgets.designsystem.kit.ink
import dev.alllexey.itmowidgets.designsystem.kit.tabular
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.sport.presentation.sections.SportSectionCard
import dev.alllexey.itmowidgets.feature.sport.presentation.sections.SportSectionsBody
import dev.alllexey.itmowidgets.feature.sport.presentation.sections.SportSectionsUiState
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportOption
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_add
import dev.alllexey.itmowidgets.shared.designsystem.ic_check
import dev.alllexey.itmowidgets.shared.designsystem.ic_event_busy
import dev.alllexey.itmowidgets.shared.designsystem.ic_event_repeat
import dev.alllexey.itmowidgets.shared.designsystem.ic_hourglass_top
import dev.alllexey.itmowidgets.shared.designsystem.ic_keep
import dev.alllexey.itmowidgets.shared.designsystem.ic_sprint
import dev.alllexey.itmowidgets.shared.feature.sport.SportStrings
import org.jetbrains.compose.resources.painterResource

/** Test tags of `Мои секции`. */
object SportSectionsTags {
    const val LIST = "sport_sections:list"
    const val EMPTY = "sport_sections:empty"
    const val PICKER = "sport_sections:picker"

    fun card(name: String) = "sport_sections:card:$name"
    fun repeat(name: String) = "sport_sections:repeat:$name"
}

/** What `Мои секции` asks of its host and ViewModel. */
data class SportSectionsActions(
    val onBack: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onOpenPicker: () -> Unit = {},
    val onClosePicker: () -> Unit = {},
    val onTogglePinned: (String) -> Unit = {},
    val onToggleRepeat: (String) -> Unit = {},
    val onOpenLesson: (Long) -> Unit = {},
    val onRowAction: (Long) -> Unit = {},
    val onOpenBookings: () -> Unit = {},
    val prompt: SportPromptActions = SportPromptActions(),
)

/**
 * `Мои секции` (DECISIONS.md, Спорт): each pinned section with its next lessons and `Очередь через неделю`, the queue
 * limit in the subtitle and a gate when it is spent; `+` pins another section.
 */
@Composable
fun SportSectionsScreen(
    state: SportSectionsUiState,
    actions: SportSectionsActions,
    modifier: Modifier = Modifier,
    snackbars: SnackbarHostState = remember { SnackbarHostState() },
) {
    ItmoDetailScreen(
        title = text(SportStrings.sport_sections_title),
        onBack = actions.onBack,
        backLabel = text(CoreStrings.common_back),
        modifier = modifier,
        subtitle = state.subtitle?.asString(),
        actions = {
            IconButton(onClick = actions.onOpenPicker) {
                Icon(painterResource(Res.drawable.ic_add), contentDescription = text(SportStrings.sport_sections_add))
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val body = state.body) {
                SportSectionsBody.Loading -> ContentStateLoading()
                is SportSectionsBody.Failed -> ContentState(
                    title = text(CoreStrings.common_load_error_title),
                    description = body.error.toUiText().asString(),
                    action = ContentStateAction(text(CoreStrings.common_retry), actions.onRetry),
                )
                SportSectionsBody.Empty -> ContentState(
                    title = text(SportStrings.sport_sections_empty_title),
                    modifier = Modifier.testTag(SportSectionsTags.EMPTY),
                    icon = painterResource(Res.drawable.ic_keep),
                    description = text(SportStrings.sport_sections_empty_text),
                    action = ContentStateAction(text(SportStrings.sport_sections_pick), actions.onOpenPicker),
                )
                is SportSectionsBody.Content -> AppRefreshBox(body.refreshing, actions.onRefresh, Modifier.fillMaxSize()) {
                    LazyColumn(
                        Modifier.fillMaxSize().testTag(SportSectionsTags.LIST),
                        contentPadding = PaddingValues(ItmoTheme.spacing.screenMargin),
                        verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.group),
                    ) {
                        state.limitGate?.let { gate ->
                            item(key = "gate") {
                                GateCard(
                                    gate.title.asString(),
                                    gate.reason.asString(),
                                    gate.fix.asString(),
                                    actions.onOpenBookings,
                                    icon = painterResource(Res.drawable.ic_hourglass_top),
                                )
                            }
                        }
                        items(body.sections, key = { it.name }) { SectionCard(it, actions) }
                        body.note?.let { note -> item(key = "note") { ListGroupNote(note.asString()) } }
                    }
                }
            }
            SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter))
        }
    }
    state.picker?.let { SportSectionsPicker(it, actions) }
    state.prompt?.let { SportPromptSheet(it, actions.prompt) }
}

/** A pinned section as one group: its header, the next lessons, the repeat switch and its dates. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SectionCard(card: SportSectionCard, actions: SportSectionsActions) {
    ListGroup(Modifier.testTag(SportSectionsTags.card(card.name))) {
        item {
            ListRow(
                title = card.title,
                subtitle = card.teacher,
                leading = { IconTile(painterResource(Res.drawable.ic_sprint), Tone.Primary) },
                below = { KindTag(card.kind?.asString(), card.kindTone, card.place?.asString()) },
                accessory = {
                    IconButton(onClick = { actions.onTogglePinned(card.name) }) {
                        Icon(
                            painterResource(Res.drawable.ic_keep),
                            contentDescription = text(SportStrings.sport_sections_unpin),
                            tint = ItmoTheme.colorScheme.primary,
                        )
                    }
                },
            )
        }
        card.lessons.forEach { lesson ->
            item {
                ListRow(
                    title = lesson.day.asString(),
                    leading = {
                        Text(lesson.time, Modifier.timeColumn(), style = ItmoTheme.typography.titleSmall.tabular(), fontWeight = FontWeight.SemiBold)
                    },
                    accessory = { RowAction(lesson.action, lesson.lessonId, actions.onRowAction) },
                    onClick = { actions.onOpenLesson(lesson.lessonId) },
                )
            }
        }
        card.repeat?.let { repeat ->
            item {
                SwitchListRow(
                    title = text(SportStrings.sport_repeat_title),
                    checked = repeat.on,
                    onCheckedChange = { actions.onToggleRepeat(card.name) },
                    modifier = Modifier.testTag(SportSectionsTags.repeat(card.name)),
                    subtitle = repeat.subtitle.asString(),
                    icon = painterResource(Res.drawable.ic_event_repeat),
                    enabled = repeat.enabled,
                )
            }
            if (repeat.dates.isNotEmpty() || repeat.clash != null) {
                item {
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = ItmoTheme.spacing.touchTarget)
                            .padding(horizontal = ItmoTheme.spacing.cardPadding, vertical = ItmoTheme.spacing.content),
                        verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact),
                    ) {
                        if (repeat.dates.isNotEmpty()) {
                            Text(text(SportStrings.sport_repeat_next), color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.labelLarge)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact),
                                verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact),
                            ) {
                                repeat.dates.forEach { StatusPill(it.asString()) }
                            }
                        }
                        repeat.clash?.let { clash ->
                            Row(horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact)) {
                                Icon(painterResource(Res.drawable.ic_event_busy), null, Modifier.size(SportSizes.icon), tint = Tone.Warning.ink())
                                Text(clash.asString(), style = ItmoTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SportSectionsPicker(options: List<SportOption>, actions: SportSectionsActions) {
    ModalBottomSheet(
        onDismissRequest = actions.onClosePicker,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null,
        containerColor = ItmoTheme.colorScheme.surfaceContainerLow,
    ) {
        SportSectionsPickerSheetContent(options, actions)
    }
}

/** Every section of the catalog; a tap pins or unpins it. */
@Composable
fun SportSectionsPickerSheetContent(options: List<SportOption>, actions: SportSectionsActions, modifier: Modifier = Modifier) {
    SheetScaffold(
        title = text(SportStrings.sport_sections_picker_title),
        modifier = modifier.testTag(SportSectionsTags.PICKER),
        close = SheetClose(text(CoreStrings.common_close), actions.onClosePicker),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = ItmoTheme.spacing.section)) {
            ListGroup {
                options.forEach { option ->
                    item {
                        ListRow(
                            title = option.label.asString(),
                            accessory = {
                                if (option.selected) Icon(painterResource(Res.drawable.ic_check), null, tint = ItmoTheme.colorScheme.primary)
                            },
                            onClick = { option.value?.let(actions.onTogglePinned) },
                        )
                    }
                }
            }
        }
    }
}
