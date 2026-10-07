package dev.alllexey.itmowidgets.feature.resources.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.resources.SubjectLink
import dev.alllexey.itmowidgets.core.resources.displayTitle
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButton
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButtonStyle
import dev.alllexey.itmowidgets.designsystem.components.groups.GroupSurface
import dev.alllexey.itmowidgets.designsystem.components.groups.SectionHeading
import dev.alllexey.itmowidgets.designsystem.components.groups.SectionHeadingSpacing
import dev.alllexey.itmowidgets.designsystem.components.groups.connectedGroupItem
import dev.alllexey.itmowidgets.designsystem.components.rows.LinkRow
import dev.alllexey.itmowidgets.designsystem.components.rows.Vote
import dev.alllexey.itmowidgets.designsystem.components.rows.VoteLabels
import dev.alllexey.itmowidgets.designsystem.components.rows.VotePill
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetClose
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetScaffold
import dev.alllexey.itmowidgets.designsystem.components.state.ContentState
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateAction
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateSize
import dev.alllexey.itmowidgets.designsystem.components.state.Skeleton
import dev.alllexey.itmowidgets.designsystem.components.state.SkeletonStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.resources.presentation.LinkEvent
import dev.alllexey.itmowidgets.feature.resources.presentation.SubjectLinksUiState
import dev.alllexey.itmowidgets.feature.resources.presentation.SubjectLinksViewModel
import dev.alllexey.itmowidgets.shared.core.common_close
import dev.alllexey.itmowidgets.shared.core.common_retry
import dev.alllexey.itmowidgets.shared.core.links_actions
import dev.alllexey.itmowidgets.shared.core.links_add
import dev.alllexey.itmowidgets.shared.core.links_mine
import dev.alllexey.itmowidgets.shared.core.links_score
import dev.alllexey.itmowidgets.shared.core.links_title
import dev.alllexey.itmowidgets.shared.core.links_vote_down
import dev.alllexey.itmowidgets.shared.core.links_vote_up
import dev.alllexey.itmowidgets.shared.designsystem.ic_add
import dev.alllexey.itmowidgets.shared.designsystem.ic_error
import dev.alllexey.itmowidgets.shared.designsystem.ic_link
import dev.alllexey.itmowidgets.shared.feature.resources.Res
import dev.alllexey.itmowidgets.shared.feature.resources.links_empty
import dev.alllexey.itmowidgets.shared.feature.resources.links_error_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/** What the links sheet asks its host to do; every effect (opening a link, navigation, closing) is the host's. */
@Immutable
class SubjectLinksActions(
    val onOpen: (SubjectLink) -> Unit = {},
    /** A long press: the link's actions sheet. */
    val onLinkActions: (SubjectLink) -> Unit = {},
    /** `up` is the up arrow; tapping the arrow of the current vote takes it back upstream. */
    val onVote: (link: SubjectLink, up: Boolean) -> Unit = { _, _ -> },
    val onAdd: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onClose: () -> Unit = {},
)

object SubjectLinksSheetTestTags {
    const val LIST = "subject_links_list"
    const val LOADING = "subject_links_loading"
    const val STATE = "subject_links_state"
    const val RETRY = "subject_links_retry"
    const val ADD = "subject_links_add"
}

/**
 * The links sheet over its view model: votes and the retry go to [viewModel], the rest to the host. A failed action
 * or a failed refresh that keeps the links reaches [onFailure] while the sheet is started (the host's snackbar).
 */
@Composable
fun SubjectLinksSheetRoute(
    viewModel: SubjectLinksViewModel,
    onOpen: (SubjectLink) -> Unit,
    onLinkActions: (SubjectLink) -> Unit,
    onAdd: () -> Unit,
    onClose: () -> Unit,
    onFailure: (AppError) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val failure by rememberUpdatedState(onFailure)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event -> if (event is LinkEvent.Failed) failure(event.error) }
        }
    }
    val actions = remember(viewModel, onOpen, onLinkActions, onAdd, onClose) {
        SubjectLinksActions(
            onOpen = onOpen,
            onLinkActions = onLinkActions,
            onVote = { link, up -> viewModel.vote(link.id, up) },
            onAdd = onAdd,
            onRetry = { viewModel.refresh(RefreshMode.Pull) },
            onClose = onClose,
        )
    }
    SubjectLinksSheetContent(viewModel.scope.subjectName, state, actions, modifier)
}

/**
 * All links of one subject period (`Все ссылки`): the subject under the title, then categories in declaration order,
 * chats and links of past years, each a heading over one connected group, and the add button pinned under the list.
 * Own links say `моя`, others carry the vote pill, without arrows when voting is restricted or the app has no
 * connection. The list is keyed by link id in the view model's stable order, so a vote changes a pill in place. The
 * first load shows placeholder rows; a failed one the error with a retry; a period without links the empty state.
 */
@Composable
fun SubjectLinksSheetContent(
    subject: String,
    state: SubjectLinksUiState,
    actions: SubjectLinksActions,
    modifier: Modifier = Modifier,
) {
    SheetScaffold(
        title = stringResource(CoreRes.string.links_title),
        modifier = modifier,
        subtitle = subject,
        close = SheetClose(stringResource(CoreRes.string.common_close), actions.onClose),
        footer = {
            ProgressButton(
                stringResource(CoreRes.string.links_add),
                actions.onAdd,
                Modifier
                    .padding(horizontal = ItmoTheme.spacing.screenMargin)
                    .fillMaxWidth()
                    .testTag(SubjectLinksSheetTestTags.ADD),
                style = ProgressButtonStyle.Tonal,
                icon = painterResource(KitRes.drawable.ic_add),
            )
        },
    ) {
        val error = state.error
        when {
            state.content == null && error == null -> Skeleton(
                SkeletonStyle.List,
                Modifier
                    .fillMaxWidth()
                    .height(LoadingHeight)
                    .testTag(SubjectLinksSheetTestTags.LOADING),
                rows = LOADING_ROWS,
            )
            state.content == null -> ContentState(
                stringResource(Res.string.links_error_title),
                Modifier.testTag(SubjectLinksSheetTestTags.STATE),
                size = ContentStateSize.Compact,
                icon = painterResource(KitRes.drawable.ic_error),
                description = error?.toUiText()?.asString(),
                action = ContentStateAction(
                    stringResource(CoreRes.string.common_retry),
                    actions.onRetry,
                    modifier = Modifier.testTag(SubjectLinksSheetTestTags.RETRY),
                ),
            )
            else -> {
                val rows = remember(state) { state.linkRows() }
                if (rows.isEmpty()) {
                    ContentState(
                        stringResource(Res.string.links_empty),
                        Modifier.testTag(SubjectLinksSheetTestTags.STATE),
                        size = ContentStateSize.Compact,
                        icon = painterResource(KitRes.drawable.ic_link),
                    )
                } else {
                    LinkList(rows, actions)
                }
            }
        }
    }
}

@Composable
private fun LinkList(rows: List<LinkListRow>, actions: SubjectLinksActions) {
    LazyColumn(
        Modifier
            .fillMaxWidth()
            .testTag(SubjectLinksSheetTestTags.LIST),
        contentPadding = PaddingValues(
            start = ItmoTheme.spacing.screenMargin,
            end = ItmoTheme.spacing.screenMargin,
            bottom = ItmoTheme.spacing.group,
        ),
    ) {
        items(rows, key = { it.key }, contentType = { if (it is LinkListRow.Header) HEADER else LINK }) { row ->
            when (row) {
                is LinkListRow.Header -> SectionHeading(
                    row.title.asString(),
                    spacing = if (row.first) SectionHeadingSpacing.First else SectionHeadingSpacing.Sheet,
                )
                is LinkListRow.Item -> LinkItem(row, actions)
            }
        }
    }
}

/** Own and others' links look alike: `моя` marks an own one, the vote pill another student's. */
@Composable
private fun LinkItem(row: LinkListRow.Item, actions: SubjectLinksActions) {
    val link = row.link
    LinkRow(
        title = link.displayTitle(),
        onClick = { actions.onOpen(link) },
        // The sheet itself is surfaceContainerLow, so its groups take a stronger surface than a page's.
        modifier = Modifier.connectedGroupItem(row.position, GroupSurface.Sheet),
        caption = linkMeta(link, row.pinned, row.previous).asString(),
        ownBadge = if (link.isMine) stringResource(CoreRes.string.links_mine) else null,
        onLongClick = { actions.onLinkActions(link) },
        longClickLabel = stringResource(CoreRes.string.links_actions),
        votes = if (link.isMine) null else {
            {
                VotePill(
                    score = link.score,
                    myVote = link.myVote.toVote(),
                    scoreDescription = stringResource(CoreRes.string.links_score, link.score),
                    onVote = if (row.canVote) { vote -> actions.onVote(link, vote == Vote.Up) } else null,
                    labels = VoteLabels(
                        up = stringResource(CoreRes.string.links_vote_up),
                        down = stringResource(CoreRes.string.links_vote_down),
                    ),
                )
            }
        },
    )
}

/** A viewer's vote of +1, -1 or 0 as the pill's selected arrow. */
internal fun Int.toVote(): Vote? = when {
    this > 0 -> Vote.Up
    this < 0 -> Vote.Down
    else -> null
}

/** `sheet_subject_links.xml`'s 256 dp skeleton of three list rows. */
private val LoadingHeight = 256.dp
private const val LOADING_ROWS = 3

private const val HEADER = 0
private const val LINK = 1
