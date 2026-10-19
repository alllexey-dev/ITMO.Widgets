package dev.alllexey.itmowidgets.feature.social.ui.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.social.presentation.CardAction
import dev.alllexey.itmowidgets.feature.social.presentation.UserProfileUiState
import dev.alllexey.itmowidgets.feature.social.ui.preview.SocialSamples
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class UserProfileScreenTest {

    @Test
    fun theHeaderButtonsFollowWhoThePersonIsAndTheFirstIsTheFilledOne() = runComposeUiTest {
        var state by mutableStateOf(SocialSamples.card("app", "none"))
        val tapped = mutableListOf<CardAction>()
        setContent { ItmoTheme { UserProfileScreen(state, UserProfileActions(onAction = { tapped += it })) } }

        val cases = listOf(
            SocialSamples.card("app", "none") to listOf("Добавить в друзья"),
            SocialSamples.card("noapp") to listOf("Пригласить в ITMO.Widgets"),
            SocialSamples.card("teacher", teaches = "no") to emptyList(),
            SocialSamples.card("teacher", teaches = "yes") to listOf("Написать отзыв"),
            SocialSamples.card("both", "none", "yes") to listOf("Написать отзыв", "Добавить в друзья"),
            SocialSamples.incomingCard to listOf("Принять заявку", "Отклонить"),
            SocialSamples.ownCard to emptyList(),
            SocialSamples.card("app", "friend") to emptyList(),
        )
        for ((value, labels) in cases) {
            state = value
            waitForIdle()
            assertTouchTargets()
            val tags = listOf(UserProfileTestTags.PRIMARY, UserProfileTestTags.SECONDARY)
            tags.forEachIndexed { index, tag ->
                val label = labels.getOrNull(index)
                if (label == null) onNodeWithTag(tag).assertDoesNotExist() else onNodeWithTag(tag).assertTextEquals(label)
            }
        }

        state = SocialSamples.incomingCard
        waitForIdle()
        onNodeWithTag(UserProfileTestTags.PRIMARY).performClick()
        onNodeWithTag(UserProfileTestTags.SECONDARY).performClick()
        assertEquals(listOf(CardAction.ACCEPT_REQUEST, CardAction.REJECT_REQUEST), tapped)
    }

    @Test
    fun anActionInFlightDisablesTheButtons() = runComposeUiTest {
        val busy = (SocialSamples.incomingCard as UserProfileUiState.Content).copy(busy = true)
        setContent { ItmoTheme { UserProfileScreen(busy, UserProfileActions()) } }

        onNodeWithTag(UserProfileTestTags.PRIMARY).assertIsNotEnabled()
        onNodeWithTag(UserProfileTestTags.SECONDARY).assertIsNotEnabled()
    }

    @Test
    fun onlyTheRowsBackendOpensLeadOnAndAFriendIsRemovedFromTheCard() = runComposeUiTest {
        var state by mutableStateOf(SocialSamples.card("app", "none"))
        val calls = mutableListOf<String>()
        setContent {
            ItmoTheme {
                UserProfileScreen(
                    state,
                    UserProfileActions(
                        onSchedule = { calls += "schedule" },
                        onSport = { calls += "sport" },
                        onFriends = { calls += "friends" },
                        onRemoveFriend = { calls += "remove" },
                    ),
                )
            }
        }

        for (tag in listOf(UserProfileTestTags.SCHEDULE, UserProfileTestTags.SPORT, UserProfileTestTags.FRIENDS, UserProfileTestTags.REMOVE)) {
            onNodeWithTag(tag).assertDoesNotExist()
        }
        // Someone else's hidden data is simply absent: no hint, no lock.
        onNodeWithText("Обычно открывается после добавления в друзья").assertDoesNotExist()

        state = SocialSamples.card("app", "friend")
        waitForIdle()
        for (tag in listOf(UserProfileTestTags.SCHEDULE, UserProfileTestTags.SPORT, UserProfileTestTags.FRIENDS, UserProfileTestTags.REMOVE)) {
            scrollTo(tag)
            onNodeWithTag(tag).performClick()
        }
        assertEquals(listOf("schedule", "sport", "friends", "remove"), calls)
    }

    @Test
    fun whoTeachesTheViewerSeesTheTeachingBlockRightUnderTheHeader() = runComposeUiTest {
        setContent { ItmoTheme { UserProfileScreen(SocialSamples.card("both", "friend", "yes"), UserProfileActions()) } }

        val header = onNodeWithTag(UserProfileTestTags.HERO).getUnclippedBoundsInRoot()
        val teaching = onNodeWithTag(UserProfileTestTags.TEACHING).getUnclippedBoundsInRoot()
        assertTrue(teaching.top > header.bottom)
        // The lesson now is the block's hero, then what they teach the viewer.
        onNodeWithText("Сейчас ведёт пару до 13:10").assertExists()
        assertTrue(onNodeWithTag(UserProfileTestTags.NOW).getUnclippedBoundsInRoot().top > teaching.top)
        onNodeWithTag(UserProfileTestTags.LIST).performScrollToNode(hasText("Ведёт у вас", substring = true))
        onNodeWithText("Ведёт у вас", useUnmergedTree = true).assertExists()
    }

    @Test
    fun theStatesOfferRetryOnlyForAFailureAndShareOnlyForACard() = runComposeUiTest {
        var state: UserProfileUiState by mutableStateOf(UserProfileUiState.Loading)
        var retries = 0
        var shares = 0
        setContent {
            ItmoTheme { UserProfileScreen(state, UserProfileActions(onRetry = { retries++ }, onShare = { shares++ })) }
        }

        onNodeWithTag(UserProfileTestTags.LOADING).assertExists()
        onNodeWithTag(UserProfileTestTags.SHARE).assertDoesNotExist()

        state = SocialSamples.notFound
        waitForIdle()
        onNodeWithText("Профиль не найден").assertExists()
        onNodeWithText("Повторить").assertDoesNotExist()

        state = SocialSamples.failed
        waitForIdle()
        onNodeWithText("Повторить").performClick()

        state = SocialSamples.card()
        waitForIdle()
        onNodeWithTag(UserProfileTestTags.SHARE).performClick()
        assertEquals(1, retries)
        assertEquals(1, shares)
    }

    @Test
    fun theIsuLineCopiesTheNumber() = runComposeUiTest {
        val copied = mutableListOf<Int>()
        setContent { ItmoTheme { UserProfileScreen(SocialSamples.card(), UserProfileActions(onCopyIsu = { copied += it })) } }

        onNodeWithTag(UserProfileTestTags.ISU).performClick()

        assertEquals(listOf(288104), copied)
    }

    @Test
    fun aTeachersReviewsFollowTheirSummary() = runComposeUiTest {
        setContent { ItmoTheme { UserProfileScreen(SocialSamples.card("teacher"), UserProfileActions()) } }

        scrollTo(UserProfileTestTags.REVIEWS)
        onNodeWithTag(UserProfileTestTags.REVIEWS).assertExists()
        onNodeWithText("Где найти", useUnmergedTree = true).assertExists()
    }

    private fun ComposeUiTest.scrollTo(tag: String) {
        onNodeWithTag(UserProfileTestTags.LIST).performScrollToNode(hasTestTag(tag))
    }
}
