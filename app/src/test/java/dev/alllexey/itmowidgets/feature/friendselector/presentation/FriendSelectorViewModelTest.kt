package dev.alllexey.itmowidgets.feature.friendselector.presentation

import dev.alllexey.itmowidgets.core.friend.FriendListState
import dev.alllexey.itmowidgets.core.friend.FriendRepository
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.result.AppError
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
            val viewModel = FriendSelectorViewModel(repository, history)

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
                FakeHistory()
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
            val viewModel = FriendSelectorViewModel(repository, FakeHistory())
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
            val viewModel = FriendSelectorViewModel(repository, FakeHistory())
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
            val viewModel = FriendSelectorViewModel(repository, FakeHistory())
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
    fun `records confirmed selection`() =
        runTest(mainDispatcherRule.dispatcher) {
            val history = FakeHistory()
            val viewModel = FriendSelectorViewModel(
                FakeFriendRepository(FriendListState.Content(emptyList())),
                history
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

    private class FakeFriendRepository(initialState: FriendListState) : FriendRepository {
        val state = MutableStateFlow(initialState)
        var refreshRequests = 0

        override fun observeFriendList(): Flow<FriendListState> = state

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
