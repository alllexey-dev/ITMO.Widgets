package dev.alllexey.itmowidgets.designsystem.components.rows

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.components.groups.GroupPosition
import dev.alllexey.itmowidgets.designsystem.components.groups.connectedGroupItem
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertNoTextOverflow
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class LinkRowTest {
    @Test
    fun theRowOpensOnTapAndShowsItsActionsOnALongPress() = runComposeUiTest {
        var opened = 0
        var actions = 0
        setContent {
            ItmoTheme {
                LinkRow(
                    TITLE,
                    onClick = { opened++ },
                    caption = CAPTION,
                    ownBadge = OWN,
                    onLongClick = { actions++ },
                    longClickLabel = ACTIONS,
                )
            }
        }

        val row = onNodeWithText(TITLE).assertHeightIsAtLeast(56.dp)
        assertEquals(ACTIONS, row.fetchSemanticsNode().config[SemanticsActions.OnLongClick].label)
        row.performClick()
        row.performTouchInput { longClick() }

        assertEquals(1, opened)
        assertEquals(1, actions)
    }

    @Test
    fun titleCaptionAndBadgeAreReadOnce() = runComposeUiTest {
        setContent { ItmoTheme { LinkRow(TITLE, onClick = {}, caption = CAPTION, ownBadge = OWN) } }

        val row = onNodeWithText(TITLE).fetchSemanticsNode().id
        assertEquals(row, onNodeWithText(CAPTION).fetchSemanticsNode().id)
        assertEquals(row, onNodeWithText(OWN).fetchSemanticsNode().id)
    }

    @Test
    fun theVoteArrowsStayOwnTargetsAndTheRowCarriesTheVotes() = runComposeUiTest {
        val votes = mutableListOf<Vote>()
        setContent {
            ItmoTheme {
                LinkRow(
                    TITLE,
                    onClick = {},
                    modifier = Modifier.connectedGroupItem(GroupPosition.Single),
                    icon = ColorPainter(Color.Black),
                    caption = CAPTION,
                    votes = { VotePill(4, myVote = null, SCORE, onVote = { votes += it }, VoteLabels(UP, DOWN)) },
                )
            }
        }

        onNodeWithContentDescription(UP).performClick()
        val rowActions = onNodeWithText(TITLE).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf(UP, DOWN), rowActions.map { it.label })
        rowActions.last().action()

        assertEquals(listOf(Vote.Up, Vote.Down), votes)
        assertTouchTargets()
    }

    @Test
    fun longTitlesWrapBesideTheVotesOnANarrowScreenAtLargeFont() = runComposeUiTest {
        setContent {
            Narrow {
                LinkRow(
                    LONG_TITLE,
                    onClick = {},
                    modifier = Modifier.width(NARROW_ROW_DP.dp),
                    icon = ColorPainter(Color.Black),
                    caption = LONG_CAPTION,
                    votes = { VotePill(-12, myVote = Vote.Down, SCORE, onVote = {}, VoteLabels(UP, DOWN)) },
                )
            }
        }

        assertNoTextOverflow()
        assertTrue(lineCount(LONG_TITLE) > 1, "the title wraps")
        val title = onNodeWithText(LONG_TITLE, useUnmergedTree = true).getBoundsInRoot()
        val up = onNodeWithContentDescription(UP).getBoundsInRoot()
        assertTrue(title.right <= up.left, "the title ends before the pill: $title, $up")
        assertTouchTargets()
    }

    @Test
    fun longTitlesWrapBesideTheOwnBadge() = runComposeUiTest {
        setContent {
            Narrow {
                LinkRow(LONG_TITLE, onClick = {}, modifier = Modifier.width(NARROW_ROW_DP.dp), ownBadge = OWN)
            }
        }

        assertNoTextOverflow()
        assertTrue(lineCount(LONG_TITLE) > 1, "the title wraps")
    }

    private fun androidx.compose.ui.test.ComposeUiTest.lineCount(text: String): Int {
        val layouts = mutableListOf<TextLayoutResult>()
        onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.single().lineCount
    }

    @Composable
    private fun Narrow(content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, LARGE_FONT)) {
            // A tall row may exceed the test window; a screen scrolls it.
            ItmoTheme { Column(Modifier.verticalScroll(rememberScrollState())) { content() } }
        }
    }

    private companion object {
        const val TITLE = "Баллы потока"
        const val CAPTION = "docs.google.com"
        const val OWN = "моя"
        const val ACTIONS = "Действия со ссылкой"
        const val UP = "Полезная ссылка"
        const val DOWN = "Бесполезная ссылка"
        const val SCORE = "Рейтинг ссылки 4"
        const val LONG_TITLE = "Математический анализ и дифференциальные уравнения в частных производных"
        const val LONG_CAPTION = "Преображенская Александра Вячеславовна, P3119"
        const val LARGE_FONT = 1.3f

        /** A 320 dp screen less its 16 dp margins: a connected group's row. */
        const val NARROW_ROW_DP = 288
    }
}
