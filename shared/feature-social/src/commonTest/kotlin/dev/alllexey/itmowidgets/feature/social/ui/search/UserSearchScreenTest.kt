package dev.alllexey.itmowidgets.feature.social.ui.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.social.PeopleSearchPage
import dev.alllexey.itmowidgets.core.social.PeopleSearchRepository
import dev.alllexey.itmowidgets.core.social.PersonSearchResult
import dev.alllexey.itmowidgets.core.testing.FakeSocialRepository
import dev.alllexey.itmowidgets.core.testing.profile
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.social.presentation.UserAction
import dev.alllexey.itmowidgets.feature.social.presentation.UserListItem
import dev.alllexey.itmowidgets.feature.social.presentation.UserRowUi
import dev.alllexey.itmowidgets.feature.social.presentation.UserSearchUiState
import dev.alllexey.itmowidgets.feature.social.presentation.UserSearchViewModel
import dev.alllexey.itmowidgets.feature.social.ui.list.UserListTestTags
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class UserSearchScreenTest {

    @Test
    fun typingReachesTheCallerAndTheClearButtonShowsOnlyOverText() = runComposeUiTest {
        var query by mutableStateOf("")
        val typed = mutableListOf<String>()
        setContent {
            ItmoTheme {
                Screen(
                    UserSearchUiState.Idle,
                    query = query,
                    onQueryChange = {
                        typed += it
                        query = it
                    },
                )
            }
        }
        onNodeWithTag(UserSearchTestTags.CLEAR).assertDoesNotExist()

        onNodeWithTag(UserSearchTestTags.FIELD).performTextInput("Соколов")
        onNodeWithTag(UserSearchTestTags.CLEAR).assertExists()
        onNodeWithContentDescription("Очистить текст").performClick()

        assertEquals(listOf("Соколов", ""), typed)
        onNodeWithTag(UserSearchTestTags.CLEAR).assertDoesNotExist()
    }

    @Test
    fun rowActionsTapsAndLoadMoreReachTheirCallbacks() = runComposeUiTest {
        val calls = mutableListOf<String>()
        setContent {
            ItmoTheme {
                Screen(
                    results(),
                    onAction = { row, action -> calls += "${row.isu}:$action" },
                    onLoadMore = { calls += "more" },
                    onOpenProfile = { calls += "open:$it" },
                )
            }
        }

        onNodeWithText("Добавить").performClick()
        onNodeWithTag(UserListTestTags.row(STRANGER)).performClick()
        onNodeWithTag(UserListTestTags.LIST).performScrollToNode(hasTestTag(UserListTestTags.LOAD_MORE))
        onNodeWithText("Пригласить").performClick()
        onNodeWithTag(UserListTestTags.LOAD_MORE).performClick()

        assertEquals(listOf("$REGISTERED:ADD", "open:$STRANGER", "$STRANGER:INVITE", "more"), calls)
    }

    @Test
    fun theNextPageInFlightShowsTheBarInsteadOfTheButton() = runComposeUiTest {
        var state by mutableStateOf<UserSearchUiState>(results())
        setContent { ItmoTheme { Screen(state) } }
        onNodeWithTag(UserSearchTestTags.LOADING_MORE).assertDoesNotExist()

        state = results(loadingMore = true)
        waitForIdle()
        onNodeWithTag(UserSearchTestTags.LOADING_MORE).assertExists()
        onNodeWithTag(UserListTestTags.LOAD_MORE).assertDoesNotExist()
    }

    @Test
    fun everyStateIsDistinctAndEveryTargetIsAtLeast48Dp() = runComposeUiTest {
        var state by mutableStateOf<UserSearchUiState>(UserSearchUiState.Idle)
        setContent { ItmoTheme { Screen(state, query = "Соколов") } }

        for ((value, text) in States) {
            state = value
            waitForIdle()
            assertTouchTargets()
            val shown = when (value) {
                UserSearchUiState.Loading -> UserSearchTestTags.LOADING
                is UserSearchUiState.Content -> UserListTestTags.LIST
                else -> UserSearchTestTags.STATE
            }
            for (tag in listOf(UserSearchTestTags.LOADING, UserListTestTags.LIST, UserSearchTestTags.STATE)) {
                if (tag == shown) onNodeWithTag(tag).assertExists() else onNodeWithTag(tag).assertDoesNotExist()
            }
            text?.let { onNodeWithText(it).assertExists() }
        }
    }

    @Test
    fun anErrorOffersARetryAndTheBackButtonLeaves() = runComposeUiTest {
        val calls = mutableListOf<String>()
        setContent {
            ItmoTheme {
                Screen(
                    UserSearchUiState.Error(AppError.Network),
                    onRetry = { calls += "retry" },
                    onBack = { calls += "back" },
                )
            }
        }

        onNodeWithText("Повторить").performClick()
        onNodeWithContentDescription("Назад").performClick()

        assertEquals(listOf("retry", "back"), calls)
    }

    @Test
    fun theRouteFocusesTheFieldInvitesLoadsMoreAndResetsThroughTheViewModel() = runComposeUiTest {
        val search = RecordingPeopleSearch()
        val viewModel = UserSearchViewModel(search, FakeSocialRepository())
        var invites = 0
        setContent {
            ItmoTheme { UserSearchRoute(onOpenProfile = {}, onInvite = { invites++ }, onBack = {}, viewModel = viewModel) }
        }

        // The route opens with the field focused, ready to type.
        onNodeWithTag(UserSearchTestTags.FIELD).assertIsFocused()
        onNodeWithTag(UserSearchTestTags.FIELD).performTextInput("Соколов")
        // A retry searches at once; the debounced search of the typed text would only repeat it.
        runOnIdle { viewModel.refresh(RefreshMode.Force) }
        waitUntil { viewModel.uiState.value is UserSearchUiState.Content }

        onNodeWithText("Пригласить").performClick()
        waitUntil { invites == 1 }

        onNodeWithTag(UserListTestTags.LIST).performScrollToNode(hasTestTag(UserListTestTags.LOAD_MORE))
        onNodeWithTag(UserListTestTags.LOAD_MORE).performClick()
        waitUntil { search.offsets.last() == NEXT_OFFSET }
        onNodeWithTag(UserListTestTags.LIST).performScrollToNode(hasText(LAST_NAME))

        onNodeWithTag(UserSearchTestTags.CLEAR).performClick()
        onNodeWithTag(UserSearchTestTags.CLEAR).assertDoesNotExist()
        assertEquals(UserSearchUiState.Idle, viewModel.uiState.value)
        onNodeWithText("Кого ищем?").assertExists()
        assertEquals(listOf("Соколов"), search.queries.distinct())
    }

    private class RecordingPeopleSearch : PeopleSearchRepository {
        val queries = mutableListOf<String>()
        val offsets = mutableListOf<Int>()

        override suspend fun search(query: String, offset: Int): AppResult<PeopleSearchPage> {
            queries += query
            offsets += offset
            val page = if (offset == 0) {
                PeopleSearchPage(
                    listOf(
                        PersonSearchResult(REGISTERED, "Соколов Артём", null, profile(REGISTERED)),
                        PersonSearchResult(STRANGER, "Соколов Борис", null, null),
                    ),
                    total = 3,
                    nextOffset = NEXT_OFFSET,
                )
            } else {
                PeopleSearchPage(listOf(PersonSearchResult(STRANGER + 1, LAST_NAME, null, null)), 3, null)
            }
            return AppResult.Success(page)
        }
    }

    private companion object {
        const val REGISTERED = 200011
        const val STRANGER = 200014
        const val NEXT_OFFSET = 2
        const val LAST_NAME = "Соколова Дарья"

        val States = listOf(
            UserSearchUiState.Idle to "Кого ищем?",
            UserSearchUiState.Loading to null,
            UserSearchUiState.Empty to "Никого не нашли",
            UserSearchUiState.Error(AppError.Network) to "Не удалось загрузить",
            results() to "В ITMO.Widgets",
        )

        fun results(loadingMore: Boolean = false) = UserSearchUiState.Content(
            items = buildList {
                add(UserListItem.Header(UiText.Dynamic("В ITMO.Widgets")))
                add(UserListItem.User(row(REGISTERED, "Соколов Артём", UserAction.ADD)))
                add(UserListItem.Header(UiText.Dynamic("Остальные")))
                add(UserListItem.User(row(STRANGER, "Соколов Борис", UserAction.INVITE)))
                if (!loadingMore) add(UserListItem.LoadMore)
            },
            loadingMore = loadingMore,
        )

        fun row(isu: Int, name: String, primary: UserAction) =
            UserRowUi(isu, name, null, UiText.Dynamic("ИСУ $isu"), primary = primary)
    }
}

@Composable
private fun Screen(
    state: UserSearchUiState,
    query: String = "",
    onQueryChange: (String) -> Unit = {},
    onRetry: () -> Unit = {},
    onAction: (UserRowUi, UserAction) -> Unit = { _, _ -> },
    onLoadMore: () -> Unit = {},
    onOpenProfile: (Int) -> Unit = {},
    onBack: () -> Unit = {},
) = UserSearchScreen(
    query = query,
    state = state,
    onQueryChange = onQueryChange,
    onRetry = onRetry,
    onAction = onAction,
    onLoadMore = onLoadMore,
    onOpenProfile = onOpenProfile,
    onBack = onBack,
)
