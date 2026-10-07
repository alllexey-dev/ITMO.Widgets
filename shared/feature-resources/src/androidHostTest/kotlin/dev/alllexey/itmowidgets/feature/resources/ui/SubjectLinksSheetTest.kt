package dev.alllexey.itmowidgets.feature.resources.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs
import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.LinkVisibility
import dev.alllexey.itmowidgets.core.resources.ResourceReportReason
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.resources.SubjectLink
import dev.alllexey.itmowidgets.core.resources.SubjectLinksRepository
import dev.alllexey.itmowidgets.core.resources.SubjectLinksSnapshot
import dev.alllexey.itmowidgets.core.resources.SubjectLinksState
import dev.alllexey.itmowidgets.core.resources.UserRestriction
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.resources.presentation.SubjectLinksUiState
import dev.alllexey.itmowidgets.feature.resources.presentation.SubjectLinksViewModel
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertNoTextOverflow
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.flow.MutableStateFlow
import org.robolectric.annotation.Config

/**
 * The list cases of the deleted `SubjectLinksVisualTest` on the Compose sheet: sections in order, votes through the
 * real view model that change the pill in place, own links among others, review states, a vote restriction, the
 * missing connection, long titles at 320 dp and font scale 1.3, the four states and the host callbacks. Its looks
 * live in the `SubjectLinksSheetContent_*` goldens.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h4000dp")
class SubjectLinksSheetTest {

    private val opened = mutableListOf<String>()
    private val actionsOf = mutableListOf<String>()
    private val events = mutableListOf<String>()
    private val actions = SubjectLinksActions(
        onOpen = { opened += it.id },
        onLinkActions = { actionsOf += it.id },
        onVote = { link, up -> events += "vote:${link.id}:$up" },
        onAdd = { events += "add" },
        onRetry = { events += "retry" },
        onClose = { events += "close" },
    )

    @Test
    fun sheetListsEveryCategoryThenChatsThenPastYears() = runComposeUiTest {
        setContent { Sheet(SubjectLinksSamples.content) }

        val headings = onAllNodes(isHeading()).fetchSemanticsNodes()
            .mapNotNull { it.config.getOrNull(SemanticsProperties.Text)?.joinToString() }
        assertEquals(
            listOf("Ссылки", "Таблица баллов", "Очередь на сдачу", "Материалы курса", "Задания", "Записи лекций",
                "Конспекты", "К экзамену", "Другое", "Чаты", "С прошлых лет"),
            headings,
        )
        assertEquals("drive.google.com, Все, закреплена", caption("Материалы лектора"))
        assertEquals("drive.google.com, Все, 2025/26", caption("Материалы прошлого года"))
        assertEquals("Все, 2025/26", caption("synthetic.notion.site"))
        assertEquals("docs.google.com, ФИЗ ПИИКТ 3.2", caption("Баллы нашей группы"))
        assertEquals("Только я", caption("example.org"))
        assertTouchTargets()
        assertNoTextOverflow()
    }

    @Test
    fun arrowsChangeTheScoreAndTheSameArrowTakesTheVoteBack() = runComposeUiTest {
        val repository = VotingRepository(SubjectLinksSamples.snapshot)
        val model = model(repository)
        setContent { Route(model) }
        val title = "Задания потока"

        fun assertScore(score: String, vote: Int) {
            waitForIdle()
            onNode(hasText(title) and hasText(score)).assertExists()
            arrow(title, "Полезная ссылка").apply { if (vote > 0) assertIsSelected() else assertIsNotSelected() }
            arrow(title, "Бесполезная ссылка").apply { if (vote < 0) assertIsSelected() else assertIsNotSelected() }
        }

        assertScore("0", 0)
        arrow(title, "Полезная ссылка").performClick()
        assertScore("1", 1)
        arrow(title, "Полезная ссылка").performClick()
        assertScore("0", 0)
        arrow(title, "Бесполезная ссылка").performClick()
        // A negative score is written with a typographic minus.
        assertScore("−1", -1)
        assertEquals(-1, repository.link("tasks-flow").myVote)
    }

    @Test
    fun aVoteKeepsTheRowInItsPlace() = runComposeUiTest {
        // "Старая таблица" ties the first row at 8 and stays second by age; one vote up would rank it first.
        val older = SubjectLinksSamples.snapshot.shared.first { it.id == "scores-all" }
            .copy(id = "scores-old", title = "Старая таблица", updatedAt = SubjectLinksSamples.snapshot.shared[0].updatedAt - 1.hours)
        val snapshot = SubjectLinksSamples.snapshot.copy(shared = SubjectLinksSamples.snapshot.shared + older)
        val repository = VotingRepository(snapshot)
        setContent { Route(model(repository)) }

        assertEquals(listOf("Баллы всего потока", "Старая таблица", "Баллы нашей группы", "Баллы по таблице преподавателя"),
            scoresInPlace())
        val before = top("Старая таблица")

        arrow("Старая таблица", "Полезная ссылка").performClick()
        waitForIdle()

        assertEquals(9, repository.link("scores-old").score)
        onNode(hasText("Старая таблица") and hasText("9")).assertExists()
        assertEquals(before, top("Старая таблица"))
        assertEquals(listOf("Баллы всего потока", "Старая таблица", "Баллы нашей группы", "Баллы по таблице преподавателя"),
            scoresInPlace())
    }

    @Test
    fun ownLinksRankAmongOthersWithTheBadgeOnOneGroupSurface() = runComposeUiTest {
        setContent { Sheet(SubjectLinksSamples.content) }

        assertEquals(listOf("Баллы всего потока", "Баллы нашей группы", "Баллы по таблице преподавателя"),
            listOf("Баллы по таблице преподавателя", "Баллы всего потока", "Баллы нашей группы").sortedBy { top(it) })
        onNode(hasText("Баллы нашей группы") and hasText("моя")).assertExists()
        assertTrue(scoreDescriptions("Баллы нашей группы").isEmpty())
        assertEquals(listOf("Рейтинг 8"), scoreDescriptions("Баллы всего потока"))
        assertEquals(listOf("Рейтинг 2"), scoreDescriptions("Баллы по таблице преподавателя"))
        // The own vote of another student's link is told by the selected arrow.
        arrow("Баллы по таблице преподавателя", "Полезная ссылка").assertIsSelected()
        // One connected group: the rows of a section stand 2 dp apart.
        val gap = top("Баллы нашей группы") - bottom("Баллы всего потока")
        assertEquals(2f, gap.value, 0.5f)
    }

    @Test
    fun ownRowsSayTheirReviewStateAndOthersShowVotesWithoutArrowsUnderARestriction() = runComposeUiTest {
        var state by mutableStateOf(SubjectLinksSamples.restricted)
        setContent { Sheet(state) }

        onAllNodesWithContentDescription("Полезная ссылка").assertCountEquals(0)
        onAllNodesWithContentDescription("Бесполезная ссылка").assertCountEquals(0)
        val scores = onAllNodes(hasContentDescription("Рейтинг", substring = true)).fetchSemanticsNodes()
        assertEquals(11, scores.size)
        onNode(hasText(SubjectLinksSamples.LONG_TITLE) and hasText("моя")).assertExists()
        assertTrue(caption(SubjectLinksSamples.LONG_TITLE).endsWith("отклонена"))
        assertEquals("github.com, Все, на проверке", caption("Разборы домашних заданий"))
        captions().forEach { assertFalse(it.contains("·"), it) }

        state = SubjectLinksSamples.own
        waitForIdle()
        assertEquals("drive.google.com, Все", caption("Мои материалы"))
        assertTrue(caption("Конспект семинаров").endsWith("отклонена"))
        assertTrue(caption("Скрытая подборка").endsWith("скрыта"))
    }

    @Test
    fun withoutTheConnectionOthersLinksShowTheirScoreWithoutArrows() = runComposeUiTest {
        setContent { Sheet(SubjectLinksSamples.offline) }

        onAllNodesWithContentDescription("Полезная ссылка").assertCountEquals(0)
        assertEquals(listOf("Рейтинг 12"), scoreDescriptions("Материалы лектора"))
    }

    @Test
    @Config(qualifiers = "w320dp-h4000dp")
    fun longTitlesFitAtLargeFontOnANarrowScreen() = runComposeUiTest {
        setContent { Sheet(SubjectLinksSamples.content, width = NARROW_WIDTH, fontScale = NARROW_FONT_SCALE) }

        onNodeWithText(SubjectLinksSamples.LONG_TITLE, substring = true).assertExists()
        assertNoTextOverflow()
        assertTouchTargets()
    }

    @Test
    fun theStatesShareTheAreaAndTheErrorRetries() = runComposeUiTest {
        var state by mutableStateOf(SubjectLinksSamples.loading)
        setContent { Sheet(state, height = SHEET_HEIGHT) }

        onNodeWithTag(SubjectLinksSheetTestTags.LOADING).assertExists()
        onNodeWithTag(SubjectLinksSheetTestTags.LIST).assertDoesNotExist()

        state = SubjectLinksSamples.error
        waitForIdle()
        onNodeWithText("Не удалось загрузить ссылки").assertExists()
        onNodeWithText("Нет связи. Проверьте интернет.").assertExists()
        onNodeWithTag(SubjectLinksSheetTestTags.RETRY, useUnmergedTree = true).performClick()
        assertEquals(listOf("retry"), events)
        assertTouchTargets()

        state = SubjectLinksSamples.empty
        waitForIdle()
        onNodeWithText("Ссылок пока нет").assertExists()
        onNodeWithTag(SubjectLinksSheetTestTags.RETRY, useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag(SubjectLinksSheetTestTags.ADD).assertExists()
    }

    @Test
    fun aTapOpensTheLinkALongPressItsActionsAndTheHeaderAndFooterReachTheHost() = runComposeUiTest {
        setContent { Sheet(SubjectLinksSamples.content) }

        onNode(hasText("Материалы лектора")).performClick()
        val row = onNode(hasText("Баллы всего потока"))
        assertEquals("Действия со ссылкой", row.fetchSemanticsNode().config[SemanticsActions.OnLongClick].label)
        row.performSemanticsAction(SemanticsActions.OnLongClick)
        arrow("Задания потока", "Бесполезная ссылка").performClick()
        onNodeWithTag(SubjectLinksSheetTestTags.ADD).performClick()
        onNodeWithContentDescription("Закрыть").performClick()

        assertEquals(listOf("materials-all"), opened)
        assertEquals(listOf("scores-all"), actionsOf)
        assertEquals(listOf("vote:tasks-flow:false", "add", "close"), events)
    }

    private fun ComposeUiTest.arrow(title: String, label: String): SemanticsNodeInteraction =
        onNode(hasContentDescription(label) and hasAnyAncestor(hasText(title)))

    private fun ComposeUiTest.top(title: String): Dp = onNode(hasText(title)).getBoundsInRoot().top

    private fun ComposeUiTest.bottom(title: String): Dp = onNode(hasText(title)).getBoundsInRoot().bottom

    /** The scores section's rows from top to bottom. */
    private fun ComposeUiTest.scoresInPlace(): List<String> {
        val titles = listOf("Баллы всего потока", "Старая таблица", "Баллы нашей группы", "Баллы по таблице преподавателя")
        return titles.sortedBy { top(it) }
    }

    /** The second text of the merged row: its caption. */
    private fun ComposeUiTest.caption(title: String): String =
        onNode(hasText(title)).fetchSemanticsNode().config[SemanticsProperties.Text][1].text

    private fun ComposeUiTest.captions(): List<String> =
        onAllNodes(hasAnyAncestor(hasTestTag(SubjectLinksSheetTestTags.LIST)) and isLinkRow()).fetchSemanticsNodes()
            .mapNotNull { it.config.getOrNull(SemanticsProperties.Text)?.getOrNull(1)?.text }

    private fun ComposeUiTest.scoreDescriptions(title: String): List<String> =
        onNode(hasText(title)).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            .filter { it.startsWith("Рейтинг") }

    private fun isLinkRow() = SemanticsMatcher("a link row") { it.config.getOrNull(SemanticsActions.OnLongClick) != null }

    private fun model(repository: SubjectLinksRepository) = SubjectLinksViewModel(
        SavedStateHandle(
            mapOf(
                SubjectLinksArgs.SUBJECT_ID to SubjectLinksSamples.scope.subjectId,
                SubjectLinksArgs.SUBJECT_NAME to SubjectLinksSamples.scope.subjectName,
                SubjectLinksArgs.PERIOD_KEY to SubjectLinksSamples.scope.periodKey,
            )
        ),
        repository,
    )

    @Composable
    private fun Route(model: SubjectLinksViewModel) = Frame(WIDE_WIDTH, 1f, TALL_HEIGHT) {
        SubjectLinksSheetRoute(model, onOpen = {}, onLinkActions = {}, onAdd = {}, onClose = {}, onFailure = {})
    }

    @Composable
    private fun Sheet(
        state: SubjectLinksUiState,
        width: Dp = WIDE_WIDTH,
        fontScale: Float = 1f,
        height: Dp = TALL_HEIGHT,
    ) = Frame(width, fontScale, height) {
        SubjectLinksSheetContent(SubjectLinksSamples.scope.subjectName, state, actions)
    }

    /** Tall enough that the lazy list composes every row; the window is as tall (the class's qualifiers). */
    @Composable
    private fun Frame(width: Dp, fontScale: Float, height: Dp, content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            ItmoTheme(platformStyle = ItmoPlatformStyle.Material) {
                Box(Modifier.size(width, height)) { content() }
            }
        }
    }

    /** Applies votes as Backend would: the score moves by the change of the viewer's vote. */
    private class VotingRepository(snapshot: SubjectLinksSnapshot) : SubjectLinksRepository {
        private val state = MutableStateFlow<SubjectLinksState>(SubjectLinksState.Content(snapshot))
        private val restrictions = MutableStateFlow<List<UserRestriction>>(emptyList())

        fun link(id: String): SubjectLink = snapshot().let { it.mine + it.shared + it.previous }.first { it.id == id }

        private fun snapshot() = (state.value as SubjectLinksState.Content).snapshot

        override fun observe(scope: ResourceScope) = state
        override fun peek(scope: ResourceScope) = snapshot()
        override suspend fun refresh(scope: ResourceScope): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun save(
            scope: ResourceScope,
            id: String,
            category: LinkCategory,
            url: String,
            title: String?,
            visibility: LinkVisibility,
            flowId: Long?,
        ): AppResult<SubjectLink> = error("not used")
        override suspend fun delete(scope: ResourceScope, id: String): AppResult<Unit> = error("not used")
        override suspend fun pin(scope: ResourceScope, id: String?): AppResult<Unit> = error("not used")
        override suspend fun report(scope: ResourceScope, id: String, reason: ResourceReportReason, comment: String?) =
            error("not used")
        override fun observeRestrictions() = restrictions
        override suspend fun refreshRestrictions(): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun vote(scope: ResourceScope, id: String, value: Int): AppResult<Unit> {
            val voted: (SubjectLink) -> SubjectLink = { link ->
                if (link.id == id) link.copy(score = link.score - link.myVote + value, myVote = value) else link
            }
            val current = snapshot()
            state.value = SubjectLinksState.Content(
                current.copy(mine = current.mine.map(voted), shared = current.shared.map(voted), previous = current.previous.map(voted))
            )
            return AppResult.Success(Unit)
        }
    }

    private companion object {
        val WIDE_WIDTH = 411.dp
        val NARROW_WIDTH = 320.dp
        const val NARROW_FONT_SCALE = 1.3f
        val TALL_HEIGHT = 4000.dp
        val SHEET_HEIGHT = 802.dp
    }
}
