package dev.alllexey.itmowidgets.feature.social.ui.friends

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.social.presentation.FriendsEmpty
import dev.alllexey.itmowidgets.feature.social.presentation.FriendsEvent
import dev.alllexey.itmowidgets.feature.social.presentation.FriendsTab
import dev.alllexey.itmowidgets.feature.social.presentation.FriendsUiState
import dev.alllexey.itmowidgets.feature.social.presentation.UserAction
import dev.alllexey.itmowidgets.feature.social.presentation.UserListItem
import dev.alllexey.itmowidgets.feature.social.presentation.UserRowUi
import dev.alllexey.itmowidgets.feature.social.ui.list.UserListTestTags
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class FriendsScreenTest {

    @Test
    fun theTabsSwitchAndTheRequestsTabCarriesTheIncomingCount() = runComposeUiTest {
        var state by mutableStateOf(friends(incomingCount = 3))
        val selected = mutableListOf<FriendsTab>()
        setContent { ItmoTheme { Screen(state, onSelectTab = { selected += it }) } }

        onNodeWithTag(FriendsTestTags.tab(FriendsTab.FRIENDS)).assertIsSelected()
        onNodeWithTag(FriendsTestTags.tab(FriendsTab.REQUESTS)).assertIsNotSelected()
        onNodeWithTag(FriendsTestTags.BADGE, useUnmergedTree = true).assertExists()
        onNodeWithText("3", useUnmergedTree = true).assertExists()

        onNodeWithTag(FriendsTestTags.tab(FriendsTab.REQUESTS)).performClick()
        onNodeWithTag(FriendsTestTags.tab(FriendsTab.FRIENDS)).performClick()
        assertEquals(listOf(FriendsTab.REQUESTS, FriendsTab.FRIENDS), selected)

        state = requests(incomingCount = 0)
        waitForIdle()
        onNodeWithTag(FriendsTestTags.tab(FriendsTab.REQUESTS)).assertIsSelected()
        onNodeWithTag(FriendsTestTags.BADGE, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun aLongCountIsCappedLikeBadgeDrawable() = runComposeUiTest {
        setContent { ItmoTheme { Screen(friends(incomingCount = 1000)) } }

        onNodeWithText("999+", useUnmergedTree = true).assertExists()
    }

    @Test
    fun theBadgeStaysClearOfTheRequestsLabel() = runComposeUiTest {
        setContent { ItmoTheme { Screen(friends(incomingCount = 12)) } }

        val label = onNodeWithText("Заявки", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val badge = onNodeWithTag(FriendsTestTags.BADGE, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(badge.left >= label.right, "badge $badge overlaps the label $label")
    }

    @Test
    fun rowActionsAndTapsReachTheirCallbacks() = runComposeUiTest {
        val actions = mutableListOf<Pair<Int, UserAction>>()
        val opened = mutableListOf<Int>()
        setContent {
            ItmoTheme {
                Screen(requests(), onAction = { row, action -> actions += row.isu to action }, onOpenProfile = { opened += it })
            }
        }

        onNodeWithText("Принять").performClick()
        onNodeWithText("Отклонить").performClick()
        onNodeWithTag(UserListTestTags.LIST).performScrollToNode(hasTestTag(UserListTestTags.row(OUTGOING)))
        onNodeWithText("Отменить").performClick()
        onNodeWithTag(UserListTestTags.row(OUTGOING)).performClick()

        assertEquals(
            listOf(INCOMING to UserAction.ACCEPT, INCOMING to UserAction.REJECT, OUTGOING to UserAction.CANCEL),
            actions,
        )
        assertEquals(listOf(OUTGOING), opened)
    }

    @Test
    fun removingAsksFirstAndTheAnswerReachesTheCaller() = runComposeUiTest {
        var pending by mutableStateOf<FriendsEvent.ConfirmRemove?>(null)
        val confirmed = mutableListOf<Int>()
        var dismissed = 0
        setContent {
            ItmoTheme {
                Screen(
                    friends(),
                    pendingRemoval = pending,
                    onConfirmRemoval = {
                        confirmed += it
                        pending = null
                    },
                    onDismissRemoval = {
                        dismissed++
                        pending = null
                    },
                )
            }
        }
        onNode(isDialog()).assertDoesNotExist()

        pending = FriendsEvent.ConfirmRemove(FRIEND, UiText.Dynamic(FRIEND_NAME))
        waitForIdle()
        onNode(isDialog()).assertExists()
        onNodeWithText("Удалить из друзей?").assertExists()
        onNodeWithText("$FRIEND_NAME перестанет видеть ваши данные для друзей, а вы — его.").assertExists()
        onNodeWithText("Отмена").performClick()
        onNode(isDialog()).assertDoesNotExist()

        pending = FriendsEvent.ConfirmRemove(FRIEND, UiText.Dynamic(FRIEND_NAME))
        waitForIdle()
        // The row's own `Удалить` stays behind the dialog; the dialog's button confirms.
        onNode(hasText("Удалить") and hasAnyAncestor(isDialog())).performClick()
        onNode(isDialog()).assertDoesNotExist()

        assertEquals(1, dismissed)
        assertEquals(listOf(FRIEND), confirmed)
    }

    @Test
    fun everyStateIsDistinctAndEveryTargetIsAtLeast48Dp() = runComposeUiTest {
        var state by mutableStateOf<FriendsUiState>(FriendsUiState.Loading)
        setContent { ItmoTheme { Screen(state) } }

        for ((value, text) in States) {
            state = value
            waitForIdle()
            assertTouchTargets()
            val shown = when {
                value == FriendsUiState.Loading -> FriendsTestTags.LOADING
                value is FriendsUiState.Content && value.empty == null -> UserListTestTags.LIST
                else -> FriendsTestTags.STATE
            }
            for (tag in listOf(FriendsTestTags.LOADING, UserListTestTags.LIST, FriendsTestTags.STATE)) {
                if (tag == shown) onNodeWithTag(tag).assertExists() else onNodeWithTag(tag).assertDoesNotExist()
            }
            // The tabs belong to content only: no switching over placeholders, disabled services or an error.
            if (value is FriendsUiState.Content) {
                onNodeWithTag(FriendsTestTags.TABS).assertExists()
            } else {
                onNodeWithTag(FriendsTestTags.TABS).assertDoesNotExist()
            }
            text?.let { onNodeWithText(it).assertExists() }
        }
    }

    @Test
    fun eachStateLeadsOnToItsScreenOrRetry() = runComposeUiTest {
        var state by mutableStateOf<FriendsUiState>(empty(FriendsTab.FRIENDS))
        val calls = mutableListOf<String>()
        setContent {
            ItmoTheme {
                Screen(
                    state,
                    onRetry = { calls += "retry" },
                    onOpenSearch = { calls += "search" },
                    onOpenSettings = { calls += "settings" },
                    onBack = { calls += "back" },
                )
            }
        }

        onNodeWithText("Найти людей").performClick()
        onNodeWithContentDescription("Найти людей").performClick()
        onNodeWithContentDescription("Назад").performClick()

        state = empty(FriendsTab.REQUESTS)
        waitForIdle()
        // An empty requests tab offers no action of its own.
        onNodeWithText("Найти людей").assertDoesNotExist()

        state = FriendsUiState.Disabled
        waitForIdle()
        onNodeWithText("Настройки").performClick()

        state = FriendsUiState.Error(AppError.Network)
        waitForIdle()
        onNodeWithText("Повторить").performClick()

        assertEquals(listOf("search", "search", "back", "settings", "retry"), calls)
    }

    @Test
    fun aReloadKeepsTheListOnScreenAndOnlyAnErrorReplacesIt() = runComposeUiTest {
        var state by mutableStateOf<FriendsUiState>(friends())
        setContent { ItmoTheme { Screen(state) } }

        state = FriendsUiState.Loading
        waitForIdle()
        onNodeWithTag(UserListTestTags.row(FRIEND)).assertExists()
        onNodeWithTag(FriendsTestTags.LOADING).assertDoesNotExist()

        state = FriendsUiState.Error(AppError.Network)
        waitForIdle()
        state = FriendsUiState.Loading
        waitForIdle()
        onNodeWithTag(FriendsTestTags.LOADING).assertExists()
        onNodeWithTag(UserListTestTags.row(FRIEND)).assertDoesNotExist()
    }

    private companion object {
        const val FRIEND = 200001
        const val FRIEND_NAME = "Преображенская Евгения Владиславовна"
        const val INCOMING = 200004
        const val OUTGOING = 200006

        val States = listOf(
            FriendsUiState.Loading to null,
            empty(FriendsTab.FRIENDS) to "Друзей пока нет",
            empty(FriendsTab.REQUESTS) to "Заявок нет",
            FriendsUiState.Disabled to "Нет подключения к ITMO.Widgets",
            FriendsUiState.Error(AppError.Network) to "Не удалось загрузить",
            friends() to FRIEND_NAME,
            requests() to "Входящие",
        )

        fun friends(incomingCount: Int = 1) = FriendsUiState.Content(
            tab = FriendsTab.FRIENDS,
            items = listOf(UserListItem.User(row(FRIEND, FRIEND_NAME, secondary = UserAction.REMOVE))),
            incomingCount = incomingCount,
            refreshing = false,
        )

        fun requests(incomingCount: Int = 1) = FriendsUiState.Content(
            tab = FriendsTab.REQUESTS,
            items = listOf(
                UserListItem.Header(UiText.Dynamic("Входящие")),
                UserListItem.User(row(INCOMING, "Григорьев Евгений", UserAction.ACCEPT, UserAction.REJECT)),
                UserListItem.Header(UiText.Dynamic("Отправленные")),
                UserListItem.User(row(OUTGOING, "Захаров Борис", secondary = UserAction.CANCEL)),
            ),
            incomingCount = incomingCount,
            refreshing = false,
        )

        fun empty(tab: FriendsTab) = FriendsUiState.Content(
            tab = tab,
            items = emptyList(),
            incomingCount = 0,
            refreshing = false,
            empty = if (tab == FriendsTab.FRIENDS) FriendsEmpty.NO_FRIENDS else FriendsEmpty.NO_REQUESTS,
        )

        fun row(isu: Int, name: String, primary: UserAction? = null, secondary: UserAction? = null) =
            UserRowUi(isu, name, null, UiText.Dynamic("$isu • M3234"), primary = primary, secondary = secondary)
    }
}

@Composable
private fun Screen(
    state: FriendsUiState,
    onSelectTab: (FriendsTab) -> Unit = {},
    onRetry: () -> Unit = {},
    onAction: (UserRowUi, UserAction) -> Unit = { _, _ -> },
    onOpenProfile: (Int) -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onBack: () -> Unit = {},
    pendingRemoval: FriendsEvent.ConfirmRemove? = null,
    onConfirmRemoval: (Int) -> Unit = {},
    onDismissRemoval: () -> Unit = {},
) = FriendsScreen(
    state = state,
    onSelectTab = onSelectTab,
    onRefresh = {},
    onRetry = onRetry,
    onAction = onAction,
    onOpenProfile = onOpenProfile,
    onOpenSearch = onOpenSearch,
    onOpenSettings = onOpenSettings,
    onBack = onBack,
    pendingRemoval = pendingRemoval,
    onConfirmRemoval = onConfirmRemoval,
    onDismissRemoval = onDismissRemoval,
)
