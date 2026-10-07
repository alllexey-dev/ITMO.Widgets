package dev.alllexey.itmowidgets.feature.resources.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.alllexey.itmowidgets.core.resources.GoogleSheetUrl
import dev.alllexey.itmowidgets.core.resources.LinkVisibility
import dev.alllexey.itmowidgets.core.resources.SubjectLink
import dev.alllexey.itmowidgets.core.resources.SubjectLinkStatus
import dev.alllexey.itmowidgets.core.resources.displayTitle
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.designsystem.components.dialogs.ConfirmDialog
import dev.alllexey.itmowidgets.designsystem.components.rows.Vote
import dev.alllexey.itmowidgets.designsystem.components.rows.VoteLabels
import dev.alllexey.itmowidgets.designsystem.components.rows.VotePill
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetHandle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.resources.presentation.LinkEvent
import dev.alllexey.itmowidgets.feature.resources.presentation.SubjectLinksUiState
import dev.alllexey.itmowidgets.feature.resources.presentation.SubjectLinksViewModel
import dev.alllexey.itmowidgets.shared.core.common_cancel
import dev.alllexey.itmowidgets.shared.core.links_score
import dev.alllexey.itmowidgets.shared.core.links_vote_down
import dev.alllexey.itmowidgets.shared.core.links_vote_up
import dev.alllexey.itmowidgets.shared.designsystem.ic_content_copy
import dev.alllexey.itmowidgets.shared.designsystem.ic_delete
import dev.alllexey.itmowidgets.shared.designsystem.ic_edit
import dev.alllexey.itmowidgets.shared.designsystem.ic_flag
import dev.alllexey.itmowidgets.shared.designsystem.ic_keep
import dev.alllexey.itmowidgets.shared.designsystem.ic_keep_off
import dev.alllexey.itmowidgets.shared.designsystem.ic_open_in_new
import dev.alllexey.itmowidgets.shared.designsystem.ic_person
import dev.alllexey.itmowidgets.shared.designsystem.ic_table
import dev.alllexey.itmowidgets.shared.feature.resources.Res
import dev.alllexey.itmowidgets.shared.feature.resources.links_author
import dev.alllexey.itmowidgets.shared.feature.resources.links_copy
import dev.alllexey.itmowidgets.shared.feature.resources.links_delete
import dev.alllexey.itmowidgets.shared.feature.resources.links_delete_confirm
import dev.alllexey.itmowidgets.shared.feature.resources.links_edit
import dev.alllexey.itmowidgets.shared.feature.resources.links_open
import dev.alllexey.itmowidgets.shared.feature.resources.links_pin
import dev.alllexey.itmowidgets.shared.feature.resources.links_rejected_reason
import dev.alllexey.itmowidgets.shared.feature.resources.links_report
import dev.alllexey.itmowidgets.shared.feature.resources.links_unpin
import dev.alllexey.itmowidgets.shared.feature.resources.sheet_scores_action
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/** The rows of the link actions sheet, in display order. */
enum class LinkAction { OPEN, AUTHOR, COPY, SCORES, PIN, EDIT, DELETE, REPORT }

/**
 * What the link actions sheet shows for one link of [SubjectLinksUiState]: the vote pill ([showsScore], with arrows
 * while [canVote]), the moderator's [reviewNote] of an own rejected or hidden link, the profile [author] of another
 * student's link and the [rows] in display order.
 */
@Immutable
data class LinkActionsModel(
    val link: SubjectLink,
    val pinned: Boolean,
    val previous: Boolean,
    val showsScore: Boolean,
    val canVote: Boolean,
    val reviewNote: String?,
    val author: String?,
    val rows: List<LinkAction>,
)

/**
 * The sheet's rules for [linkId], or null while the links load or once the link is gone. Others' links vote; an own
 * shared link shows its score without arrows, an own private one none. Pinning needs the connection unless the link is
 * one's own; reporting needs it, no `REPORT` restriction and no earlier report. A deleted account's shared links move
 * to a placeholder author with a negative ISU and no profile, so only a positive ISU is named.
 */
fun SubjectLinksUiState.linkActions(linkId: String): LinkActionsModel? {
    val snapshot = content ?: return null
    val link = (snapshot.mine + snapshot.shared + snapshot.previous).firstOrNull { it.id == linkId } ?: return null
    val reviewed = link.status == SubjectLinkStatus.REJECTED || link.status == SubjectLinkStatus.HIDDEN
    val author = link.author?.takeIf { !link.isMine && it.isu > 0 }
    return LinkActionsModel(
        link = link,
        pinned = snapshot.pinnedId == link.id,
        previous = snapshot.previous.any { it.id == link.id },
        showsScore = !link.isMine || link.visibility != LinkVisibility.PRIVATE,
        canVote = !link.isMine && canVote,
        reviewNote = link.reviewNote.takeIf { link.isMine && reviewed },
        author = author?.name,
        rows = listOfNotNull(
            LinkAction.OPEN,
            LinkAction.AUTHOR.takeIf { author != null },
            LinkAction.COPY,
            LinkAction.SCORES.takeIf { GoogleSheetUrl.parse(link.url) != null },
            LinkAction.PIN.takeIf { link.isMine || snapshot.servicesEnabled },
            LinkAction.EDIT.takeIf { link.isMine },
            LinkAction.DELETE.takeIf { link.isMine },
            LinkAction.REPORT.takeIf { !link.isMine && canReport && !link.reportedByMe },
        ),
    )
}

/** What the actions sheet asks of its host; every effect (navigation, the clipboard, closing) is the host's. */
@Immutable
class LinkActionsActions(
    /** `up` is the up arrow; tapping the arrow of the current vote takes it back. A vote keeps the sheet open. */
    val onVote: (link: SubjectLink, up: Boolean) -> Unit = { _, _ -> },
    val onOpen: (SubjectLink) -> Unit = {},
    /** `Автор: <name>`: the author's profile by ISU. */
    val onAuthor: (isu: Int) -> Unit = {},
    val onCopy: (SubjectLink) -> Unit = {},
    /** `Мои баллы`: the own total from this Google Sheet. */
    val onScores: (SubjectLink) -> Unit = {},
    val onPin: (SubjectLink) -> Unit = {},
    val onEdit: (SubjectLink) -> Unit = {},
    /** `Удалить` was tapped; [LinkActionsSheetRoute] asks before it deletes. */
    val onDelete: (SubjectLink) -> Unit = {},
    val onReport: (SubjectLink) -> Unit = {},
)

object LinkActionsSheetTestTags {
    const val NOTE = "link_actions_note"

    fun row(action: LinkAction): String = "link_actions_${action.name.lowercase()}"
}

/**
 * The actions sheet over the links view model it shares with nothing: votes, pins and the confirmed delete go to
 * [viewModel], the rest to the host. One action runs at a time: while one is in flight the rows that act ignore taps
 * (open, copy and the author's profile stay). Every action but a vote closes the sheet on success ([onDismiss]); a
 * failure reaches [onFailure] while the sheet is started (the host's snackbar). A link deleted here or elsewhere closes
 * the sheet unless an action is pending.
 */
@Composable
fun LinkActionsSheetRoute(
    viewModel: SubjectLinksViewModel,
    linkId: String,
    effects: LinkActionsActions,
    onDismiss: () -> Unit,
    onFailure: (AppError) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dismiss by rememberUpdatedState(onDismiss)
    val failure by rememberUpdatedState(onFailure)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    LinkEvent.Done, LinkEvent.Saved -> dismiss()
                    is LinkEvent.Failed -> failure(event.error)
                }
            }
        }
    }
    val gone = state.content != null && state.linkActions(linkId) == null
    LaunchedEffect(gone, state.busy) {
        if (gone && !state.busy) dismiss()
    }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    val actions = remember(viewModel, effects) {
        fun idle() = !viewModel.uiState.value.busy
        LinkActionsActions(
            onVote = { link, up -> if (idle()) viewModel.vote(link.id, up) },
            onOpen = effects.onOpen,
            onAuthor = effects.onAuthor,
            onCopy = effects.onCopy,
            onScores = { if (idle()) effects.onScores(it) },
            onPin = { if (idle()) viewModel.pin(it.id) },
            onEdit = { if (idle()) effects.onEdit(it) },
            onDelete = { if (idle()) confirmingDelete = true },
            onReport = { if (idle()) effects.onReport(it) },
        )
    }
    LinkActionsSheetContent(state, linkId, actions, modifier)
    if (confirmingDelete) {
        ConfirmDialog(
            title = stringResource(Res.string.links_delete_confirm),
            confirmLabel = stringResource(Res.string.links_delete),
            dismissLabel = stringResource(CoreRes.string.common_cancel),
            onConfirm = {
                confirmingDelete = false
                viewModel.delete(linkId)
            },
            onDismiss = { confirmingDelete = false },
            destructive = true,
        )
    }
}

/**
 * What can be done with one link (`sheet_link_actions.xml`): its title and caption (as in the list), the vote pill at
 * the end of the title, the moderator's reason under an own rejected or hidden link, then `Открыть`, `Автор: <name>`,
 * `Скопировать ссылку`, `Мои баллы` for a Google Sheet of any author, `Закрепить` / `Открепить`, `Изменить` and
 * `Удалить` for own links and `Пожаловаться` (see [linkActions]). Nothing is drawn while the links load or once the
 * link is gone.
 */
@Composable
fun LinkActionsSheetContent(
    state: SubjectLinksUiState,
    linkId: String,
    actions: LinkActionsActions,
    modifier: Modifier = Modifier,
) {
    val model = remember(state, linkId) { state.linkActions(linkId) } ?: return
    val link = model.link
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = ItmoTheme.spacing.group),
    ) {
        SheetHandle(Modifier.align(Alignment.CenterHorizontally))
        Heading(model, actions)
        model.reviewNote?.let { note ->
            Text(
                stringResource(Res.string.links_rejected_reason, note),
                Modifier
                    .padding(horizontal = ItmoTheme.spacing.screenMargin)
                    .padding(top = ItmoTheme.spacing.related)
                    .testTag(LinkActionsSheetTestTags.NOTE),
                color = ItmoTheme.colorScheme.error,
                style = ItmoTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(ItmoTheme.spacing.compact))
        model.rows.forEach { action -> ActionRow(action, model, actions) }
    }
}

/** The title and caption, and the vote pill on the margin when the link has a score. */
@Composable
private fun Heading(model: LinkActionsModel, actions: LinkActionsActions) {
    val link = model.link
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = ItmoTheme.spacing.group)
            .padding(
                start = ItmoTheme.spacing.screenMargin,
                end = if (model.showsScore) ItmoTheme.spacing.related else ItmoTheme.spacing.screenMargin,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                link.displayTitle(),
                Modifier.semantics { heading() },
                color = ItmoTheme.colorScheme.onSurface,
                style = ItmoTheme.typography.titleMedium,
            )
            Text(
                linkMeta(link, model.pinned, model.previous).asString(),
                Modifier.padding(top = ItmoTheme.spacing.related),
                color = ItmoTheme.colorScheme.onSurfaceVariant,
                style = ItmoTheme.typography.bodyMedium,
            )
        }
        if (model.showsScore) {
            VotePill(
                score = link.score,
                myVote = link.myVote.toVote(),
                scoreDescription = stringResource(CoreRes.string.links_score, link.score),
                onVote = if (model.canVote) { vote -> actions.onVote(link, vote == Vote.Up) } else null,
                labels = VoteLabels(
                    up = stringResource(CoreRes.string.links_vote_up),
                    down = stringResource(CoreRes.string.links_vote_down),
                ),
            )
        }
    }
}

@Composable
private fun ActionRow(action: LinkAction, model: LinkActionsModel, actions: LinkActionsActions) {
    val link = model.link
    when (action) {
        LinkAction.OPEN -> ActionRow(action, KitRes.drawable.ic_open_in_new.painter(), stringResource(Res.string.links_open)) {
            actions.onOpen(link)
        }
        LinkAction.AUTHOR -> ActionRow(
            action,
            KitRes.drawable.ic_person.painter(),
            stringResource(Res.string.links_author, model.author.orEmpty()),
        ) { link.author?.let { actions.onAuthor(it.isu) } }
        LinkAction.COPY -> ActionRow(action, KitRes.drawable.ic_content_copy.painter(), stringResource(Res.string.links_copy)) {
            actions.onCopy(link)
        }
        LinkAction.SCORES -> ActionRow(action, KitRes.drawable.ic_table.painter(), stringResource(Res.string.sheet_scores_action)) {
            actions.onScores(link)
        }
        LinkAction.PIN -> ActionRow(
            action,
            (if (model.pinned) KitRes.drawable.ic_keep_off else KitRes.drawable.ic_keep).painter(),
            stringResource(if (model.pinned) Res.string.links_unpin else Res.string.links_pin),
        ) { actions.onPin(link) }
        LinkAction.EDIT -> ActionRow(action, KitRes.drawable.ic_edit.painter(), stringResource(Res.string.links_edit)) {
            actions.onEdit(link)
        }
        LinkAction.DELETE -> ActionRow(action, KitRes.drawable.ic_delete.painter(), stringResource(Res.string.links_delete)) {
            actions.onDelete(link)
        }
        LinkAction.REPORT -> ActionRow(action, KitRes.drawable.ic_flag.painter(), stringResource(Res.string.links_report)) {
            actions.onReport(link)
        }
    }
}

/** `Widget.ItmoWidgets.SheetAction`: a row of at least 48 dp on the margin, the icon in `onSurfaceVariant`. */
@Composable
private fun ActionRow(action: LinkAction, icon: Painter, text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ItmoTheme.spacing.touchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = ItmoTheme.spacing.screenMargin, vertical = ItmoTheme.spacing.content)
            .testTag(LinkActionsSheetTestTags.row(action)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, Modifier.size(ActionIconSize), tint = ItmoTheme.colorScheme.onSurfaceVariant)
        Text(
            text,
            Modifier.padding(start = ItmoTheme.spacing.group),
            color = ItmoTheme.colorScheme.onSurface,
            style = ItmoTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun DrawableResource.painter(): Painter = painterResource(this)

/** The 24 dp icons of `Widget.ItmoWidgets.SheetAction`. */
private val ActionIconSize = 24.dp
