package dev.alllexey.itmowidgets.feature.friendselector.presentation

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.friend.FriendRepository
import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.navigation.FriendSelectionContract
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.result.valueOrNull
import dev.alllexey.itmowidgets.core.social.PeopleSearchPage
import dev.alllexey.itmowidgets.core.social.PeopleSearchRepository
import dev.alllexey.itmowidgets.core.social.PersonSearchResult
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.feature.friendselector.domain.FriendSelectionHistory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FriendSelectorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val first = user(1, "Первый")
    private val second = user(2, "Второй")

    @Test
    fun `renders content and keeps history order`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeFriendRepository(LoadState.Content(listOf(first, second)))
        val viewModel = viewModel(repository, FakeHistory(listOf(2, 1)))

        advanceUntilIdle()

        assertEquals(
            FriendSelectorUiState(
                body = FriendSelectorBody.Users(listOf(first, second)),
                recentFriends = listOf(second, first)
            ),
            viewModel.uiState.value
        )
        assertEquals(1, repository.refreshRequests)
    }

    @Test
    fun `renders the no-friends body for an empty friend list`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = viewModel(FakeFriendRepository(LoadState.Content(emptyList())))

        advanceUntilIdle()

        assertEquals(FriendSelectorBody.NoFriends, viewModel.uiState.value.body)
        assertTrue(viewModel.uiState.value.canApply)
        assertTrue(viewModel.uiState.value.showsScope)
    }

    @Test
    fun `preserves typed errors and disabled state`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeFriendRepository(LoadState.Error(AppError.Unauthorized))
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        assertEquals(FriendSelectorBody.Error(AppError.Unauthorized), viewModel.uiState.value.body)
        assertFalse(viewModel.uiState.value.canApply)
        assertFalse(viewModel.uiState.value.showsScope)

        repository.state.value = LoadState.Disabled
        advanceUntilIdle()
        assertEquals(FriendSelectorBody.Disabled, viewModel.uiState.value.body)
        assertFalse(viewModel.uiState.value.canApply)
    }

    @Test
    fun `settles on a terminal state when a repeated refresh does not change the repository`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeFriendRepository(LoadState.Disabled)
            val viewModel = viewModel(repository)
            advanceUntilIdle()
            assertEquals(FriendSelectorBody.Disabled, viewModel.uiState.value.body)

            // The repository keeps its conflated state, so nothing new is emitted from its flow.
            viewModel.refresh(RefreshMode.Force)
            advanceUntilIdle()

            assertEquals(FriendSelectorBody.Disabled, viewModel.uiState.value.body)
            assertEquals(2, repository.refreshRequests)
        }

    @Test
    fun `entry refreshes silently and a forced retry shows progress over an error`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeFriendRepository(LoadState.Error(AppError.Network), gated = true)
            val viewModel = viewModel(repository)
            runCurrent()
            assertEquals(FriendSelectorBody.Error(AppError.Network), viewModel.uiState.value.body)

            repository.finishRefresh()
            runCurrent()
            viewModel.retry()
            runCurrent()
            assertEquals(FriendSelectorBody.Loading, viewModel.uiState.value.body)
            assertEquals(2, repository.refreshRequests)

            repository.finishRefresh()
            runCurrent()
            assertEquals(FriendSelectorBody.Error(AppError.Network), viewModel.uiState.value.body)
        }

    @Test
    fun `keeps the loaded list visible while refreshing`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeFriendRepository(LoadState.Content(listOf(first)), gated = true)
        val viewModel = viewModel(repository)
        runCurrent()
        repository.finishRefresh()
        runCurrent()

        viewModel.refresh(RefreshMode.Pull)
        runCurrent()

        assertEquals(FriendSelectorBody.Users(listOf(first)), viewModel.uiState.value.body)
        repository.finishRefresh()
    }

    @Test
    fun `filters friends by trimmed lowercased name, ISU, group, faculty and course`() =
        runTest(mainDispatcherRule.dispatcher) {
            val physicist = user(556677, "Анна Физикова", UserGroup("P3112", 1, "ФТФ"))
            val programmer = user(112233, "Борис Кодов", UserGroup("M3207", 4, "ПИиКТ"))
            val viewModel = viewModel(FakeFriendRepository(LoadState.Content(listOf(physicist, programmer))))
            advanceUntilIdle()

            for ((query, expected) in listOf(
                "  АННА " to listOf(physicist),
                "1122" to listOf(programmer),
                "m3207" to listOf(programmer),
                "фтф" to listOf(physicist),
                "4" to listOf(programmer),
                "" to listOf(physicist, programmer)
            )) {
                viewModel.onQueryChanged(query)
                advanceUntilIdle()
                assertEquals(query, FriendSelectorBody.Users(expected), viewModel.uiState.value.body)
            }

            viewModel.onQueryChanged("нет такого")
            advanceUntilIdle()
            assertEquals(FriendSelectorBody.NoMatches, viewModel.uiState.value.body)
        }

    @Test
    fun `a preselection counts only for a listed friend whose schedule is open`() =
        runTest(mainDispatcherRule.dispatcher) {
            val closed = user(3, "Закрытый", schedule = false)
            val viewModel = viewModel(
                FakeFriendRepository(LoadState.Content(listOf(first, closed))),
                FakeHistory(listOf(3, 1)),
                SavedStateHandle(mapOf(FriendSelectionContract.ARG_SELECTED_ISU to closed.isu))
            )
            assertEquals(closed.isu, viewModel.uiState.value.selectedIsu)

            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertNull(state.selectedIsu)
            assertNull(state.applyTarget)
            assertEquals(listOf(first), state.recentFriends)
        }

    @Test
    fun `an open preselection is chosen and leads the recent chips`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = viewModel(
            FakeFriendRepository(LoadState.Content(listOf(first, second))),
            FakeHistory(listOf(1)),
            SavedStateHandle(mapOf(FriendSelectionContract.ARG_SELECTED_ISU to second.isu))
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(second.isu, state.selectedIsu)
        assertEquals(second, state.applyTarget)
        assertEquals(listOf(second, first), state.recentFriends)
    }

    @Test
    fun `a pending selection survives refreshes and recent chips never reorder`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeFriendRepository(LoadState.Content(listOf(first, second)))
            val history = FakeHistory(listOf(1, 2))
            val viewModel = viewModel(repository, history)
            advanceUntilIdle()

            viewModel.select(second)
            advanceUntilIdle()
            history.recent = listOf(2, 1)
            repository.state.value = LoadState.Error(AppError.Network)
            advanceUntilIdle()
            assertEquals(second.isu, viewModel.uiState.value.selectedIsu)
            assertEquals(listOf(first, second), viewModel.uiState.value.recentFriends)

            val renamed = first.copy(name = "Первый Новый")
            repository.state.value = LoadState.Content(listOf(second, renamed))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(second, state.applyTarget)
            assertEquals(listOf(renamed, second), state.recentFriends)
        }

    @Test
    fun `a friend list that loads empty resets the choice to the own schedule`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeFriendRepository(LoadState.Content(listOf(first, second)))
            val viewModel = viewModel(repository)
            advanceUntilIdle()
            viewModel.select(first)

            repository.state.value = LoadState.Content(emptyList())
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.applyTarget)

            repository.state.value = LoadState.Content(listOf(first, second))
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.selectedIsu)
        }

    @Test
    fun `a closed schedule cannot be chosen`() = runTest(mainDispatcherRule.dispatcher) {
        val closed = user(3, "Закрытый", schedule = false)
        val viewModel = viewModel(FakeFriendRepository(LoadState.Content(listOf(first, closed))))
        advanceUntilIdle()

        viewModel.select(closed)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.applyTarget)
    }

    @Test
    fun `the pending selection and the recent order are restored from saved state`() =
        runTest(mainDispatcherRule.dispatcher) {
            val third = user(3, "Третий")
            val friends = listOf(first, second, third)
            val savedState = SavedStateHandle(mapOf(FriendSelectionContract.ARG_SELECTED_ISU to first.isu))
            val before = viewModel(FakeFriendRepository(LoadState.Content(friends)), FakeHistory(listOf(2, 3)), savedState)
            advanceUntilIdle()
            assertEquals(listOf(first, second, third), before.uiState.value.recentFriends)
            before.select(third)
            advanceUntilIdle()

            // A new process: the history changed meanwhile, the friend list arrives in another order.
            val restored = viewModel(
                FakeFriendRepository(LoadState.Content(friends.reversed())),
                FakeHistory(listOf(3, 2, 1)),
                savedState
            )
            assertEquals(third.isu, restored.uiState.value.selectedIsu)
            advanceUntilIdle()

            val state = restored.uiState.value
            assertEquals(third, state.applyTarget)
            assertEquals(listOf(first, second, third), state.recentFriends)
        }

    @Test
    fun `the own schedule chosen before process death stays chosen after restore`() =
        runTest(mainDispatcherRule.dispatcher) {
            val savedState = SavedStateHandle(mapOf(FriendSelectionContract.ARG_SELECTED_ISU to first.isu))
            val before = viewModel(FakeFriendRepository(LoadState.Content(listOf(first))), savedState = savedState)
            advanceUntilIdle()
            before.selectOwnSchedule()

            val restored = viewModel(FakeFriendRepository(LoadState.Content(listOf(first))), savedState = savedState)
            advanceUntilIdle()

            assertNull(restored.uiState.value.selectedIsu)
        }

    @Test
    fun `folds the signed-in user into state, also when it arrives late or the list failed`() =
        runTest(mainDispatcherRule.dispatcher) {
            val me = user(7, "Я Сам")
            val repository = FakeFriendRepository(LoadState.Content(listOf(first)))
            val viewModel = viewModel(repository)
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.currentUser)
            assertEquals(FriendSelectorBody.Users(listOf(first)), viewModel.uiState.value.body)

            repository.user.value = me
            advanceUntilIdle()
            assertEquals(me, viewModel.uiState.value.currentUser)

            repository.state.value = LoadState.Error(AppError.Network)
            advanceUntilIdle()
            assertEquals(me, viewModel.uiState.value.currentUser)
        }

    @Test
    fun `wide scope searches registered people after the debounce and narrow scope does not`() =
        runTest(mainDispatcherRule.dispatcher) {
            val stranger = user(2, "Чужой")
            val search = RegisteredPeopleSearch(registered = listOf(stranger))
            val viewModel = viewModel(FakeFriendRepository(LoadState.Content(listOf(first))), search = search)
            advanceUntilIdle()

            viewModel.onQueryChanged("чуж")
            advanceUntilIdle()
            assertEquals(emptyList<String>(), search.queries)
            assertEquals(FriendSelectorBody.NoMatches, viewModel.uiState.value.body)

            viewModel.selectScope(FriendSelectorScope.ALL)
            advanceUntilIdle()
            assertEquals(listOf("чуж"), search.queries)
            assertEquals(FriendSelectorScope.ALL, viewModel.uiState.value.scope)
            assertEquals(FriendSelectorBody.Users(listOf(stranger)), viewModel.uiState.value.body)

            viewModel.select(stranger)
            viewModel.selectScope(FriendSelectorScope.FRIENDS)
            viewModel.onQueryChanged("")
            advanceUntilIdle()
            assertEquals(FriendSelectorScope.FRIENDS, viewModel.uiState.value.scope)
            assertEquals(FriendSelectorBody.Users(listOf(first)), viewModel.uiState.value.body)
            assertEquals(stranger, viewModel.uiState.value.applyTarget)
        }

    @Test
    fun `wide scope stays available without friends, reports failures and retries the search`() =
        runTest(mainDispatcherRule.dispatcher) {
            val search = RegisteredPeopleSearch(error = AppError.Network)
            val repository = FakeFriendRepository(LoadState.Content(emptyList()))
            val viewModel = viewModel(repository, search = search)
            advanceUntilIdle()
            assertEquals(FriendSelectorBody.NoFriends, viewModel.uiState.value.body)

            viewModel.selectScope(FriendSelectorScope.ALL)
            advanceUntilIdle()
            assertEquals(FriendSelectorBody.PeopleIdle, viewModel.uiState.value.body)
            viewModel.onQueryChanged("а")
            advanceUntilIdle()
            assertEquals(FriendSelectorBody.PeopleError(AppError.Network), viewModel.uiState.value.body)

            search.error = null
            viewModel.retry()
            advanceUntilIdle()
            assertEquals(listOf("а", "а"), search.queries)
            assertEquals(1, repository.refreshRequests)
            assertEquals(FriendSelectorBody.PeopleEmpty, viewModel.uiState.value.body)

            viewModel.onQueryChanged("")
            advanceUntilIdle()
            assertEquals(FriendSelectorBody.PeopleIdle, viewModel.uiState.value.body)
        }

    @Test
    fun `the people search shows progress while it runs`() = runTest(mainDispatcherRule.dispatcher) {
        val search = RegisteredPeopleSearch(gate = CompletableDeferred())
        val viewModel = viewModel(FakeFriendRepository(LoadState.Content(listOf(first))), search = search)
        advanceUntilIdle()

        viewModel.selectScope(FriendSelectorScope.ALL)
        viewModel.onQueryChanged("ан")
        advanceUntilIdle()

        assertEquals(FriendSelectorBody.PeopleLoading, viewModel.uiState.value.body)
        assertTrue(viewModel.uiState.value.canApply)
        search.gate?.complete(Unit)
    }

    @Test
    fun `apply for a friend records the history once and delivers the friend`() =
        runTest(mainDispatcherRule.dispatcher) {
            val history = FakeHistory()
            val viewModel = viewModel(FakeFriendRepository(LoadState.Content(listOf(first, second))), history)
            advanceUntilIdle()
            viewModel.select(second)

            viewModel.apply()
            viewModel.apply()
            advanceUntilIdle()
            // The event waits for a view that collects after the tap.
            val events = mutableListOf<FriendSelectorEvent>()
            backgroundScope.launch { viewModel.events.collect { events += it } }
            runCurrent()

            assertEquals(listOf(second.isu), history.recorded)
            assertEquals(listOf<FriendSelectorEvent>(FriendSelectorEvent.Apply(second)), events)
        }

    @Test
    fun `apply for the own schedule records nothing and delivers no friend`() =
        runTest(mainDispatcherRule.dispatcher) {
            val history = FakeHistory()
            val viewModel = viewModel(
                FakeFriendRepository(LoadState.Content(listOf(first))),
                history,
                SavedStateHandle(mapOf(FriendSelectionContract.ARG_SELECTED_ISU to first.isu))
            )
            val events = mutableListOf<FriendSelectorEvent>()
            backgroundScope.launch { viewModel.events.collect { events += it } }
            advanceUntilIdle()
            viewModel.selectOwnSchedule()

            viewModel.apply()
            advanceUntilIdle()
            runCurrent()

            assertEquals(emptyList<Int>(), history.recorded)
            assertEquals(listOf<FriendSelectorEvent>(FriendSelectorEvent.Apply(null)), events)
        }

    @Test
    fun `apply is ignored until the friend list has loaded`() = runTest(mainDispatcherRule.dispatcher) {
        val history = FakeHistory()
        val repository = FakeFriendRepository(LoadState.Loading)
        val viewModel = viewModel(repository, history)
        val events = mutableListOf<FriendSelectorEvent>()
        backgroundScope.launch { viewModel.events.collect { events += it } }
        advanceUntilIdle()

        viewModel.apply()
        advanceUntilIdle()
        runCurrent()
        assertTrue(events.isEmpty())

        repository.state.value = LoadState.Content(listOf(first))
        advanceUntilIdle()
        viewModel.apply()
        advanceUntilIdle()
        runCurrent()
        assertEquals(listOf<FriendSelectorEvent>(FriendSelectorEvent.Apply(null)), events)
    }

    private fun viewModel(
        repository: FakeFriendRepository,
        history: FakeHistory = FakeHistory(),
        savedState: SavedStateHandle = SavedStateHandle(),
        search: RegisteredPeopleSearch = RegisteredPeopleSearch()
    ) = FriendSelectorViewModel(repository, history, search, savedState)

    private fun user(isu: Int, name: String, vararg groups: UserGroup, schedule: Boolean = true) = UserSummary(
        isu = isu,
        name = name,
        pictureUrl = null,
        groups = groups.toList(),
        sharing = UserSharing(sport = true, schedule = schedule)
    )

    private class RegisteredPeopleSearch(
        private val registered: List<UserSummary> = emptyList(),
        var error: AppError? = null,
        val gate: CompletableDeferred<Unit>? = null
    ) : PeopleSearchRepository {
        val queries = mutableListOf<String>()

        override suspend fun search(query: String, offset: Int): AppResult<PeopleSearchPage> {
            queries += query
            gate?.await()
            error?.let { return AppResult.Failure(it) }
            val results = registered.map { user ->
                PersonSearchResult(user.isu, user.name, user.pictureUrl, UserProfile(user, RelationshipState.NONE))
            } + PersonSearchResult(999999, "Незарегистрированный", null, null)
            return AppResult.Success(PeopleSearchPage(results, results.size, null))
        }
    }

    private class FakeFriendRepository(
        initialState: LoadState<List<UserSummary>>,
        private val gated: Boolean = false
    ) : FriendRepository {
        val state = MutableStateFlow(initialState)
        val user = MutableStateFlow<UserSummary?>(null)
        var refreshRequests = 0
        private var gate = CompletableDeferred<Unit>()

        override fun observeFriendList(): Flow<LoadState<List<UserSummary>>> = state

        override fun observeCurrentUser(): Flow<UserSummary?> = user

        override suspend fun refreshFriendList() {
            refreshRequests += 1
            if (gated) gate.await()
        }

        fun finishRefresh() {
            gate.complete(Unit)
            gate = CompletableDeferred()
        }

        override val currentFriends: List<UserSummary>?
            get() = state.value.valueOrNull()
    }

    private class FakeHistory(var recent: List<Int> = emptyList()) : FriendSelectionHistory {
        val recorded = mutableListOf<Int>()

        override suspend fun getRecentIsu(): List<Int> = recent

        override suspend fun record(isu: Int) {
            recorded += isu
        }
    }
}
