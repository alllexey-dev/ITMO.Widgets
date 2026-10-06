package dev.alllexey.itmowidgets.feature.social.ui.userfriends

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.social.presentation.UserFriendsUiState
import dev.alllexey.itmowidgets.feature.social.presentation.UserListItem
import dev.alllexey.itmowidgets.feature.social.presentation.UserRowUi
import dev.alllexey.itmowidgets.feature.social.ui.list.UserListTestTags
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class UserFriendsScreenTest {

    @Test
    fun aRowOpensThatProfileAndOffersNoOtherAction() = runComposeUiTest {
        val opened = mutableListOf<Int>()
        setContent {
            ItmoTheme { Screen(UserFriendsUiState.Content(Friends), onOpenProfile = { opened += it }) }
        }

        onNodeWithText(LONG_NAME).assertExists()
        onNodeWithTag(UserListTestTags.row(200003)).performClick()
        onNodeWithTag(UserListTestTags.row(200001)).performClick()

        assertEquals(listOf(200003, 200001), opened)
        // The back button and one target per row: the list has no mutation buttons.
        assertEquals(1 + Friends.size, onAllNodes(hasClickAction()).fetchSemanticsNodes().size)
    }

    @Test
    fun everyStateIsDistinctAndEveryTargetIsAtLeast48Dp() = runComposeUiTest {
        var state by mutableStateOf<UserFriendsUiState>(UserFriendsUiState.Loading)
        setContent { ItmoTheme { Screen(state) } }

        for ((value, text) in States) {
            state = value
            waitForIdle()
            assertTouchTargets()
            val shown = when {
                value == UserFriendsUiState.Loading -> UserFriendsTestTags.LOADING
                value is UserFriendsUiState.Content && value.items.isNotEmpty() -> UserListTestTags.LIST
                else -> UserFriendsTestTags.STATE
            }
            for (tag in listOf(UserFriendsTestTags.LOADING, UserListTestTags.LIST, UserFriendsTestTags.STATE)) {
                if (tag == shown) onNodeWithTag(tag).assertExists() else onNodeWithTag(tag).assertDoesNotExist()
            }
            text?.let { onNodeWithText(it).assertExists() }
        }
    }

    @Test
    fun theDeniedListOffersNothingWhileTheOtherStatesLeadOn() = runComposeUiTest {
        var state by mutableStateOf<UserFriendsUiState>(UserFriendsUiState.Hidden)
        var retries = 0
        var settings = 0
        var backs = 0
        setContent {
            ItmoTheme {
                Screen(state, onRetry = { retries++ }, onOpenSettings = { settings++ }, onBack = { backs++ })
            }
        }

        // Only the back button is a target over the hidden list.
        assertEquals(1, onAllNodes(hasClickAction()).fetchSemanticsNodes().size)
        onNodeWithContentDescription("Назад").performClick()

        state = UserFriendsUiState.Disabled
        waitForIdle()
        onNodeWithText("Настройки").performClick()

        state = UserFriendsUiState.Error(AppError.Network)
        waitForIdle()
        onNodeWithText("Повторить").performClick()

        state = UserFriendsUiState.Content(emptyList())
        waitForIdle()
        onNodeWithText("Обновить").performClick()

        assertEquals(1, backs)
        assertEquals(1, settings)
        assertEquals(2, retries)
    }

    @Test
    fun theTitleNamesTheOwnerOrFallsBackToFriends() = runComposeUiTest {
        var owner by mutableStateOf("Иван")
        setContent { ItmoTheme { Screen(UserFriendsUiState.Loading, owner = owner) } }

        onNodeWithText("Друзья · Иван").assertExists()
        owner = " "
        waitForIdle()
        onNodeWithText("Друзья").assertExists()
    }

    private companion object {
        const val LONG_NAME = "Преображенская Евгения Владиславовна"

        val Friends = listOf(row(200001, LONG_NAME, RelationshipState.FRIENDS), row(200003, "Соколов Артём", null))

        val States = listOf(
            UserFriendsUiState.Loading to null,
            UserFriendsUiState.Content(emptyList()) to "Пока нет друзей",
            UserFriendsUiState.Hidden to "Список друзей скрыт",
            UserFriendsUiState.Disabled to "Нет подключения к ITMO.Widgets",
            UserFriendsUiState.Error(AppError.Network) to "Не удалось загрузить",
            UserFriendsUiState.Content(Friends) to LONG_NAME,
        )

        fun row(isu: Int, name: String, relationship: RelationshipState?) = UserListItem.User(
            UserRowUi(isu, name, null, UiText.Dynamic("$isu • M3234"), relationship?.let { UiText.Dynamic(it.name) }),
        )
    }
}

@Composable
private fun Screen(
    state: UserFriendsUiState,
    owner: String = "Иван",
    onOpenProfile: (Int) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onRetry: () -> Unit = {},
    onBack: () -> Unit = {},
) = UserFriendsScreen(
    state = state,
    ownerName = owner,
    onRefresh = {},
    onRetry = onRetry,
    onOpenProfile = onOpenProfile,
    onOpenSettings = onOpenSettings,
    onBack = onBack,
)
