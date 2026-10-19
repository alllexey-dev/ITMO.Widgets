package dev.alllexey.itmowidgets.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.bell.BellItem
import dev.alllexey.itmowidgets.core.bell.BellKind
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.designsystem.components.avatar.Avatar
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetClose
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetScaffold
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateLoading
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateSize
import dev.alllexey.itmowidgets.designsystem.kit.IconTile
import dev.alllexey.itmowidgets.designsystem.kit.ListGroup
import dev.alllexey.itmowidgets.designsystem.kit.ListGroupNote
import dev.alllexey.itmowidgets.designsystem.kit.ListRow
import dev.alllexey.itmowidgets.designsystem.kit.PersonAvatarSize
import dev.alllexey.itmowidgets.designsystem.kit.SectionHeader
import dev.alllexey.itmowidgets.designsystem.kit.TextTile
import dev.alllexey.itmowidgets.designsystem.kit.Tone
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.home.presentation.BellRow
import dev.alllexey.itmowidgets.feature.home.presentation.BellUiState
import dev.alllexey.itmowidgets.shared.compose.strings.Res
import dev.alllexey.itmowidgets.shared.compose.strings.bell_accept
import dev.alllexey.itmowidgets.shared.compose.strings.bell_decline
import dev.alllexey.itmowidgets.shared.compose.strings.bell_earlier
import dev.alllexey.itmowidgets.shared.compose.strings.bell_empty_text
import dev.alllexey.itmowidgets.shared.compose.strings.bell_new
import dev.alllexey.itmowidgets.shared.compose.strings.bell_nothing_new
import dev.alllexey.itmowidgets.shared.compose.strings.bell_settings
import dev.alllexey.itmowidgets.shared.compose.strings.common_close
import dev.alllexey.itmowidgets.shared.compose.strings.today_notifications
import dev.alllexey.itmowidgets.shared.designsystem.ic_check_circle
import dev.alllexey.itmowidgets.shared.designsystem.ic_edit_calendar
import dev.alllexey.itmowidgets.shared.designsystem.ic_menu_book
import dev.alllexey.itmowidgets.shared.designsystem.ic_settings
import dev.alllexey.itmowidgets.shared.designsystem.ic_sprint
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/** Test tags of [BellSheetContent]. */
object BellTestTags {
    const val LOADING = "bell:loading"
    const val NOTHING_NEW = "bell:nothing_new"
    const val SETTINGS = "bell:settings"

    fun row(id: String) = "bell:row:$id"

    fun accept(id: String) = "bell:accept:$id"

    fun decline(id: String) = "bell:decline:$id"
}

/** What the bell asks its host to do. */
data class BellActions(
    val onOpen: (BellItem) -> Unit = {},
    val onAnswer: (BellItem, accept: Boolean) -> Unit = { _, _ -> },
    val onSettings: () -> Unit = {},
    val onClose: () -> Unit = {},
)

/**
 * The bell's sheet over Сегодня: the events new at this opening under `Новое` (a dot and a bold title), the rest under
 * `Раньше`, or `Ничего нового` over them; scores with their values, schedule changes `было` struck through over
 * `стало`, queue results and friend requests with `Принять` and `Отклонить`. Every row leads to its event's home. The
 * notification settings close the list. Stateless; [BellRoute] feeds it.
 */
@Composable
fun BellSheetContent(state: BellUiState, actions: BellActions, modifier: Modifier = Modifier) {
    SheetScaffold(
        title = stringResource(Res.string.today_notifications),
        modifier = modifier,
        close = SheetClose(stringResource(Res.string.common_close), actions.onClose),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ItmoTheme.spacing.screenMargin)
                .padding(bottom = ItmoTheme.spacing.group),
            verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
        ) {
            when {
                state.loading -> ContentStateLoading(Modifier.testTag(BellTestTags.LOADING), ContentStateSize.Compact)
                state.fresh.isNotEmpty() -> {
                    SectionHeader(stringResource(Res.string.bell_new), count = state.fresh.size.toString())
                    Events(state.fresh, actions)
                    if (state.earlier.isNotEmpty()) {
                        SectionHeader(stringResource(Res.string.bell_earlier))
                        Events(state.earlier, actions)
                    }
                }
                else -> {
                    NothingNew()
                    if (state.earlier.isNotEmpty()) Events(state.earlier, actions)
                    if (state.empty) ListGroupNote(stringResource(Res.string.bell_empty_text))
                }
            }
            ListGroup(Modifier.padding(top = ItmoTheme.spacing.compact)) {
                item {
                    ListRow(
                        stringResource(Res.string.bell_settings),
                        Modifier.testTag(BellTestTags.SETTINGS),
                        icon = painterResource(KitRes.drawable.ic_settings),
                        chevron = true,
                        onClick = actions.onSettings,
                    )
                }
            }
        }
    }
}

@Composable
private fun NothingNew() {
    Row(
        Modifier.padding(start = ItmoTheme.spacing.related).testTag(BellTestTags.NOTHING_NEW),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact),
    ) {
        Icon(
            painterResource(KitRes.drawable.ic_check_circle),
            contentDescription = null,
            Modifier.size(NothingNewIconSize),
            tint = ItmoTheme.extendedColors.success,
        )
        Text(
            stringResource(Res.string.bell_nothing_new),
            color = ItmoTheme.colorScheme.onSurfaceVariant,
            style = ItmoTheme.typography.titleSmall,
        )
    }
}

@Composable
private fun Events(rows: List<BellRow>, actions: BellActions) {
    ListGroup { rows.forEach { row -> item { Event(row, actions) } } }
}

@Composable
private fun Event(row: BellRow, actions: BellActions) {
    val item = row.item
    val muted = ItmoTheme.colorScheme.onSurfaceVariant
    val newLabel = stringResource(Res.string.bell_new)
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (item.target != null) Modifier.clickable { actions.onOpen(item) } else Modifier)
            .semantics { if (row.new) stateDescription = newLabel }
            .padding(horizontal = ItmoTheme.spacing.cardPadding, vertical = ItmoTheme.spacing.content)
            .testTag(BellTestTags.row(item.id)),
        horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
        verticalAlignment = Alignment.Top,
    ) {
        Lead(item)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(LineGap)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact)) {
                Text(
                    item.title.asString(),
                    Modifier.weight(1f),
                    style = ItmoTheme.typography.bodyLarge,
                    fontWeight = if (row.new) FontWeight.SemiBold else null,
                )
                row.time?.let {
                    Text(it.asString(), Modifier.padding(top = LineGap), color = muted, style = ItmoTheme.typography.labelMedium)
                }
                if (row.new) {
                    Box(
                        Modifier.padding(top = ItmoTheme.spacing.compact)
                            .size(DotSize)
                            .background(ItmoTheme.colorScheme.primary, CircleShape),
                    )
                }
            }
            item.text?.let { Text(it.asString(), color = muted, style = ItmoTheme.typography.bodyMedium) }
            item.was?.let {
                Text(it.asString(), color = muted, style = ItmoTheme.typography.bodyMedium, textDecoration = TextDecoration.LineThrough)
            }
            item.now?.let {
                Text(
                    it.asString(),
                    color = ItmoTheme.extendedColors.warning,
                    style = ItmoTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
            if (item.request != null) Answer(row, actions)
        }
    }
}

@Composable
private fun Answer(row: BellRow, actions: BellActions) {
    val item = row.item
    Row(
        Modifier.padding(top = ItmoTheme.spacing.related),
        horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact),
    ) {
        Button(
            onClick = { actions.onAnswer(item, true) },
            modifier = Modifier.testTag(BellTestTags.accept(item.id)),
            enabled = !row.busy,
            contentPadding = ButtonDefaults.SmallContentPadding,
        ) { Text(stringResource(Res.string.bell_accept)) }
        OutlinedButton(
            onClick = { actions.onAnswer(item, false) },
            modifier = Modifier.testTag(BellTestTags.decline(item.id)),
            enabled = !row.busy,
            contentPadding = ButtonDefaults.SmallContentPadding,
        ) { Text(stringResource(Res.string.bell_decline)) }
    }
}

/** A mark's change of points, the person of a request, or the event's icon in its tone. */
@Composable
private fun Lead(item: BellItem) {
    when (item.kind) {
        BellKind.MARK -> {
            val lead = item.lead
            if (lead != null) TextTile(lead, Tone.Success) else IconTile(painterResource(KitRes.drawable.ic_menu_book), Tone.Success)
        }
        BellKind.SCHEDULE_CHANGE -> IconTile(painterResource(KitRes.drawable.ic_edit_calendar), Tone.Warning)
        BellKind.SPORT -> IconTile(painterResource(KitRes.drawable.ic_sprint), if (item.failed) Tone.Error else Tone.Success)
        BellKind.FRIEND_REQUEST -> Avatar(item.lead, item.pictureUrl, size = PersonAvatarSize, initials = item.initials)
    }
}

private val DotSize = 8.dp
private val NothingNewIconSize = 20.dp
private val LineGap = 2.dp
