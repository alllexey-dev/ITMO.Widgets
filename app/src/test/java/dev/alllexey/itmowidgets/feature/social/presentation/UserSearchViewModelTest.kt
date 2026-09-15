package dev.alllexey.itmowidgets.feature.social.presentation

import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.social.PeopleSearchPage
import dev.alllexey.itmowidgets.core.social.PeopleSearchRepository
import dev.alllexey.itmowidgets.core.social.PersonSearchResult
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.text.UiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserSearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `debounces typing and splits registered people from the rest`() = runTest(mainDispatcherRule.dispatcher) {
        val search = FakePeopleSearch(
            pages = mapOf(
                0 to PeopleSearchPage(
                    results = listOf(
                        PersonSearchResult(1, "Зарегистрированный", null, profile(1)),
                        PersonSearchResult(2, "Посторонний", null, null)
                    ),
                    total = 3,
                    nextOffset = 2
                ),
                2 to PeopleSearchPage(listOf(PersonSearchResult(3, "Третий", null, null)), 3, null)
            )
        )
        val viewModel = UserSearchViewModel(search, FakeSocialRepository())

        viewModel.onQueryChanged("и")
        viewModel.onQueryChanged("ив")
        advanceTimeBy(100)
        assertEquals(emptyList<String>(), search.queries)
        assertEquals(UserSearchUiState.Idle, viewModel.uiState.value)

        advanceUntilIdle()

        assertEquals(listOf("ив"), search.queries)
        val content = viewModel.uiState.value as UserSearchUiState.Content
        assertEquals(
            listOf(
                UserListItem.Header(UiText.Resource(R.string.user_search_section_registered)),
                "user:1",
                UserListItem.Header(UiText.Resource(R.string.user_search_section_others)),
                "user:2",
                UserListItem.LoadMore
            ),
            content.items.map { if (it is UserListItem.User) "user:${it.row.isu}" else it }
        )
        val registered = (content.items[1] as UserListItem.User).row
        assertEquals(UserAction.ADD, registered.primary)
        assertEquals(true, registered.opensProfile)
        val stranger = (content.items[3] as UserListItem.User).row
        assertEquals(UserAction.INVITE, stranger.primary)
        assertEquals(false, stranger.opensProfile)

        viewModel.loadMore()
        advanceUntilIdle()
        val more = viewModel.uiState.value as UserSearchUiState.Content
        assertEquals(listOf(0, 2), search.offsets)
        assertEquals(false, more.items.contains(UserListItem.LoadMore))
        assertEquals(3, more.items.count { it is UserListItem.User })
    }

    @Test
    fun `adding a friend updates the row without a new search and failures become events`() =
        runTest(mainDispatcherRule.dispatcher) {
            val search = FakePeopleSearch(
                pages = mapOf(0 to PeopleSearchPage(listOf(PersonSearchResult(1, "Кто-то", null, profile(1))), 1, null))
            )
            val social = FakeSocialRepository()
            val viewModel = UserSearchViewModel(search, social)
            viewModel.onQueryChanged("кто")
            advanceUntilIdle()
            val row = ((viewModel.uiState.value as UserSearchUiState.Content).items[1] as UserListItem.User).row

            viewModel.onAction(row, UserAction.ADD)
            advanceUntilIdle()

            assertEquals(listOf("send:1"), social.actions)
            assertEquals(listOf("кто"), search.queries)
            val updated = ((viewModel.uiState.value as UserSearchUiState.Content).items[1] as UserListItem.User).row
            assertEquals(UserAction.CANCEL, updated.primary)
            assertEquals(UiText.Resource(R.string.user_status_outgoing), updated.status)

            social.actionError = AppError.Forbidden
            viewModel.onAction(updated, UserAction.CANCEL)
            assertEquals(UserSearchEvent.ActionFailed(AppError.Forbidden), viewModel.eventFlow.first())
        }

    @Test
    fun `clearing the query resets immediately and search errors are typed`() =
        runTest(mainDispatcherRule.dispatcher) {
            val search = FakePeopleSearch(error = AppError.Network)
            val viewModel = UserSearchViewModel(search, FakeSocialRepository())

            viewModel.onQueryChanged("а")
            advanceUntilIdle()
            assertEquals(UserSearchUiState.Error(AppError.Network), viewModel.uiState.value)

            viewModel.onQueryChanged("")
            assertEquals(UserSearchUiState.Idle, viewModel.uiState.value)
            advanceUntilIdle()
            assertEquals(listOf("а"), search.queries)
        }

    @Test
    fun `invite is delegated to the screen with the person's name`() = runTest(mainDispatcherRule.dispatcher) {
        val search = FakePeopleSearch(
            pages = mapOf(0 to PeopleSearchPage(listOf(PersonSearchResult(2, "Посторонний", null, null)), 1, null))
        )
        val viewModel = UserSearchViewModel(search, FakeSocialRepository())
        viewModel.onQueryChanged("пос")
        advanceUntilIdle()
        val row = ((viewModel.uiState.value as UserSearchUiState.Content).items[1] as UserListItem.User).row

        viewModel.onAction(row, UserAction.INVITE)

        assertEquals(UserSearchEvent.Invite("Посторонний"), viewModel.eventFlow.first())
    }

    private class FakePeopleSearch(
        private val pages: Map<Int, PeopleSearchPage> = emptyMap(),
        private val error: AppError? = null
    ) : PeopleSearchRepository {
        val queries = mutableListOf<String>()
        val offsets = mutableListOf<Int>()

        override suspend fun search(query: String, offset: Int): AppResult<PeopleSearchPage> {
            queries += query
            offsets += offset
            error?.let { return AppResult.Failure(it) }
            return AppResult.Success(pages[offset] ?: PeopleSearchPage.EMPTY)
        }
    }
}
