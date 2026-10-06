package dev.alllexey.itmowidgets.feature.social.ui.list

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.social.presentation.UserAction
import dev.alllexey.itmowidgets.feature.social.presentation.UserListItem
import dev.alllexey.itmowidgets.feature.social.presentation.UserRowUi
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class UserListTest {

    @Test
    fun rowsCarryTheirLabelledActionsAndTheBusyRowIsInert() = runComposeUiTest {
        val actions = mutableListOf<Pair<Int, UserAction>>()
        val opened = mutableListOf<Int>()
        var loadMores = 0
        setContent {
            ItmoTheme {
                UserList(
                    Items,
                    onOpen = { opened += it.isu },
                    onAction = { row, action -> actions += row.isu to action },
                    onLoadMore = { loadMores++ },
                )
            }
        }

        assertTouchTargets()
        onNode(isHeading() and hasText("Входящие")).assertExists()
        onNodeWithText("Принять").performClick()
        onNodeWithText("Отклонить").performClick()
        onNodeWithText("Отменить").assertIsNotEnabled()
        // On the avatar: the middle of a row with two buttons is the reject button.
        onNodeWithTag(UserListTestTags.row(1)).performTouchInput { click(centerLeft + Offset(AVATAR_CENTER, 0f)) }
        // The test window is 470 px tall: the later rows are below it.
        onNodeWithTag(UserListTestTags.LIST).performScrollToNode(hasText("Пригласить"))
        onNodeWithText("Пригласить").assertIsEnabled().performClick()
        onNodeWithTag(UserListTestTags.LIST).performScrollToNode(hasTestTag(UserListTestTags.LOAD_MORE))
        onNodeWithTag(UserListTestTags.LOAD_MORE).performClick()

        assertEquals(listOf(1 to UserAction.ACCEPT, 1 to UserAction.REJECT, 3 to UserAction.INVITE), actions)
        assertEquals(listOf(1), opened)
        assertEquals(1, loadMores)
    }

    @Test
    fun aClosedRowIsNoTargetAndAnEmptyNameShowsThePlaceholder() = runComposeUiTest {
        var opened = 0
        setContent {
            ItmoTheme {
                UserList(listOf(UserListItem.User(row(3, "").copy(opensProfile = false))), onOpen = { opened++ })
            }
        }

        onNodeWithText("Пользователь ИСУ 3").assertExists()
        assertEquals(0, onAllNodes(hasClickAction()).fetchSemanticsNodes().size)
        assertEquals(0, opened)
    }

    @Test
    fun theSamePersonTwiceDoesNotBreakTheList() = runComposeUiTest {
        val twice = listOf(UserListItem.User(row(1, "А")), UserListItem.User(row(1, "Б")))
        setContent { ItmoTheme { UserList(twice, onOpen = {}) } }

        onNodeWithText("А").assertExists()
        onNodeWithText("Б").assertExists()
    }

    private companion object {
        /** 16 dp padding plus half the 48 dp avatar, at the test window's density of 1. */
        const val AVATAR_CENTER = 40f

        val Items = listOf(
            UserListItem.Header(UiText.Dynamic("Входящие")),
            UserListItem.User(row(1, "Иванова Дарья").copy(primary = UserAction.ACCEPT, secondary = UserAction.REJECT)),
            UserListItem.User(row(2, "Соколов Артём").copy(primary = UserAction.CANCEL, busy = true)),
            UserListItem.User(row(3, "Петров Пётр").copy(primary = UserAction.INVITE, opensProfile = false)),
            UserListItem.LoadMore,
        )

        fun row(isu: Int, name: String) = UserRowUi(isu, name, null, UiText.Dynamic("$isu • M3234"))
    }
}
