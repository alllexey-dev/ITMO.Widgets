package dev.alllexey.itmowidgets.feature.social.ui.friends

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.social.presentation.FriendLists
import dev.alllexey.itmowidgets.feature.social.presentation.PeopleSearchResults
import dev.alllexey.itmowidgets.feature.social.presentation.UserAction
import dev.alllexey.itmowidgets.feature.social.ui.list.UserListTestTags
import dev.alllexey.itmowidgets.feature.social.ui.preview.SocialSamples
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class FriendsScreenTest {

    private val list = SocialSamples.friends("list")
    private val lists = list.lists as FriendLists.Content
    private val incoming = lists.incoming.single()
    private val outgoing = lists.outgoing.single()
    private val friend = lists.friends.first()

    @Test
    fun requestsAnswerInTheRowSentOnesCancelAndFriendsOpenTheirCard() = runComposeUiTest {
        val calls = mutableListOf<String>()
        setContent {
            ItmoTheme {
                FriendsScreen(
                    list,
                    FriendsActions(
                        onAction = { row, action -> calls += "${action.name}:${row.isu}" },
                        onOpenProfile = { calls += "open:$it" },
                        onShareProfile = { name, isu -> calls += "share:$name:$isu" },
                    ),
                )
            }
        }

        onNodeWithTag(UserListTestTags.action(incoming.isu, UserAction.ACCEPT)).performClick()
        onNodeWithTag(UserListTestTags.action(incoming.isu, UserAction.REJECT)).performClick()
        onNodeWithTag(UserListTestTags.action(outgoing.isu, UserAction.CANCEL)).performClick()
        scrollTo(UserListTestTags.row(friend.isu))
        onNodeWithTag(UserListTestTags.row(friend.isu)).performClick()
        scrollTo(FriendsTestTags.SHARE)
        onNodeWithTag(FriendsTestTags.SHARE).performClick()

        assertEquals(
            listOf(
                "ACCEPT:${incoming.isu}", "REJECT:${incoming.isu}", "CANCEL:${outgoing.isu}", "open:${friend.isu}",
                "share:Александрова Мария:284731",
            ),
            calls,
        )
        // Removing a friend lives in their card: the list offers no removal.
        onNodeWithText("Удалить").assertDoesNotExist()
    }

    @Test
    fun aBusyRowKeepsItsButtonsInert() = runComposeUiTest {
        val busy = list.copy(lists = lists.copy(incoming = listOf(incoming.copy(busy = true))))
        setContent { ItmoTheme { FriendsScreen(busy, FriendsActions()) } }

        onNodeWithTag(UserListTestTags.action(incoming.isu, UserAction.ACCEPT)).assertIsNotEnabled()
        onNodeWithTag(UserListTestTags.action(incoming.isu, UserAction.REJECT)).assertIsNotEnabled()
    }

    @Test
    fun everyStateKeepsTouchTargetsAndLeadsOn() = runComposeUiTest {
        var state by mutableStateOf(list)
        val calls = mutableListOf<String>()
        setContent {
            ItmoTheme {
                FriendsScreen(
                    state,
                    FriendsActions(
                        onConnect = { calls += "connect" },
                        onRetry = { calls += "retry" },
                        onRetrySearch = { calls += "retry-search" },
                        onLoadMore = { calls += "more" },
                        onShareProfile = { _, _ -> calls += "share" },
                    ),
                )
            }
        }

        for (value in listOf(list, SocialSamples.friends("empty"), SocialSamples.search, SocialSamples.friendsDisabled)) {
            state = value
            waitForIdle()
            assertTouchTargets()
        }

        state = SocialSamples.friendsDisabled
        waitForIdle()
        onNodeWithTag(FriendsTestTags.SEARCH).assertDoesNotExist()
        onNodeWithText("Подключить").performClick()

        state = list.copy(lists = FriendLists.Error(AppError.Network))
        waitForIdle()
        onNodeWithText("Повторить").performClick()

        state = SocialSamples.friends("empty")
        waitForIdle()
        onNodeWithText("Пока никого").assertExists()
        onNodeWithText("Поделиться профилем").performClick()

        state = SocialSamples.search
        waitForIdle()
        onNodeWithText("В ITMO.Widgets").assertExists()
        onNodeWithText("Остальные").assertExists()
        // The found people stand in place of the lists.
        onNodeWithTag(UserListTestTags.row(outgoing.isu)).assertDoesNotExist()
        scrollTo(UserListTestTags.LOAD_MORE)
        onNodeWithTag(UserListTestTags.LOAD_MORE).performClick()

        state = SocialSamples.search.copy(search = PeopleSearchResults.Error(AppError.Network))
        waitForIdle()
        onNodeWithText("Повторить").performClick()

        assertEquals(listOf("connect", "retry", "share", "more", "retry-search"), calls)
    }

    private fun ComposeUiTest.scrollTo(tag: String) {
        onNodeWithTag(FriendsTestTags.LIST).performScrollToNode(hasTestTag(tag))
    }
}
