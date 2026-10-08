package dev.alllexey.itmowidgets.feature.recordbook.ui.subject

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.alllexey.itmowidgets.core.navigation.SheetScoresArgs
import dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.resources.SubjectLink
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.textResource
import dev.alllexey.itmowidgets.designsystem.components.bars.AppTopBarBack
import dev.alllexey.itmowidgets.designsystem.components.dialogs.ChoiceDialog
import dev.alllexey.itmowidgets.designsystem.components.groups.SectionHeading
import dev.alllexey.itmowidgets.designsystem.components.state.AppRefreshBox
import dev.alllexey.itmowidgets.designsystem.components.state.ContentState
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateAction
import dev.alllexey.itmowidgets.designsystem.components.state.Skeleton
import dev.alllexey.itmowidgets.designsystem.components.state.SkeletonStyle
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSubjectEvent
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSubjectUiState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSubjectViewModel
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SheetLinkOption
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectSheetState
import dev.alllexey.itmowidgets.feature.recordbook.ui.RecordbookErrorSnackbars
import dev.alllexey.itmowidgets.feature.recordbook.ui.subject.preview.RecordbookSubjectPreviewSamples
import dev.alllexey.itmowidgets.shared.core.common_load_error_title
import dev.alllexey.itmowidgets.shared.core.common_retry
import dev.alllexey.itmowidgets.shared.designsystem.ic_error
import dev.alllexey.itmowidgets.shared.feature.recordbook.Res
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_back
import dev.alllexey.itmowidgets.shared.feature.recordbook.sheet_scores_choose_link
import dev.alllexey.itmowidgets.shared.feature.recordbook.sheet_scores_link_mine
import dev.alllexey.itmowidgets.shared.feature.recordbook.sheet_scores_link_untitled
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_semester
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_subtitle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/** Test tags of [RecordbookSubjectScreen]. */
object RecordbookSubjectTestTags {
    const val TITLE = "recordbook_subject_title"
    const val LOADING = "recordbook_subject_loading"
    const val LIST = "recordbook_subject_list"

    /** The error state in the list's place. */
    const val STATE = "recordbook_subject_state"
    const val STATE_ACTION = "recordbook_subject_state_action"
}

/**
 * Where the subject page leads outside itself; the host turns each into its navigation. Links open outside the app,
 * the link sheets and `Мои баллы` take the subject's period, a teacher opens by ISU, BARS signs in interactively.
 */
class RecordbookSubjectExits(
    val onBack: () -> Unit = {},
    val onOpenLink: (url: String) -> Unit = {},
    val onLinkActions: (SubjectLinksArgs, linkId: String) -> Unit = { _, _ -> },
    val onAllLinks: (SubjectLinksArgs) -> Unit = {},
    val onAddLink: (SubjectLinksArgs) -> Unit = {},
    val onOpenTeacher: (isu: Int) -> Unit = {},
    val onOpenSheetScores: (SheetScoresArgs) -> Unit = {},
    val onBarsLogin: () -> Unit = {},
)

/** What the rows of [RecordbookSubjectScreen] report; [RecordbookSubjectRoute] binds them to the ViewModel. */
class RecordbookSubjectActions(
    val onBack: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onConfirmBinding: (subjectId: Long) -> Unit = {},
    val onRejectProposal: () -> Unit = {},
    val onRetryLessons: () -> Unit = {},
    val onShowAllLessons: () -> Unit = {},
    val onOpenLink: (url: String) -> Unit = {},
    val onLinkActions: (SubjectLink) -> Unit = {},
    val onVoteLink: (SubjectLink, up: Boolean) -> Unit = { _, _ -> },
    val onAllLinks: () -> Unit = {},
    val onAddLink: () -> Unit = {},
    val onOpenTeacher: (isu: Int) -> Unit = {},
    val onOpenSheet: (url: String) -> Unit = {},
    val onChangeSheetTotal: () -> Unit = {},
    val onDisconnectSheet: () -> Unit = {},
    /** The sheet link to connect: the only one at once, or the one picked out of several. */
    val onConnectSheet: (SheetLinkOption) -> Unit = {},
)

/**
 * The subject page with its Koin ViewModel (the ViewModel loads on creation from the cached subject). A failed
 * refresh or BARS journal and a failed vote show in snackbars; everything outside the page goes to [exits].
 * [semester] is the page's argument, shown under the subject's name. Android hosts it in `RecordbookSubjectFragment`.
 */
@Composable
fun RecordbookSubjectRoute(
    semester: Int,
    exits: RecordbookSubjectExits,
    viewModel: RecordbookSubjectViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val content = state as? RecordbookSubjectUiState.Content
    RecordbookErrorSnackbars(
        content?.refreshError,
        content?.barsError,
        snackbars,
        onRetry = { viewModel.refresh(RefreshMode.Force) },
        onBarsLogin = exits.onBarsLogin,
    )
    RecordbookSubjectEventSnackbars(viewModel.events, snackbars)
    val actions = remember(viewModel, exits) { subjectActions(viewModel, exits) }
    RecordbookSubjectScreen(state, semester, actions, snackbarHostState = snackbars)
}

/** The ViewModel's half of [RecordbookSubjectActions]; the link sheets and `Мои баллы` take the current scope. */
private fun subjectActions(viewModel: RecordbookSubjectViewModel, exits: RecordbookSubjectExits): RecordbookSubjectActions {
    fun scope(): ResourceScope? = (viewModel.uiState.value as? RecordbookSubjectUiState.Content)?.hub?.resourceScope
    fun linksArgs(): SubjectLinksArgs? = scope()?.let { SubjectLinksArgs(it.subjectId, it.subjectName, it.periodKey) }
    fun sheetArgs(url: String, step: SheetScoresArgs.Step): SheetScoresArgs? =
        scope()?.let { SheetScoresArgs(it.subjectId, it.subjectName, it.periodKey, url, step) }
    return RecordbookSubjectActions(
        onBack = exits.onBack,
        onRefresh = { viewModel.refresh(RefreshMode.Pull) },
        onRetry = { viewModel.refresh(RefreshMode.Force) },
        onConfirmBinding = viewModel::confirmBinding,
        onRejectProposal = viewModel::rejectProposal,
        onRetryLessons = viewModel::retryLessons,
        onShowAllLessons = viewModel::showAllLessons,
        onOpenLink = exits.onOpenLink,
        onLinkActions = { link -> linksArgs()?.let { exits.onLinkActions(it, link.id) } },
        onVoteLink = { link, up -> viewModel.voteLink(link.id, up) },
        onAllLinks = { linksArgs()?.let(exits.onAllLinks) },
        onAddLink = { linksArgs()?.let(exits.onAddLink) },
        onOpenTeacher = exits.onOpenTeacher,
        onOpenSheet = exits.onOpenLink,
        onChangeSheetTotal = {
            val content = viewModel.uiState.value as? RecordbookSubjectUiState.Content
            val score = (content?.hub?.sheet as? SubjectSheetState.Connected)?.score
            score?.let { sheetArgs(it.url, SheetScoresArgs.Step.TOTAL) }?.let(exits.onOpenSheetScores)
        },
        onDisconnectSheet = viewModel::disconnectSheet,
        onConnectSheet = { link -> sheetArgs(link.url, SheetScoresArgs.Step.CONNECT)?.let(exits.onOpenSheetScores) },
    )
}

/** A vote that did not reach the server says why in a short snackbar in place of the shown one, while started. */
@Composable
internal fun RecordbookSubjectEventSnackbars(events: Flow<RecordbookSubjectEvent>, snackbars: SnackbarHostState) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(events, snackbars, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            events.collect { event ->
                when (event) {
                    is RecordbookSubjectEvent.VoteFailed -> {
                        // As 2.2's snackbar did, the failure replaces whatever snackbar is shown.
                        val message = getString(event.error.textResource())
                        snackbars.currentSnackbarData?.dismiss()
                        launch { snackbars.showSnackbar(message, duration = SnackbarDuration.Short) }
                    }
                }
            }
        }
    }
}

/**
 * One page per subject (2.2's `fragment_recordbook_subject.xml`): the subject's name in up to two lines with its
 * assessment and semester under the back button, then one list of the result card (or the sport overview for physical
 * education), links, chats, controls, teachers and the nearest lessons ([subjectHubItems]).
 *
 * The first load without a cache shows list placeholders under the header, a failed one the error with a retry in the
 * same area; a pull shows the indicator over the list. Several sheet links ask which one to connect, own ones marked.
 */
@Composable
fun RecordbookSubjectScreen(
    state: RecordbookSubjectUiState,
    semester: Int,
    actions: RecordbookSubjectActions,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var sheetLinks by remember { mutableStateOf<List<SheetLinkOption>?>(null) }
    val connectSheet: (List<SheetLinkOption>) -> Unit = { links ->
        val single = links.singleOrNull()
        if (single != null) actions.onConnectSheet(single) else if (links.isNotEmpty()) sheetLinks = links
    }
    Box(modifier.fillMaxSize().background(ItmoTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize()) {
            SubjectHeader(state, semester, actions.onBack)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (state) {
                    RecordbookSubjectUiState.Loading -> Skeleton(
                        SkeletonStyle.List,
                        Modifier.fillMaxSize().testTag(RecordbookSubjectTestTags.LOADING),
                    )
                    is RecordbookSubjectUiState.Error -> ContentState(
                        title = stringResource(CoreRes.string.common_load_error_title),
                        modifier = Modifier.fillMaxSize().testTag(RecordbookSubjectTestTags.STATE),
                        icon = painterResource(KitRes.drawable.ic_error),
                        description = stringResource(state.error.textResource()),
                        action = ContentStateAction(
                            stringResource(CoreRes.string.common_retry),
                            actions.onRetry,
                            modifier = Modifier.testTag(RecordbookSubjectTestTags.STATE_ACTION),
                        ),
                    )
                    is RecordbookSubjectUiState.Content ->
                        AppRefreshBox(state.refreshing, actions.onRefresh, Modifier.fillMaxSize()) {
                            SubjectHubList(state, actions, connectSheet, listState)
                        }
                }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
    sheetLinks?.let { links ->
        SheetLinkPicker(
            links,
            onPick = { link ->
                sheetLinks = null
                actions.onConnectSheet(link)
            },
            onDismiss = { sheetLinks = null },
        )
    }
}

/**
 * The toolbar of the page: the back button and the subject's name in `titleMedium`, up to two lines, over
 * `Экзамен, 2 семестр` (only the semester while the subject loads or failed). The kit's `AppTopBar` keeps a title to
 * one line once it has a subtitle; the subject's name needs both lines.
 */
@Composable
private fun SubjectHeader(state: RecordbookSubjectUiState, semester: Int, onBack: () -> Unit) {
    val subject = (state as? RecordbookSubjectUiState.Content)?.subject
    val subtitle = subject?.controlType?.takeIf(String::isNotBlank)
        ?.let { stringResource(Res.string.subject_subtitle, it, semester) }
        ?: stringResource(Res.string.subject_semester, semester)
    Row(Modifier.fillMaxWidth().padding(start = HeaderEdgePadding)) {
        // The toolbar's navigation button stays centred in the bar's first 64 dp when the name takes two lines.
        Box(Modifier.height(HeaderMinHeight), contentAlignment = Alignment.Center) {
            AppTopBarBack(stringResource(Res.string.recordbook_back), onBack)
        }
        Column(
            Modifier
                .weight(1f)
                .heightIn(min = HeaderMinHeight)
                .padding(start = HeaderEdgePadding, end = ItmoTheme.spacing.screenMargin)
                .padding(vertical = ItmoTheme.spacing.compact),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                // A blank name keeps the line, so the header does not grow when the subject arrives; TalkBack skips
                // it, and only a name is the page's heading.
                subject?.name ?: " ",
                Modifier
                    .testTag(RecordbookSubjectTestTags.TITLE)
                    .then(if (subject != null) Modifier.semantics { heading() } else Modifier.clearAndSetSemantics {}),
                color = ItmoTheme.colorScheme.onSurface,
                maxLines = TITLE_LINES,
                overflow = TextOverflow.Ellipsis,
                style = ItmoTheme.typography.titleMedium.russian(),
            )
            Text(
                subtitle,
                Modifier.padding(top = SubtitleGap),
                color = ItmoTheme.colorScheme.onSurfaceVariant,
                style = ItmoTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SubjectHubList(
    state: RecordbookSubjectUiState.Content,
    actions: RecordbookSubjectActions,
    onConnectSheet: (List<SheetLinkOption>) -> Unit,
    listState: LazyListState,
) {
    val items = remember(state) { subjectHubItems(state) }
    val keys = remember(items) { subjectHubKeys(items) }
    val sheetActions = remember(actions, onConnectSheet) {
        SubjectSheetActions(
            onOpen = actions.onOpenSheet,
            onChangeTotal = actions.onChangeSheetTotal,
            onDisconnect = actions.onDisconnectSheet,
            onConnect = onConnectSheet,
        )
    }
    LazyColumn(
        Modifier.fillMaxSize().testTag(RecordbookSubjectTestTags.LIST),
        state = listState,
        contentPadding = PaddingValues(
            start = ItmoTheme.spacing.screenMargin,
            end = ItmoTheme.spacing.screenMargin,
            bottom = ItmoTheme.spacing.screenMargin,
        ),
    ) {
        items(items.size, key = { keys[it] }, contentType = { items[it]::class }) { index ->
            SubjectHubRow(items[index], actions, sheetActions)
        }
    }
}

@Composable
private fun SubjectHubRow(item: SubjectHubItem, actions: RecordbookSubjectActions, sheetActions: SubjectSheetActions) {
    when (item) {
        is SubjectHubItem.Hero -> SubjectHero(
            item.subject,
            item.step,
            sheet = item.sheet?.let { sheet -> { SubjectSheetTotal(sheet, sheetActions) } },
        )
        is SubjectHubItem.SportOverview -> SubjectSportOverview(item.subject, item.sport, onRetry = actions.onRetry)
        is SubjectHubItem.Link ->
            SubjectLinkItem(item, actions.onOpenLink, actions.onLinkActions, actions.onVoteLink)
        is SubjectHubItem.Lms -> SubjectLmsItem(item, actions.onOpenLink)
        is SubjectHubItem.AllLinks -> SubjectAllLinksRow(item, actions.onAllLinks)
        is SubjectHubItem.AddLink -> SubjectAddLinkRow(item, actions.onAddLink)
        is SubjectHubItem.Chat -> SubjectChatItem(item, actions.onOpenLink, actions.onLinkActions)
        is SubjectHubItem.Section -> SectionHeading(stringResource(item.title))
        is SubjectHubItem.Group -> SubjectControlGroupHeading(item.group)
        is SubjectHubItem.Control -> SubjectControlRow(item)
        is SubjectHubItem.Notice -> SubjectNotice(item.error, onRetry = actions.onRetry)
        is SubjectHubItem.Lesson -> SubjectLessonRow(item)
        is SubjectHubItem.AllLessons -> SubjectAllLessonsRow(item, actions.onShowAllLessons)
        is SubjectHubItem.LessonsMessage -> SubjectLessonsMessage(item.state, actions.onRetryLessons)
        is SubjectHubItem.BindingProposal ->
            SubjectBindingProposal(item.candidate, actions.onConfirmBinding, actions.onRejectProposal)
        is SubjectHubItem.BindingChoice -> SubjectBindingChoice(item.candidates, actions.onConfirmBinding)
        is SubjectHubItem.Teacher -> SubjectTeacherRow(item, actions.onOpenTeacher)
    }
}

/**
 * A key per row that stays with the row while the page updates (the identities 2.2's `SubjectHubAdapter` diff used): a
 * link, chat, control, lesson or teacher by its own id, every other row by its kind. A repeated key gets a suffix.
 */
internal fun subjectHubKeys(items: List<SubjectHubItem>): List<String> {
    val seen = mutableSetOf<String>()
    return items.map { item ->
        val key = when (item) {
            is SubjectHubItem.Hero -> "hero"
            is SubjectHubItem.SportOverview -> "sport"
            is SubjectHubItem.Link -> "link:${item.link.id}"
            is SubjectHubItem.Lms -> "lms"
            is SubjectHubItem.AllLinks -> "all-links"
            is SubjectHubItem.AddLink -> "add-link"
            is SubjectHubItem.Chat -> "chat:${item.link.id}"
            is SubjectHubItem.Section -> "section:${item.title.key}"
            is SubjectHubItem.Group -> "group:${item.group.controls.firstOrNull()?.id}"
            is SubjectHubItem.Control -> "control:${item.row.control.id}:${item.row.control.name}"
            is SubjectHubItem.Notice -> "notice"
            is SubjectHubItem.Lesson -> "lesson:${item.lesson.pairId}"
            is SubjectHubItem.AllLessons -> "all-lessons"
            is SubjectHubItem.LessonsMessage -> "lessons-message"
            is SubjectHubItem.BindingProposal, is SubjectHubItem.BindingChoice -> "binding"
            is SubjectHubItem.Teacher -> "teacher:${item.teacher.name}"
        }
        if (seen.add(key)) key else generateSequence(2) { it + 1 }.map { "$key:$it" }.first(seen::add)
    }
}

/** `Какая таблица?`: the subject's sheet links in their order, an own one as `Название, моя`. */
@Composable
private fun SheetLinkPicker(links: List<SheetLinkOption>, onPick: (SheetLinkOption) -> Unit, onDismiss: () -> Unit) {
    val untitled = stringResource(Res.string.sheet_scores_link_untitled)
    val names = links.map { link ->
        val title = link.title?.takeIf(String::isNotBlank) ?: untitled
        if (link.mine) stringResource(Res.string.sheet_scores_link_mine, title) else title
    }
    ChoiceDialog(
        title = stringResource(Res.string.sheet_scores_choose_link),
        options = names,
        selectedIndex = null,
        onSelect = { onPick(links[it]) },
        onDismiss = onDismiss,
    )
}

/** Russian hyphenation and the balanced line breaks of `breakStrategy="high_quality"`. */
private fun TextStyle.russian(): TextStyle =
    copy(localeList = LocaleList("ru"), hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph)

private const val TITLE_LINES = 2

/** `?attr/actionBarSize` of the XML toolbar under Material 3. */
private val HeaderMinHeight = 64.dp

/** The back button 4 dp in from the edge and the title 4 dp after it, at the 56 dp of the XML toolbar's title. */
private val HeaderEdgePadding = 4.dp
private val SubtitleGap = 2.dp

/*
 * The harness names a baseline `<function>_<@Preview name>`, and LR-1c recorded the XML references as
 * `RecordbookSubjectScreen_<state>`. Each state therefore is a function called after the screen in a holder class of
 * its own; the scanner instantiates each holder by reflection.
 */

@Composable
private fun SubjectPreview(state: RecordbookSubjectUiState, listState: LazyListState = rememberLazyListState()) =
    ItmoPreview {
        RecordbookSubjectScreen(state, semester = 2, actions = RecordbookSubjectActions(), listState = listState)
    }

/** The session: a final grade over the scale, simple controls, a teacher without a profile, no lessons ahead. */
internal class RecordbookSubjectScreenSessionPreview {
    @Preview(name = "session")
    @Composable
    fun RecordbookSubjectScreen() = SubjectPreview(RecordbookSubjectPreviewSamples.session())
}

/** A credit in the middle of the semester: the single credit mark on the scale and what is left to it. */
internal class RecordbookSubjectScreenCreditPreview {
    @Preview(name = "credit")
    @Composable
    fun RecordbookSubjectScreen() = SubjectPreview(RecordbookSubjectPreviewSamples.credit())
}

/** Physical education: the sport overview instead of the result card, no links and no lessons. */
internal class RecordbookSubjectScreenSportPreview {
    @Preview(name = "sport")
    @Composable
    fun RecordbookSubjectScreen() = SubjectPreview(RecordbookSubjectPreviewSamples.sport())
}

/** A BARS journal: two modules as groups of their controls, the exam and extra points. */
internal class RecordbookSubjectScreenBarsPreview {
    @Preview(name = "bars")
    @Composable
    fun RecordbookSubjectScreen() = SubjectPreview(RecordbookSubjectPreviewSamples.bars())
}

/** The connected sheet total in the result card, the short list of links and the chats. */
internal class RecordbookSubjectScreenSheetPreview {
    @Preview(name = "sheet")
    @Composable
    fun RecordbookSubjectScreen() = SubjectPreview(RecordbookSubjectPreviewSamples.sheet())
}

/** No sheet connected yet: `Мои баллы из таблицы` at the bottom of the result card. */
internal class RecordbookSubjectScreenOfferPreview {
    @Preview(name = "offer")
    @Composable
    fun RecordbookSubjectScreen() = SubjectPreview(RecordbookSubjectPreviewSamples.offer())
}

/** Lessons matched only by name: the binding proposal at the end of the page. */
internal class RecordbookSubjectScreenBindingPreview {
    @Preview(name = "binding")
    @Composable
    fun RecordbookSubjectScreen() = SubjectPreview(RecordbookSubjectPreviewSamples.binding())
}

internal class RecordbookSubjectScreenLoadingPreview {
    @Preview(name = "loading")
    @Composable
    fun RecordbookSubjectScreen() = SubjectPreview(RecordbookSubjectUiState.Loading)
}

internal class RecordbookSubjectScreenErrorPreview {
    @Preview(name = "error")
    @Composable
    fun RecordbookSubjectScreen() = SubjectPreview(RecordbookSubjectUiState.Error(AppError.Network))
}
