package dev.alllexey.itmowidgets.feature.social.ui.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.testing.ownReview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.social.presentation.ProfileFactKind
import dev.alllexey.itmowidgets.feature.social.presentation.SocialBlock
import dev.alllexey.itmowidgets.feature.social.presentation.UserProfileUiState
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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

    @Test
    fun theReviewsListTheSummaryTheOwnReviewThenTheOthersAndWritingFollowsTheRight() = runComposeUiTest {
        var reviews by mutableStateOf(P.teacherReviews.copy(mine = ownReview(), canWrite = true, knownTeacher = true))
        val list = LazyListState()
        var writes = 0
        setContent {
            ItmoTheme {
                // Tall enough for every item, so the list composes the whole page.
                Box(Modifier.requiredHeight(TALL_PAGE)) {
                    UserProfileScreen(P.page(P.teacher, null, reviews), UserProfileActions(onWriteReview = { writes++ }), listState = list)
                }
            }
        }

        assertEquals(
            listOf("hero", "facts:POSITION", "facts:ROOM", "reviews:heading", "reviews:summary", "reviews:own") +
                P.teacherReviews.reviews.map { "review:${it.id}" },
            list.layoutInfo.visibleItemsInfo.map { it.key },
        )
        onNodeWithContentDescription("Отзывы, 4").assertExists()
        // An own review takes the place of writing a new one.
        onNodeWithTag(UserProfileTestTags.WRITE_REVIEW).assertDoesNotExist()

        reviews = reviews.copy(mine = null)
        waitForIdle()
        onNodeWithTag(UserProfileTestTags.WRITE_REVIEW).performClick()
        assertEquals(1, writes)

        reviews = reviews.copy(canWrite = false)
        waitForIdle()
        onNodeWithTag(UserProfileTestTags.WRITE_REVIEW).assertDoesNotExist()
    }

    @Test
    fun lateReviewsAppendUnderThePageWithoutMovingIt() = runComposeUiTest {
        var page by mutableStateOf(P.page(P.teacher, null))
        setContent { ItmoTheme { UserProfileScreen(page, UserProfileActions()) } }

        val hero = onNodeWithTag(UserProfileTestTags.HERO).getUnclippedBoundsInRoot()
        val positions = onNodeWithTag(UserProfileTestTags.facts(ProfileFactKind.POSITION)).getUnclippedBoundsInRoot()
        onNodeWithTag(UserProfileTestTags.REVIEWS).assertDoesNotExist()

        page = P.page(P.teacher, null, P.teacherReviews)
        waitForIdle()
        assertEquals(hero, onNodeWithTag(UserProfileTestTags.HERO).getUnclippedBoundsInRoot())
        assertEquals(positions, onNodeWithTag(UserProfileTestTags.facts(ProfileFactKind.POSITION)).getUnclippedBoundsInRoot())
        scrollTo(UserProfileTestTags.REVIEWS)
        onNodeWithContentDescription("Отзывы, 3").assertExists()
    }

    @Test
    fun aPersonWhoArrivesRevealsTheAvatarAndOneThereFromTheStartShowsAtOnce() = runComposeUiTest {
        var state by mutableStateOf(P.friendPage)
        mainClock.autoAdvance = false
        setContent { ItmoTheme { UserProfileScreen(state, UserProfileActions()) } }
        mainClock.advanceTimeByFrame()
        val shown = avatarWidth()

        state = UserProfileUiState.Loading
        Snapshot.sendApplyNotifications()
        mainClock.advanceTimeByFrame()
        state = P.friendPage
        Snapshot.sendApplyNotifications()
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        val growing = avatarWidth()
        assertTrue(growing < shown - Tolerance, "a person who arrives starts smaller: $growing of $shown")

        mainClock.advanceTimeBy(REVEAL_SETTLE_MILLIS)
        val revealed = avatarWidth()
        assertTrue(abs(shown.value - revealed.value) <= Tolerance.value, "a revealed avatar: $revealed of $shown")
    }

    /** The width of the hero avatar's initials, which scale with the avatar's reveal. */
    private fun ComposeUiTest.avatarWidth(): Dp =
        onNode(hasText(INITIALS) and hasAnyAncestor(hasTestTag(UserProfileTestTags.HERO)), useUnmergedTree = true)
            .getBoundsInRoot()
            .width

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
        val TALL_PAGE = 4000.dp

        /** The initials of [P.LONG_NAME], the preview person without a photo. */
        const val INITIALS = "АК"

        /** Pixel rounding of the initials' width. */
        val Tolerance = 1.dp

        /** Longer than the expressive spatial spring needs to settle. */
        const val REVEAL_SETTLE_MILLIS = 2_000L
        val FriendshipCases = listOf(
            FriendshipCase(RelationshipState.NONE, "Добавить в друзья"),
            FriendshipCase(RelationshipState.OUTGOING, "Отменить заявку", status = "Заявка отправлена"),
            FriendshipCase(RelationshipState.INCOMING, "Принять заявку", "Отклонить", status = "Хочет добавить вас"),
            FriendshipCase(RelationshipState.FRIENDS, primary = null, badge = "в друзьях"),
            FriendshipCase(RelationshipState.BLOCKED, primary = null),
        )
    }
}
