package dev.alllexey.itmowidgets.feature.recordbook.ui.subject

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
import dev.alllexey.itmowidgets.designsystem.kit.ListGroupNote
import dev.alllexey.itmowidgets.designsystem.kit.SegmentedChoice
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSubjectUiState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectMaterials
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectTab
import dev.alllexey.itmowidgets.shared.compose.strings.common_load_error_title
import dev.alllexey.itmowidgets.shared.compose.strings.common_retry
import dev.alllexey.itmowidgets.shared.designsystem.ic_add
import dev.alllexey.itmowidgets.shared.feature.recordbook.Res
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_back
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_refresh_error
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_link_add
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_scores_title
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_tab_lessons
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_tab_materials
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.compose.strings.Res as CoreRes
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/** Test tags of [RecordbookSubjectScreen]. */
object SubjectTestTags {
    const val LIST = "subject:list"
    const val LOADING = "subject:loading"
    const val ERROR = "subject:error"
    const val TABS = "subject:tabs"
    const val HERO = "subject:hero"
    const val ADD_LINK = "subject:add_link"
    const val MATERIALS_GATE = "subject:materials_gate"
    const val REVIEWS_GATE = "subject:reviews_gate"

    fun teacher(index: Int) = "subject:teacher:$index"
    fun link(id: String) = "subject:link:$id"
}

/**
 * What the subject page asks of its host and its ViewModel: the host opens links, the link sheets, a teacher, the
 * review editor, `Подключение к ITMO.Widgets`, `Мои баллы`, the BARS sign-in and the Спорт tab;
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
 * `Баллы`, `Пары`, `Материалы` under it, each tab's content below. On `Материалы` the FAB adds a link. Stateless;
 * [RecordbookSubjectRoute] feeds it.
 */
@Composable
fun RecordbookSubjectScreen(
    state: RecordbookSubjectUiState,
    actions: RecordbookSubjectActions,
    modifier: Modifier = Modifier,
    snackbars: SnackbarHostState = remember { SnackbarHostState() },
    listState: LazyListState = rememberLazyListState(),
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
                            title = stringResource(CoreRes.string.common_load_error_title),
                            modifier = Modifier.testTag(SubjectTestTags.ERROR),
                            description = state.error.toUiText().asString(),
                            action = ContentStateAction(stringResource(CoreRes.string.common_retry), actions.onRetry),
                        )
                    }
                }
                is RecordbookSubjectUiState.Content -> AppRefreshBox(state.refreshing, actions.onRefresh, Modifier.fillMaxSize()) {
                    Tabs(state, actions, listState)
                }
            }
            SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun Tabs(state: RecordbookSubjectUiState.Content, actions: RecordbookSubjectActions, listState: LazyListState) {
    val tabs = SubjectTab.entries
    val labels = listOf(
        stringResource(Res.string.subject_scores_title),
        stringResource(Res.string.subject_tab_lessons),
        stringResource(Res.string.subject_tab_materials),
    )
    LazyColumn(
        Modifier.fillMaxSize().testTag(SubjectTestTags.LIST),
        state = listState,
        contentPadding = PaddingValues(
            start = ItmoTheme.spacing.screenMargin,
            end = ItmoTheme.spacing.screenMargin,
            top = ItmoTheme.spacing.related,
            bottom = ItmoTheme.spacing.fabClearance,
        ),
        verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
    ) {
        item(key = "tabs") {
            SegmentedChoice(
                options = labels,
                selectedIndex = tabs.indexOf(state.tab),
                onSelect = { actions.onSelectTab(tabs[it]) },
                modifier = Modifier.testTag(SubjectTestTags.TABS),
            )
        }
        state.refreshError?.let { error ->
            item(key = "refresh-error") {
                ListGroupNote(stringResource(Res.string.recordbook_refresh_error, error.toUiText().asString()))
            }
        }
        tab(state, actions)
    }
}

private fun LazyListScope.tab(state: RecordbookSubjectUiState.Content, actions: RecordbookSubjectActions) {
    when (state.tab) {
        SubjectTab.SCORES -> scoresTab(state.scores, actions)
        SubjectTab.LESSONS -> lessonsTab(state.lessons, actions)
        SubjectTab.MATERIALS -> materialsTab(state.materials, actions)
    }
}
