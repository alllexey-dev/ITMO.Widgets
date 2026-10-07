package dev.alllexey.itmowidgets.feature.social.ui.reviews

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.alllexey.itmowidgets.core.reviews.TeacherReview
import dev.alllexey.itmowidgets.designsystem.components.groups.GroupPosition
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test
import kotlin.test.assertEquals
import dev.alllexey.itmowidgets.feature.social.ui.reviews.ReviewPreviewFixtures as F

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class TeacherReviewRowTest {

    @Test
    fun voteArrowsAre48DpTargetsAndReportTheirArrow() = runComposeUiTest {
        val votes = mutableListOf<Pair<String, Boolean>>()
        setContent { Row(F.named, actions = TeacherReviewActions(onVote = { id, up -> votes += id to up })) }

        listOf(UP, DOWN).forEach { label ->
            onNodeWithContentDescription(label)
                .assertWidthIsEqualTo(Target)
                .assertHeightIsEqualTo(Target)
                .performClick()
        }
        assertTouchTargets(Target)
        assertEquals(listOf(F.named.id to true, F.named.id to false), votes)
    }

    @Test
    fun theScoreOffersBothVotesToTalkBack() = runComposeUiTest {
        val votes = mutableListOf<Pair<String, Boolean>>()
        setContent { Row(F.anonymous, actions = TeacherReviewActions(onVote = { id, up -> votes += id to up })) }

        val actions = onNodeWithContentDescription("Рейтинг -3").fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]
        assertEquals(listOf(UP, DOWN), actions.map { it.label })
        actions.forEach { it.action() }

        assertEquals(listOf(F.anonymous.id to true, F.anonymous.id to false), votes)
    }

    @Test
    fun aVoteInFlightDisablesTheArrowsAndTheMenu() = runComposeUiTest {
        val votes = mutableListOf<Pair<String, Boolean>>()
        setContent { Row(F.anonymous, busy = true, actions = TeacherReviewActions(onVote = { id, up -> votes += id to up })) }

        onNodeWithContentDescription(UP).assertIsNotEnabled().performClick()
        onNodeWithContentDescription(ACTIONS).assertIsNotEnabled()
        onNodeWithContentDescription("Рейтинг -3").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.CustomActions))
        assertEquals(emptyList(), votes)
    }

    @Test
    fun withoutTheRightToVoteAZeroScoreIsLeftOutAndOthersStayReadOnly() = runComposeUiTest {
        var review by mutableStateOf(F.copy)
        setContent { Row(review, canVote = false) }

        onNodeWithContentDescription("Рейтинг 0").assertDoesNotExist()
        review = F.bareCopy
        waitForIdle()
        onNodeWithContentDescription("Рейтинг 2").assertExists()
        onNodeWithContentDescription(UP).assertDoesNotExist()
    }

    @Test
    fun onlyAnUnreportedCommunityReviewOffersReporting() = runComposeUiTest {
        val reports = mutableListOf<String>()
        var review by mutableStateOf(F.anonymous)
        setContent { Row(review, actions = TeacherReviewActions(onReport = { reports += it })) }

        onNodeWithContentDescription(ACTIONS).assertIsEnabled().performClick()
        onNodeWithText("Пожаловаться").performClick()
        assertEquals(listOf(F.anonymous.id), reports)

        for (other in listOf(F.reported, F.copy)) {
            review = other
            waitForIdle()
            onNodeWithContentDescription(ACTIONS).assertDoesNotExist()
        }
    }

    @Test
    fun aNamedAuthorIsAShortLinkWithTheFullNameForTalkBack() = runComposeUiTest {
        val opened = mutableListOf<Int>()
        setContent { Row(F.named, actions = TeacherReviewActions(onAuthor = { opened += it })) }

        onNodeWithText("Иванова А. С.", useUnmergedTree = true).assertExists()
        onNodeWithContentDescription("Иванова Анна Сергеевна")
            .assertHeightIsAtLeast(Target)
            .performClick()
        onNodeWithText("Вёл у автора", useUnmergedTree = true).assertExists()
        assertEquals(listOf(F.author.isu), opened)
    }

    @Test
    fun aCopyLinksItsSourceAndAnAnonymousReviewSaysSo() = runComposeUiTest {
        val sources = mutableListOf<String>()
        var review by mutableStateOf(F.copy)
        setContent { Row(review, actions = TeacherReviewActions(onSource = { sources += it })) }

        onNodeWithContentDescription("Источник: Reviews, Отзывы ПИ").performClick()
        assertEquals(listOf("https://example.org/reviews/1"), sources)

        review = F.oldCopy
        waitForIdle()
        onNodeWithContentDescription("Reviews").assertExists()
        onNodeWithText("До 2023", useUnmergedTree = true).assertExists()

        review = F.anonymous
        waitForIdle()
        onNodeWithText("Анонимный отзыв", useUnmergedTree = true).assertExists()
        onNodeWithText("Не подтверждён", useUnmergedTree = true).assertExists()
        assertTouchTargets(Target)
    }

    @Test
    fun theOwnReviewOffersEditAndDeleteAndShowsItsScoreOnlyOncePublished() = runComposeUiTest {
        var edits = 0
        var deletions = 0
        var review by mutableStateOf(F.ownRejected)
        setContent {
            ItmoTheme(platformStyle = ItmoPlatformStyle.Material) {
                OwnTeacherReviewRow(review, busy = false, actions = TeacherReviewActions(onEdit = { edits++ }, onDelete = { deletions++ }))
            }
        }

        onNodeWithText("Отклонён", useUnmergedTree = true).assertExists()
        onNodeWithText("Причина: В отзыве есть личные данные", useUnmergedTree = true).assertExists()
        onNodeWithContentDescription("Рейтинг 0").assertDoesNotExist()
        onNodeWithContentDescription(ACTIONS).performClick()
        onNodeWithText("Изменить").performClick()
        onNodeWithContentDescription(ACTIONS).performClick()
        onNodeWithText("Удалить").performClick()
        assertEquals(1 to 1, edits to deletions)

        review = F.ownPublished
        waitForIdle()
        onNodeWithText("Вёл у вас", useUnmergedTree = true).assertExists()
        onNodeWithContentDescription("Рейтинг 7").assertExists()
        onNodeWithContentDescription(UP).assertDoesNotExist()
        assertTouchTargets(Target)
    }

    @Composable
    private fun Row(
        review: TeacherReview,
        canVote: Boolean = true,
        busy: Boolean = false,
        actions: TeacherReviewActions = TeacherReviewActions(),
    ) {
        // Material on every platform, so the targets are its 48 dp, not the platform default's (44 pt on iOS).
        ItmoTheme(platformStyle = ItmoPlatformStyle.Material) {
            TeacherReviewRow(review, GroupPosition.Single, canVote = canVote, canReport = true, busy = busy, actions = actions)
        }
    }

    private companion object {
        const val UP = "Полезный отзыв"
        const val DOWN = "Бесполезный отзыв"
        const val ACTIONS = "Действия с отзывом"
        val Target = ItmoPlatformStyle.Material.minTouchTarget
    }
}
