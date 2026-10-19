package dev.alllexey.itmowidgets.feature.recordbook.ui.subject

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.alllexey.itmowidgets.core.lesson.LessonTarget
import dev.alllexey.itmowidgets.core.navigation.SheetScoresArgs
import dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs
import dev.alllexey.itmowidgets.core.navigation.TeacherReviewArgs
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.designsystem.components.state.AppRefreshBox
import dev.alllexey.itmowidgets.designsystem.components.state.ContentState
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateAction
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateLoading
import dev.alllexey.itmowidgets.designsystem.kit.ItmoDetailScreen
import dev.alllexey.itmowidgets.designsystem.kit.ItmoSnackbarHost
import dev.alllexey.itmowidgets.designsystem.kit.ListGroupNote
import dev.alllexey.itmowidgets.designsystem.kit.SegmentedChoice
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSubjectUiState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectMaterials
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectTab
import dev.alllexey.itmowidgets.shared.compose.strings.Res
import dev.alllexey.itmowidgets.shared.compose.strings.common_load_error_title
import dev.alllexey.itmowidgets.shared.compose.strings.common_retry
import dev.alllexey.itmowidgets.shared.compose.strings.recordbook_back
import dev.alllexey.itmowidgets.shared.compose.strings.recordbook_refresh_error
import dev.alllexey.itmowidgets.shared.compose.strings.subject_link_add
import dev.alllexey.itmowidgets.shared.compose.strings.subject_scores_title
import dev.alllexey.itmowidgets.shared.compose.strings.subject_tab_lessons
import dev.alllexey.itmowidgets.shared.compose.strings.subject_tab_materials
import dev.alllexey.itmowidgets.shared.designsystem.ic_add
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/** Test tags of [RecordbookSubjectScreen]. */
object SubjectTestTags {
    const val LOADING = "subject:loading"
    const val ERROR = "subject:error"
    const val TABS = "subject:tabs"
    const val HERO = "subject:hero"
    const val ADD_LINK = "subject:add_link"
    const val MATERIALS_GATE = "subject:materials_gate"
    const val REVIEWS_GATE = "subject:reviews_gate"

    /** The list of one tab's page. */
    fun list(tab: SubjectTab) = "subject:list:${tab.name.lowercase()}"
    fun teacher(index: Int) = "subject:teacher:$index"
    fun link(id: String) = "subject:link:$id"
}

/**
 * What the subject page asks of its host and its ViewModel: the host opens links, the link sheets, a teacher, the
 * review editor, `Подключение к ITMO.Widgets`, `Мои баллы`, the BARS sign-in, the Спорт tab and a lesson's sheet;
 * [RecordbookSubjectRoute] wires the rest to the ViewModel.
 */
data class RecordbookSubjectActions(
    val onBack: () -> Unit = {},
    val onOpenLink: (url: String) -> Unit = {},
    val onLinkActions: (SubjectLinksArgs, linkId: String) -> Unit = { _, _ -> },
    val onAllLinks: (SubjectLinksArgs) -> Unit = {},
    val onAddLink: (SubjectLinksArgs) -> Unit = {},
    val onOpenTeacher: (isu: Int) -> Unit = {},
    val onWriteReview: (TeacherReviewArgs) -> Unit = {},
    val onConnect: () -> Unit = {},
    val onOpenSheetScores: (SheetScoresArgs) -> Unit = {},
    val onBarsSignIn: () -> Unit = {},
    val onOpenSport: () -> Unit = {},
    val onOpenLesson: (LessonTarget) -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onSelectTab: (SubjectTab) -> Unit = {},
    val onShowAllLessons: () -> Unit = {},
    val onConfirmBinding: (subjectId: Long) -> Unit = {},
    val onRejectBinding: () -> Unit = {},
    val onRetryLessons: () -> Unit = {},
    val onVote: (linkId: String, up: Boolean) -> Unit = { _, _ -> },
    val onDisconnectSheet: () -> Unit = {},
)

/**
 * Предмет (docs/features/subject-page.md): a pushed screen with the subject's name and `Дифзачёт, 3 семестр`, the tabs
 * `Баллы`, `Пары`, `Материалы` under it, each tab's content on a page below that a horizontal swipe turns. On
 * `Материалы` the FAB adds a link. Stateless; [RecordbookSubjectRoute] feeds it.
 */
@Composable
fun RecordbookSubjectScreen(
    state: RecordbookSubjectUiState,
    actions: RecordbookSubjectActions,
    modifier: Modifier = Modifier,
    snackbars: SnackbarHostState = remember { SnackbarHostState() },
) {
    val content = state as? RecordbookSubjectUiState.Content
    val materials = (content?.materials as? SubjectMaterials.Content)?.takeIf { content.tab == SubjectTab.MATERIALS }
    ItmoDetailScreen(
        title = content?.title.orEmpty(),
        onBack = actions.onBack,
        backLabel = stringResource(Res.string.recordbook_back),
        modifier = modifier,
        subtitle = content?.subtitle?.asString(),
        floatingActionButton = {
            if (materials != null) {
                ExtendedFloatingActionButton(
                    onClick = { actions.onAddLink(materials.args) },
                    modifier = Modifier.testTag(SubjectTestTags.ADD_LINK),
                    icon = { Icon(painterResource(KitRes.drawable.ic_add), contentDescription = null) },
                    text = { Text(stringResource(Res.string.subject_link_add)) },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                RecordbookSubjectUiState.Loading -> ContentStateLoading(Modifier.testTag(SubjectTestTags.LOADING))
                is RecordbookSubjectUiState.Failed -> AppRefreshBox(state.retrying, actions.onRetry, Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        ContentState(
                            title = stringResource(Res.string.common_load_error_title),
                            modifier = Modifier.testTag(SubjectTestTags.ERROR),
                            description = state.error.toUiText().asString(),
                            action = ContentStateAction(stringResource(Res.string.common_retry), actions.onRetry),
                        )
                    }
                }
                is RecordbookSubjectUiState.Content -> AppRefreshBox(state.refreshing, actions.onRefresh, Modifier.fillMaxSize()) {
                    Tabs(state, actions)
                }
            }
            ItmoSnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/**
 * The tabs over a pager of their pages: a tap on a tab slides to its page, a horizontal swipe selects the next tab.
 * Each page keeps its own scroll position.
 */
@Composable
private fun Tabs(state: RecordbookSubjectUiState.Content, actions: RecordbookSubjectActions) {
    val tabs = SubjectTab.entries
    val labels = listOf(
        stringResource(Res.string.subject_scores_title),
        stringResource(Res.string.subject_tab_lessons),
        stringResource(Res.string.subject_tab_materials),
    )
    val pager = rememberPagerState(initialPage = tabs.indexOf(state.tab)) { tabs.size }
    val lists = tabs.map { rememberLazyListState() }
    val selected by rememberUpdatedState(state.tab)
    val onSelect by rememberUpdatedState(actions.onSelectTab)
    LaunchedEffect(state.tab) {
        val page = tabs.indexOf(state.tab)
        if (pager.currentPage != page) pager.animateScrollToPage(page)
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page -> if (tabs[page] != selected) onSelect(tabs[page]) }
    }
    Column(Modifier.fillMaxSize()) {
        SegmentedChoice(
            options = labels,
            // The swipe's destination, so the tab follows the finger before the page settles.
            selectedIndex = pager.targetPage,
            onSelect = { actions.onSelectTab(tabs[it]) },
            modifier = Modifier
                .padding(horizontal = ItmoTheme.spacing.screenMargin)
                .padding(top = ItmoTheme.spacing.related, bottom = ItmoTheme.spacing.compact)
                .testTag(SubjectTestTags.TABS),
        )
        HorizontalPager(pager, Modifier.weight(1f), key = { tabs[it] }, verticalAlignment = Alignment.Top) { page ->
            LazyColumn(
                Modifier.fillMaxSize().testTag(SubjectTestTags.list(tabs[page])),
                state = lists[page],
                contentPadding = PaddingValues(
                    start = ItmoTheme.spacing.screenMargin,
                    end = ItmoTheme.spacing.screenMargin,
                    top = ItmoTheme.spacing.related,
                    bottom = ItmoTheme.spacing.fabClearance,
                ),
                verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
            ) {
                state.refreshError?.let { error ->
                    item(key = "refresh-error") {
                        ListGroupNote(stringResource(Res.string.recordbook_refresh_error, error.toUiText().asString()))
                    }
                }
                tab(tabs[page], state, actions)
            }
        }
    }
}

private fun LazyListScope.tab(tab: SubjectTab, state: RecordbookSubjectUiState.Content, actions: RecordbookSubjectActions) {
    when (tab) {
        SubjectTab.SCORES -> scoresTab(state.scores, actions)
        SubjectTab.LESSONS -> lessonsTab(state.lessons, actions)
        SubjectTab.MATERIALS -> materialsTab(state.materials, actions)
    }
}
