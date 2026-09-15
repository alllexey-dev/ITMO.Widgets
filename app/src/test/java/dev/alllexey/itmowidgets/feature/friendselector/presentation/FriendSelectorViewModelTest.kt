package dev.alllexey.itmowidgets.feature.friendselector.presentation

import dev.alllexey.itmowidgets.core.friend.FriendListState
import dev.alllexey.itmowidgets.core.friend.FriendRepository
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.social.PeopleSearchPage
import dev.alllexey.itmowidgets.core.social.PeopleSearchRepository
import dev.alllexey.itmowidgets.core.social.PersonSearchResult
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.feature.friendselector.domain.FriendSelectionHistory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FriendSelectorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `renders content and keeps history order`() =
        runTest(mainDispatcherRule.dispatcher) {
            val first = user(1, "Первый")
            val second = user(2, "Второй")
            val repository = FakeFriendRepository(
                FriendListState.Content(listOf(first, second))
            )
            val history = FakeHistory(listOf(2, 1))
            val viewModel = FriendSelectorViewModel(repository, history, FakePeopleSearch())

            advanceUntilIdle()

            assertEquals(
                FriendSelectorUiState.Content(
                    friends = listOf(first, second),
                    recentFriends = listOf(second, first)
                ),
                viewModel.uiState.value
            )
            assertEquals(1, repository.refreshRequests)
        }

    @Test
    fun `renders empty state for empty friend list`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = FriendSelectorViewModel(
                FakeFriendRepository(FriendListState.Content(emptyList())),
                FakeHistory(),
                FakePeopleSearch()
            )

            advanceUntilIdle()

            assertEquals(FriendSelectorUiState.Empty, viewModel.uiState.value)
        }

    @Test
    fun `preserves typed errors and disabled state`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeFriendRepository(
                FriendListState.Error(AppError.Unauthorized)
            )
            val viewModel = FriendSelectorViewModel(repository, FakeHistory(), FakePeopleSearch())
            advanceUntilIdle()
            assertEquals(
                FriendSelectorUiState.Error(AppError.Unauthorized),
                viewModel.uiState.value
            )

            repository.state.value = FriendListState.Disabled
            advanceUntilIdle()
            assertEquals(FriendSelectorUiState.Disabled, viewModel.uiState.value)
        }

    @Test
    fun `settles on a terminal state when a repeated refresh does not change the repository`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeFriendRepository(FriendListState.Disabled)
            val viewModel = FriendSelectorViewModel(repository, FakeHistory(), FakePeopleSearch())
            advanceUntilIdle()
            assertEquals(FriendSelectorUiState.Disabled, viewModel.uiState.value)

            // Reopening the selector refreshes again; the repository keeps its
            // conflated state, so nothing new is emitted from its flow.
            viewModel.refresh()
            advanceUntilIdle()

            assertEquals(FriendSelectorUiState.Disabled, viewModel.uiState.value)
            assertEquals(2, repository.refreshRequests)
        }

    @Test
    fun `keeps the loaded list visible while refreshing`() =
        runTest(mainDispatcherRule.dispatcher) {
            val first = user(1, "Первый")
            val repository = FakeFriendRepository(FriendListState.Content(listOf(first)))
            val viewModel = FriendSelectorViewModel(repository, FakeHistory(), FakePeopleSearch())
            advanceUntilIdle()

            viewModel.refresh()

            assertEquals(
                FriendSelectorUiState.Content(
                    friends = listOf(first),
                    recentFriends = emptyList()
                ),
                viewModel.uiState.value
            )
        }

    @Test
    fun `exposes the signed-in user for the own-schedule entry`() =
        runTest(mainDispatcherRule.dispatcher) {
            val me = user(7, "Я Сам")
            val friend = user(1, "Первый")
            val viewModel = FriendSelectorViewModel(
                FakeFriendRepository(
                    initialState = FriendListState.Content(listOf(friend)),
                    initialUser = me
                ),
                FakeHistory(),
                FakePeopleSearch()
            )

            advanceUntilIdle()

            assertEquals(
                me,
                (viewModel.uiState.value as FriendSelectorUiState.Content).currentUser
            )
        }

    @Test
    fun `keeps friends visible when the profile is unavailable`() =
        runTest(mainDispatcherRule.dispatcher) {
            val friend = user(1, "Первый")
            val viewModel = FriendSelectorViewModel(
                FakeFriendRepository(
                    initialState = FriendListState.Content(listOf(friend)),
                    initialUser = null
                ),
                FakeHistory(),
                FakePeopleSearch()
            )

            advanceUntilIdle()

            val state = viewModel.uiState.value as FriendSelectorUiState.Content
            assertEquals(listOf(friend), state.friends)
            assertEquals(null, state.currentUser)
        }

    @Test
    fun `records confirmed selection`() =
        runTest(mainDispatcherRule.dispatcher) {
            val history = FakeHistory()
            val viewModel = FriendSelectorViewModel(
                FakeFriendRepository(FriendListState.Content(emptyList())),
                history,
                FakePeopleSearch()
            )

            viewModel.recordSelection(123456)
            advanceUntilIdle()

            assertEquals(listOf(123456), history.recorded)
        }

    private fun user(isu: Int, name: String): UserSummary {
        return UserSummary(
            isu = isu,
            name = name,
            pictureUrl = null,
            groups = emptyList(),
            sharing = UserSharing(sport = true, schedule = true)
        )
    }

    @Test
    fun `wide scope searches registered people after the debounce and narrow scope does not`() =
        runTest(mainDispatcherRule.dispatcher) {
            val friend = user(1, "Первый")
            val stranger = user(2, "Чужой")
            val search = FakePeopleSearch(registered = listOf(stranger))
            val viewModel = FriendSelectorViewModel(
                FakeFriendRepository(FriendListState.Content(listOf(friend))),
                FakeHistory(),
                search
            )
            advanceUntilIdle()

            viewModel.onQueryChanged("чуж")
            advanceUntilIdle()
            assertEquals(emptyList<String>(), search.queries)

            viewModel.selectScope(FriendSelectorScope.ALL)
            advanceUntilIdle()
            assertEquals(listOf("чуж"), search.queries)
            val content = viewModel.uiState.value as FriendSelectorUiState.Content
            assertEquals(FriendSelectorScope.ALL, content.scope)
            assertEquals(PeopleResults.Content(listOf(stranger)), content.people)
            assertEquals(listOf(friend), content.friends)

            viewModel.selectScope(FriendSelectorScope.FRIENDS)
            advanceUntilIdle()
            assertEquals(
                PeopleResults.Idle,
                (viewModel.uiState.value as FriendSelectorUiState.Content).people
            )
        }

    @Test
    fun `wide scope stays available without friends and reports search failures`() =
        runTest(mainDispatcherRule.dispatcher) {
            val search = FakePeopleSearch(error = AppError.Network)
            val viewModel = FriendSelectorViewModel(
                FakeFriendRepository(FriendListState.Content(emptyList())),
                FakeHistory(),
                search
            )
            advanceUntilIdle()
            assertEquals(FriendSelectorUiState.Empty, viewModel.uiState.value)

            viewModel.selectScope(FriendSelectorScope.ALL)
            viewModel.onQueryChanged("а")
            advanceUntilIdle()

            val content = viewModel.uiState.value as FriendSelectorUiState.Content
            assertEquals(PeopleResults.Error(AppError.Network), content.people)

            viewModel.onQueryChanged("")
            advanceUntilIdle()
            assertEquals(
                PeopleResults.Idle,
                (viewModel.uiState.value as FriendSelectorUiState.Content).people
            )
        }

    private class FakePeopleSearch(
        private val registered: List<UserSummary> = emptyList(),
        private val error: AppError? = null
    ) : PeopleSearchRepository {
        val queries = mutableListOf<String>()

        override suspend fun search(query: String, offset: Int): AppResult<PeopleSearchPage> {
            queries += query
            error?.let { return AppResult.Failure(it) }
            val results = registered.map { user ->
                PersonSearchResult(user.isu, user.name, user.pictureUrl, UserProfile(user, RelationshipState.NONE))
            } + PersonSearchResult(999999, "Незарегистрированный", null, null)
            return AppResult.Success(PeopleSearchPage(results, results.size, null))
        }
    }

    private class FakeFriendRepository(
        initialState: FriendListState,
        initialUser: UserSummary? = null
    ) : FriendRepository {
        val state = MutableStateFlow(initialState)
        val user = MutableStateFlow(initialUser)
        var refreshRequests = 0

        override fun observeFriendList(): Flow<FriendListState> = state

        override fun observeCurrentUser(): Flow<UserSummary?> = user

        override suspend fun refreshFriendList() {
            refreshRequests += 1
        }

        override val currentFriends: List<UserSummary>?
            get() = (state.value as? FriendListState.Content)?.friends
    }

    private class FakeHistory(
        private val recent: List<Int> = emptyList()
    ) : FriendSelectionHistory {
        val recorded = mutableListOf<Int>()

        override suspend fun getRecentIsu(): List<Int> = recent

        override suspend fun record(isu: Int) {
            recorded += isu
        }
    }
}
