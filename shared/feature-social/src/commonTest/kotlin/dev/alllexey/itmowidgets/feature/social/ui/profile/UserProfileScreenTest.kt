package dev.alllexey.itmowidgets.feature.social.ui.profile

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.social.presentation.SocialBlock
import dev.alllexey.itmowidgets.feature.social.presentation.UserProfileUiState
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test
import kotlin.test.assertEquals
import dev.alllexey.itmowidgets.feature.social.ui.profile.preview.UserProfilePreviewData as P

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class UserProfileScreenTest {

    @Test
    fun theFriendshipOffersTheButtonsOfItsRelationship() = runComposeUiTest {
        var state by mutableStateOf(P.studentPage(RelationshipState.NONE))
        val calls = mutableListOf<String>()
        setContent {
            ItmoTheme {
                UserProfileScreen(
                    state,
                    UserProfileActions(
                        onPrimaryAction = { calls += "primary" },
                        onSecondaryAction = { calls += "secondary" },
                    ),
                )
            }
        }

        for (case in FriendshipCases) {
            state = P.studentPage(case.relationship)
            waitForIdle()
            assertTouchTargets()
            assertButton(UserProfileTestTags.PRIMARY, case.primary)
            assertButton(UserProfileTestTags.SECONDARY, case.secondary)
            assertButton(UserProfileTestTags.BADGE, case.badge)
            case.status?.let { onNodeWithText(it).assertExists() }
            case.primary?.let { onNodeWithTag(UserProfileTestTags.PRIMARY).assertIsEnabled().performClick() }
            case.secondary?.let { onNodeWithTag(UserProfileTestTags.SECONDARY).assertIsEnabled().performClick() }
        }

        assertEquals(listOf("primary", "primary", "primary", "secondary"), calls)
    }

    @Test
    fun theViewerSeesTheirBadgeAndNoFriendshipAction() = runComposeUiTest {
        setContent { ItmoTheme { UserProfileScreen(P.selfPage, UserProfileActions()) } }

        onNodeWithTag(UserProfileTestTags.BADGE).assertTextEquals("это вы")
        onNodeWithTag(UserProfileTestTags.PRIMARY).assertDoesNotExist()
        onNodeWithTag(UserProfileTestTags.SECONDARY).assertDoesNotExist()
        scrollTo(UserProfileTestTags.SPORT)
        onNodeWithTag(UserProfileTestTags.REMOVE).assertDoesNotExist()
        onNodeWithTag(UserProfileTestTags.HIDDEN_HINT).assertDoesNotExist()
    }

    @Test
    fun aRequestInFlightDisablesEveryFriendshipButton() = runComposeUiTest {
        var state by mutableStateOf(P.studentPage(RelationshipState.INCOMING, busy = true))
        setContent { ItmoTheme { UserProfileScreen(state, UserProfileActions()) } }

        onNodeWithTag(UserProfileTestTags.PRIMARY).assertIsNotEnabled()
        onNodeWithTag(UserProfileTestTags.SECONDARY).assertIsNotEnabled()

        state = P.studentPage(RelationshipState.FRIENDS, busy = true)
        waitForIdle()
        scrollTo(UserProfileTestTags.REMOVE)
        onNodeWithTag(UserProfileTestTags.REMOVE).assertIsNotEnabled()
    }

    @Test
    fun onlyTheRowsBackendSharesLeadOn() = runComposeUiTest {
        var state by mutableStateOf(
            P.studentPage(RelationshipState.NONE, UserSharing(sport = false, schedule = true, friends = false)),
        )
        val opened = mutableListOf<String>()
        setContent {
            ItmoTheme {
                UserProfileScreen(
                    state,
                    UserProfileActions(
                        onFriends = { opened += "friends" },
                        onSchedule = { opened += "schedule" },
                        onSport = { opened += "sport" },
                        onRemoveFriend = { opened += "remove" },
                    ),
                )
            }
        }

        scrollTo(UserProfileTestTags.HIDDEN_HINT)
        onNodeWithTag(UserProfileTestTags.FRIENDS).assertHasNoClickAction()
        onNodeWithTag(UserProfileTestTags.SPORT).assertHasNoClickAction()
        onNodeWithTag(UserProfileTestTags.SCHEDULE).assertHasClickAction().performClick()
        onNodeWithTag(UserProfileTestTags.REMOVE).assertDoesNotExist()
        assertTouchTargets()

        state = P.studentPage(RelationshipState.FRIENDS)
        waitForIdle()
        scrollTo(UserProfileTestTags.REMOVE)
        onNodeWithTag(UserProfileTestTags.HIDDEN_HINT).assertDoesNotExist()
        for (tag in listOf(UserProfileTestTags.FRIENDS, UserProfileTestTags.SCHEDULE, UserProfileTestTags.SPORT)) {
            onNodeWithTag(tag).performClick()
        }
        onNodeWithTag(UserProfileTestTags.REMOVE).assertIsEnabled().performClick()

        assertEquals(listOf("schedule", "friends", "schedule", "sport", "remove"), opened)
    }

    @Test
    fun theViewerAlwaysOpensTheirOwnScheduleAndSport() = runComposeUiTest {
        val closed = SocialBlock(P.studentProfile(RelationshipState.FRIENDS, P.closedSharing), isSelf = true, busy = false)
        setContent { ItmoTheme { UserProfileScreen(P.page(P.student, closed), UserProfileActions()) } }

        scrollTo(UserProfileTestTags.SPORT)
        onNodeWithTag(UserProfileTestTags.FRIENDS).assertHasNoClickAction()
        onNodeWithTag(UserProfileTestTags.SCHEDULE).assertHasClickAction()
        onNodeWithTag(UserProfileTestTags.SPORT).assertHasClickAction()
    }

    @Test
    fun withoutTheConnectionThePageHasNoFriendshipOrSharing() = runComposeUiTest {
        val list = LazyListState()
        setContent { ItmoTheme { UserProfileScreen(P.disabledPage, UserProfileActions(), listState = list) } }

        onNodeWithTag(UserProfileTestTags.PRIMARY).assertDoesNotExist()
        onNodeWithTag(UserProfileTestTags.BADGE).assertDoesNotExist()
        // The hero and `Учёба` only.
        assertEquals(2, list.layoutInfo.totalItemsCount)
    }

    @Test
    fun theStatesOfferRetryOnlyForAFailureAndShareOnlyForAPage() = runComposeUiTest {
        var state by mutableStateOf<UserProfileUiState>(UserProfileUiState.Loading)
        var retries = 0
        var shares = 0
        var backs = 0
        setContent {
            ItmoTheme {
                UserProfileScreen(
                    state,
                    UserProfileActions(onRetry = { retries++ }, onShare = { shares++ }, onBack = { backs++ }),
                )
            }
        }

        onNodeWithTag(UserProfileTestTags.LOADING).assertExists()
        onNodeWithTag(UserProfileTestTags.SHARE).assertDoesNotExist()
        assertTouchTargets()

        state = UserProfileUiState.Error(AppError.NotFound)
        waitForIdle()
        onNodeWithText("Профиль не найден").assertExists()
        onNodeWithText("Повторить").assertDoesNotExist()
        onNodeWithTag(UserProfileTestTags.SHARE).assertDoesNotExist()

        state = UserProfileUiState.Error(AppError.Network)
        waitForIdle()
        onNodeWithText("Не удалось загрузить").assertExists()
        onNodeWithText("Повторить").performClick()
        onNodeWithTag(UserProfileTestTags.SHARE).assertDoesNotExist()
        assertTouchTargets()

        state = P.friendPage
        waitForIdle()
        onNodeWithTag(UserProfileTestTags.LOADING).assertDoesNotExist()
        onNodeWithTag(UserProfileTestTags.STATE).assertDoesNotExist()
        onNodeWithTag(UserProfileTestTags.SHARE).performClick()
        onNodeWithContentDescription("Назад").performClick()

        assertEquals(1, retries)
        assertEquals(1, shares)
        assertEquals(1, backs)
    }

    @Test
    fun theIsuLineCopiesTheNumberAndReadsAsOneAction() = runComposeUiTest {
        val copied = mutableListOf<Int>()
        setContent { ItmoTheme { UserProfileScreen(P.friendPage, UserProfileActions(onCopyIsu = { copied += it })) } }

        onNodeWithContentDescription("Номер ИСУ ${P.ISU}, скопировать").assertHasClickAction()
        onNodeWithTag(UserProfileTestTags.ISU).performClick()
        assertTouchTargets()

        assertEquals(listOf(P.ISU), copied)
    }

    @Test
    fun theReviewsSectionFollowsItsSwitch() = runComposeUiTest {
        var enabled by mutableStateOf(true)
        val list = LazyListState()
        setContent {
            ItmoTheme { UserProfileScreen(P.teacherPage, UserProfileActions(), reviewsEnabled = enabled, listState = list) }
        }

        // The hero, `Должности`, `Где найти`, the heading, the summary and three reviews.
        assertEquals(8, list.layoutInfo.totalItemsCount)
        scrollTo(UserProfileTestTags.REVIEWS)
        onNodeWithContentDescription("Отзывы, 3").assertExists()
        onNodeWithTag(UserProfileTestTags.WRITE_REVIEW).assertDoesNotExist()

        enabled = false
        waitForIdle()
        assertEquals(3, list.layoutInfo.totalItemsCount)
    }

    private fun ComposeUiTest.scrollTo(tag: String) {
        onNodeWithTag(UserProfileTestTags.LIST).performScrollToNode(hasTestTag(tag))
    }

    private fun ComposeUiTest.assertButton(tag: String, text: String?) {
        if (text == null) onNodeWithTag(tag).assertDoesNotExist() else onNodeWithTag(tag).assertTextEquals(text)
    }

    private class FriendshipCase(
        val relationship: RelationshipState,
        val primary: String?,
        val secondary: String? = null,
        val status: String? = null,
        val badge: String? = null,
    )

    private companion object {
        val FriendshipCases = listOf(
            FriendshipCase(RelationshipState.NONE, "Добавить в друзья"),
            FriendshipCase(RelationshipState.OUTGOING, "Отменить заявку", status = "Заявка отправлена"),
            FriendshipCase(RelationshipState.INCOMING, "Принять заявку", "Отклонить", status = "Хочет добавить вас"),
            FriendshipCase(RelationshipState.FRIENDS, primary = null, badge = "в друзьях"),
            FriendshipCase(RelationshipState.BLOCKED, primary = null),
        )
    }
}
