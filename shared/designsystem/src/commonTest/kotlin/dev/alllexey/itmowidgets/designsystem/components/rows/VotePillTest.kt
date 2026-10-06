package dev.alllexey.itmowidgets.designsystem.components.rows

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.testkit.MinTouchTarget
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class VotePillTest {
    @Test
    fun bothArrowsAreLabelled48DpTargets() = runComposeUiTest {
        setContent { ItmoTheme { VotePill(12, myVote = null, SCORE, onVote = {}, Labels) } }

        listOf(UP, DOWN).forEach { label ->
            onNodeWithContentDescription(label)
                .assertWidthIsEqualTo(MinTouchTarget)
                .assertHeightIsEqualTo(MinTouchTarget)
        }
        assertTouchTargets()
    }

    @Test
    fun theArrowsReportTheirVoteAndShowTheOwnOne() = runComposeUiTest {
        val votes = mutableListOf<Vote>()
        setContent { ItmoTheme { VotePill(3, myVote = Vote.Up, SCORE, onVote = { votes += it }, Labels) } }

        onNodeWithContentDescription(UP).assertIsSelected().performClick()
        onNodeWithContentDescription(DOWN).assertIsNotSelected().performClick()

        assertEquals(listOf(Vote.Up, Vote.Down), votes)
    }

    @Test
    fun theScoreOffersBothVotesAsCustomActions() = runComposeUiTest {
        val votes = mutableListOf<Vote>()
        setContent { ItmoTheme { VotePill(3, myVote = null, SCORE, onVote = { votes += it }, Labels) } }

        val actions = onNodeWithContentDescription(SCORE).fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]
        assertEquals(listOf(UP, DOWN), actions.map { it.label })
        actions.forEach { it.action() }

        assertEquals(listOf(Vote.Up, Vote.Down), votes)
    }

    @Test
    fun aDisabledPillIgnoresTapsAndOffersNoActions() = runComposeUiTest {
        val votes = mutableListOf<Vote>()
        setContent {
            ItmoTheme { VotePill(3, myVote = null, SCORE, onVote = { votes += it }, Labels, enabled = false) }
        }

        onNodeWithContentDescription(UP).assertIsNotEnabled().performClick()
        onNodeWithContentDescription(SCORE).assert(SemanticsMatcher.keyNotDefined(SemanticsActions.CustomActions))

        assertEquals(emptyList(), votes)
    }

    @Test
    fun withoutVotingOnlyTheScoreIsShown() = runComposeUiTest {
        setContent { ItmoTheme { VotePill(7, myVote = null, SCORE, onVote = null, Labels) } }

        onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(UP)))
            .assertCountEquals(0)
        onNodeWithContentDescription(SCORE).assertHasNoClickAction()
        assertTouchTargets()
    }

    @Test
    fun negativeScoresUseATrueMinusSign() {
        assertEquals("\u22125", formatScore(-5))
        assertEquals("0", formatScore(0))
        assertEquals("12", formatScore(12))
    }

    private companion object {
        const val UP = "Полезная ссылка"
        const val DOWN = "Бесполезная ссылка"
        const val SCORE = "Рейтинг ссылки 3"
        val Labels = VoteLabels(UP, DOWN)
    }
}
