package dev.alllexey.itmowidgets.feature.resources.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs
import dev.alllexey.itmowidgets.core.resources.SubjectLinksSnapshot
import dev.alllexey.itmowidgets.core.resources.SubjectLinksState
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.FakeSubjectLinksRepository
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
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import org.robolectric.annotation.Config

/**
 * The actions cases of the deleted `SubjectLinksVisualTest` on the Compose sheet, through the real view model where
 * the case acts: votes that keep the sheet open, `Мои баллы` only for a Google Sheet, the own score without arrows and
 * nothing for a private link, arrows hidden by a restriction, an own rejected link with its reason and own actions,
 * another student's link with pinning and reporting; then the confirmed delete, a deleted link, one action at a time
 * and long text at 320 dp and font scale 1.3. The looks are the `LinkActionsSheetContent_*` goldens; the clipboard,
 * the toast and the navigation are `LinkActionsBottomSheet`'s.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class LinkActionsSheetTest {

    private val effects = mutableListOf<String>()
    private var dismissed = 0
    private val hostEffects = LinkActionsActions(
        onOpen = { effects += "open:${it.id}" },
        onAuthor = { effects += "author:$it" },
        onCopy = { effects += "copy:${it.id}" },
        onScores = { effects += "scores:${it.id}" },
        onEdit = { effects += "edit:${it.id}" },
        onReport = { effects += "report:${it.id}" },
    )

    @Test
    fun actionsSheetVotesForOthersLinkAndStaysOpen() = runComposeUiTest {
        val repository = repository()
        repository.applyVotes()
        setContent { Route(model(repository), "tasks-flow") }

        fun assertScore(score: String, vote: Int) {
            waitForIdle()
            onNode(hasText(score)).assertExists()
            arrow("Полезная ссылка").apply { if (vote > 0) assertIsSelected() else assertIsNotSelected() }
            arrow("Бесполезная ссылка").apply { if (vote < 0) assertIsSelected() else assertIsNotSelected() }
        }

        // The vote sits by the title, above the first action; another student's link names its author.
        assertTrue(arrow("Бесполезная ссылка").getBoundsInRoot().bottom <= row(LinkAction.OPEN).getBoundsInRoot().top)
        onNodeWithText("Автор: Синтетический Автор").assertIsDisplayed()
        assertScore("0", 0)
        arrow("Полезная ссылка").performClick()
        assertScore("1", 1)
        arrow("Полезная ссылка").performClick()
        assertScore("0", 0)
        arrow("Бесполезная ссылка").performClick()
        assertScore("−1", -1)
        assertEquals(0, dismissed)
        assertTouchTargets()
        assertNoTextOverflow()
    }

    @Test
    fun actionsSheetOffersMyScoresOnlyForAGoogleSheet() = runComposeUiTest {
        setContent { Sheet(SubjectLinksSamples.content, "others-sheet") }

        assertTrue(row(LinkAction.OPEN).getBoundsInRoot().bottom <= row(LinkAction.SCORES).getBoundsInRoot().top)
        row(LinkAction.SCORES).getBoundsInRoot().let { assertTrue(it.bottom - it.top >= 48.dp) }
        onNodeWithText("Мои баллы").performClick()
        assertEquals(listOf("scores:others-sheet"), effects)
    }

    @Test
    fun aLinkThatIsNoGoogleSheetHasNoScoresRow() = runComposeUiTest {
        setContent { Sheet(SubjectLinksSamples.content, "tasks-flow") }

        assertEquals(listOf(LinkAction.OPEN, LinkAction.AUTHOR, LinkAction.COPY, LinkAction.PIN, LinkAction.REPORT), rows())
    }

    @Test
    fun actionsSheetShowsTheOwnScoreWithoutArrowsAndNothingForAPrivateLink() = runComposeUiTest {
        setContent { Sheet(SubjectLinksSamples.content, "own-scores") }

        onNode(hasContentDescription("Рейтинг 5")).assertExists()
        onAllNodesWithContentDescription("Полезная ссылка").assertCountEquals(0)
        onAllNodesWithContentDescription("Бесполезная ссылка").assertCountEquals(0)
    }

    @Test
    fun anOwnPrivateLinkHasNoScoreAndNoAuthor() = runComposeUiTest {
        setContent { Sheet(SubjectLinksSamples.content, "own-other") }

        onAllNodes(hasContentDescription("Рейтинг", substring = true)).assertCountEquals(0)
        onNodeWithTag(LinkActionsSheetTestTags.row(LinkAction.AUTHOR)).assertDoesNotExist()
        assertEquals(listOf(LinkAction.OPEN, LinkAction.COPY, LinkAction.PIN, LinkAction.EDIT, LinkAction.DELETE), rows())
    }

    @Test
    fun actionsSheetHidesArrowsUnderAVoteRestriction() = runComposeUiTest {
        setContent { Sheet(SubjectLinksSamples.restricted, "tasks-flow") }

        onNode(hasContentDescription("Рейтинг 0")).assertExists()
        onAllNodesWithContentDescription("Полезная ссылка").assertCountEquals(0)
        onAllNodesWithContentDescription("Бесполезная ссылка").assertCountEquals(0)
    }

    @Test
    fun ownRejectedLinkShowsTheReasonAndOwnActions() = runComposeUiTest {
        setContent { Sheet(SubjectLinksSamples.content, "own-rejected") }

        onNodeWithTag(LinkActionsSheetTestTags.NOTE).assertExists()
        onNodeWithText("Причина: Ссылка ведёт на другой предмет").assertIsDisplayed()
        onNodeWithText("отклонена", substring = true).assertIsDisplayed()
        assertEquals(listOf(LinkAction.OPEN, LinkAction.COPY, LinkAction.PIN, LinkAction.EDIT, LinkAction.DELETE), rows())
        onNode(hasContentDescription("Рейтинг 0")).assertExists()
        onAllNodesWithContentDescription("Полезная ссылка").assertCountEquals(0)
    }

    @Test
    fun othersLinkOffersPinningAndReporting() = runComposeUiTest {
        val repository = repository()
        setContent { Route(model(repository), "materials-all") }

        onNodeWithTag(LinkActionsSheetTestTags.NOTE).assertDoesNotExist()
        assertEquals(listOf(LinkAction.OPEN, LinkAction.AUTHOR, LinkAction.COPY, LinkAction.PIN, LinkAction.REPORT), rows())
        onNodeWithText("Открепить").performClick()
        waitForIdle()

        // Pinning the pinned link unpins it; the sheet closes once it succeeded.
        assertEquals(listOf("pin:null"), repository.actions)
        assertEquals(1, dismissed)
    }

    @Test
    fun reportingNeedsTheConnectionAndHappensOnce() = runComposeUiTest {
        val reported = SubjectLinksSamples.snapshot.let { snapshot ->
            snapshot.copy(shared = snapshot.shared.map { if (it.id == "materials-all") it.copy(reportedByMe = true) else it })
        }
        setContent { Sheet(SubjectLinksSamples.state(reported), "materials-all") }

        assertEquals(listOf(LinkAction.OPEN, LinkAction.AUTHOR, LinkAction.COPY, LinkAction.PIN), rows())
    }

    @Test
    fun withoutTheConnectionOthersLinksCanNeitherBePinnedNorReported() = runComposeUiTest {
        setContent { Sheet(SubjectLinksSamples.offline, "materials-all") }

        assertEquals(listOf(LinkAction.OPEN, LinkAction.AUTHOR, LinkAction.COPY), rows())
        onAllNodesWithContentDescription("Полезная ссылка").assertCountEquals(0)
    }

    @Test
    fun theRowsReachTheHostWithTheirLink() = runComposeUiTest {
        setContent { Route(model(repository()), "others-sheet") }

        listOf(LinkAction.OPEN, LinkAction.AUTHOR, LinkAction.COPY, LinkAction.SCORES, LinkAction.REPORT)
            .forEach { row(it).performClick() }

        assertEquals(
            listOf("open:others-sheet", "author:100001", "copy:others-sheet", "scores:others-sheet", "report:others-sheet"),
            effects,
        )
    }

    @Test
    fun deletingAsksFirst() = runComposeUiTest {
        val repository = repository()
        setContent { Route(model(repository), "own-scores") }

        row(LinkAction.DELETE).performClick()
        onNodeWithText("Удалить ссылку?").assertIsDisplayed()
        onNodeWithText("Отмена").performClick()
        waitForIdle()
        onNodeWithText("Удалить ссылку?").assertDoesNotExist()
        assertTrue(repository.actions.isEmpty())

        row(LinkAction.EDIT).performClick()
        row(LinkAction.DELETE).performClick()
        onNode(hasText("Удалить") and hasAnyAncestor(isDialog())).performClick()
        waitForIdle()

        assertEquals(listOf("edit:own-scores"), effects)
        assertEquals(listOf("delete:own-scores"), repository.actions)
        assertEquals(1, dismissed)
    }

    @Test
    fun aDeletedLinkClosesTheSheetUnlessAnActionIsPending() = runComposeUiTest {
        val repository = repository()
        val pending = CompletableDeferred<Unit>()
        repository.gate = { pending.await() }
        setContent { Route(model(repository), "own-other") }

        // An action in flight: the rows that act wait for it, the others still work.
        row(LinkAction.PIN).performClick()
        waitForIdle()
        row(LinkAction.EDIT).performClick()
        row(LinkAction.PIN).performClick()
        row(LinkAction.OPEN).performClick()
        assertEquals(listOf("pin:own-other"), repository.actions)
        assertEquals(listOf("open:own-other"), effects)

        repository.state.value = SubjectLinksState.Content(SubjectLinksSamples.snapshot.withoutOwn("own-other"))
        waitForIdle()
        assertEquals(0, dismissed)

        repository.result = AppResult.Failure(AppError.Network)
        pending.complete(Unit)
        waitForIdle()
        assertEquals(1, dismissed)
    }

    @Test
    fun aLinkDeletedElsewhereClosesTheSheet() = runComposeUiTest {
        val repository = repository()
        setContent { Route(model(repository), "own-other") }
        waitForIdle()
        assertEquals(0, dismissed)

        repository.state.value = SubjectLinksState.Content(SubjectLinksSamples.snapshot.withoutOwn("own-other"))
        waitForIdle()

        assertEquals(1, dismissed)
    }

    @Test
    @Config(qualifiers = "w320dp-h891dp")
    fun longTitlesFitAtLargeFontOnANarrowScreen() = runComposeUiTest {
        setContent { Sheet(SubjectLinksSamples.content, "own-rejected", NARROW_WIDTH, NARROW_FONT_SCALE) }

        onNodeWithText(SubjectLinksSamples.LONG_TITLE).assertIsDisplayed()
        assertNoTextOverflow()
        assertTouchTargets()
    }

    private fun ComposeUiTest.arrow(label: String): SemanticsNodeInteraction = onNode(hasContentDescription(label))

    private fun ComposeUiTest.row(action: LinkAction): SemanticsNodeInteraction =
        onNodeWithTag(LinkActionsSheetTestTags.row(action))

    /** The action rows on the sheet, in [LinkAction] (display) order. */
    private fun ComposeUiTest.rows(): List<LinkAction> = LinkAction.entries.filter {
        onAllNodes(hasTestTag(LinkActionsSheetTestTags.row(it))).fetchSemanticsNodes().isNotEmpty()
    }

    private fun repository() = FakeSubjectLinksRepository().apply {
        state.value = SubjectLinksState.Content(SubjectLinksSamples.snapshot)
    }

    /** Votes change the score as Backend would. */
    private fun FakeSubjectLinksRepository.applyVotes() {
        val fake = this
        gate = {
            val vote = fake.actions.last().split(':')
            if (vote[0] == "vote") {
                val (_, id, value) = vote
                val current = (fake.state.value as SubjectLinksState.Content).snapshot
                fake.state.value = SubjectLinksState.Content(
                    current.copy(shared = current.shared.map { link ->
                        if (link.id == id) link.copy(score = link.score - link.myVote + value.toInt(), myVote = value.toInt())
                        else link
                    }),
                )
            }
        }
    }

    private fun SubjectLinksSnapshot.withoutOwn(id: String) = copy(mine = mine.filterNot { it.id == id })

    private fun model(repository: FakeSubjectLinksRepository) = SubjectLinksViewModel(
        SavedStateHandle(
            mapOf(
                SubjectLinksArgs.SUBJECT_ID to SubjectLinksSamples.scope.subjectId,
                SubjectLinksArgs.SUBJECT_NAME to SubjectLinksSamples.scope.subjectName,
                SubjectLinksArgs.PERIOD_KEY to SubjectLinksSamples.scope.periodKey,
            ),
        ),
        repository,
    )

    @Composable
    private fun Route(model: SubjectLinksViewModel, linkId: String) = Frame(WIDE_WIDTH, 1f) {
        LinkActionsSheetRoute(model, linkId, hostEffects, onDismiss = { dismissed++ }, onFailure = {})
    }

    @Composable
    private fun Sheet(state: SubjectLinksUiState, linkId: String, width: Dp = WIDE_WIDTH, fontScale: Float = 1f) =
        Frame(width, fontScale) {
            LinkActionsSheetContent(
                state,
                linkId,
                LinkActionsActions(
                    onOpen = hostEffects.onOpen,
                    onScores = hostEffects.onScores,
                ),
            )
        }

    @Composable
    private fun Frame(width: Dp, fontScale: Float, content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            ItmoTheme(platformStyle = ItmoPlatformStyle.Material) {
                Box(Modifier.size(width, SHEET_HEIGHT)) { content() }
            }
        }
    }

    private companion object {
        val WIDE_WIDTH = 411.dp
        val NARROW_WIDTH = 320.dp
        const val NARROW_FONT_SCALE = 1.3f
        val SHEET_HEIGHT = 802.dp
    }
}
