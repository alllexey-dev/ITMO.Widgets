package dev.alllexey.itmowidgets.feature.social.presentation

import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.social.FriendRequests
import dev.alllexey.itmowidgets.core.social.SocialRepository
import dev.alllexey.itmowidgets.core.testing.FakeSocialRepository
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.testing.profile
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.core.Res
import dev.alllexey.itmowidgets.shared.core.user_name_placeholder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FriendsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `friends tab lists friends with a remove action and requests tab groups by direction`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeSocialRepository().apply {
                friends.value = LoadState.Content(listOf(profile(1, RelationshipState.FRIENDS)))
                requests.value = LoadState.Content(
                    FriendRequests(
                        incoming = listOf(profile(2, RelationshipState.INCOMING)),
                        outgoing = listOf(profile(3, RelationshipState.OUTGOING))
                    )
                )
            }
            val viewModel = FriendsViewModel(repository)
            advanceUntilIdle()

            val friendsTab = viewModel.uiState.value as FriendsUiState.Content
            assertEquals(1, repository.refreshes)
            assertEquals(1, friendsTab.incomingCount)
            val friendRow = (friendsTab.items.single() as UserListItem.User).row
            assertEquals(1, friendRow.isu)
            assertEquals(null, friendRow.primary)
            assertEquals(UserAction.REMOVE, friendRow.secondary)

            viewModel.selectTab(FriendsTab.REQUESTS)
            advanceUntilIdle()

            val requestsTab = viewModel.uiState.value as FriendsUiState.Content
            assertEquals(
                listOf(
                    UserListItem.Header(UiText.Resource(R.string.friends_section_incoming)),
                    "user:2",
                    UserListItem.Header(UiText.Resource(R.string.friends_section_outgoing)),
                    "user:3"
                ),
                requestsTab.items.map { if (it is UserListItem.User) "user:${it.row.isu}" else it }
            )
            val incoming = (requestsTab.items[1] as UserListItem.User).row
            assertEquals(UserAction.ACCEPT, incoming.primary)
            assertEquals(UserAction.REJECT, incoming.secondary)
            val outgoing = (requestsTab.items[3] as UserListItem.User).row
            assertEquals(UserAction.CANCEL, outgoing.secondary)
        }

    @Test
    fun `accepting moves the person to friends and removal asks for confirmation first`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeSocialRepository().apply {
                friends.value = LoadState.Content(emptyList())
                requests.value = LoadState.Content(
                    FriendRequests(incoming = listOf(profile(2, RelationshipState.INCOMING)), outgoing = emptyList())
                )
            }
            val viewModel = FriendsViewModel(repository)
            viewModel.selectTab(FriendsTab.REQUESTS)
            advanceUntilIdle()
            val incoming = ((viewModel.uiState.value as FriendsUiState.Content).items[1] as UserListItem.User).row

            viewModel.onAction(incoming, UserAction.ACCEPT)
            advanceUntilIdle()

            assertEquals(listOf("accept:2"), repository.actions)
            assertEquals(0, (viewModel.uiState.value as FriendsUiState.Content).incomingCount)
            viewModel.selectTab(FriendsTab.FRIENDS)
            advanceUntilIdle()
            val friend = ((viewModel.uiState.value as FriendsUiState.Content).items.single() as UserListItem.User).row

            viewModel.onAction(friend, UserAction.REMOVE)
            assertEquals(FriendsEvent.ConfirmRemove(2, UiText.Dynamic("Пользователь 2")), viewModel.events.first())
            assertEquals(listOf("accept:2"), repository.actions)

            viewModel.removeFriend(2)
            advanceUntilIdle()
            assertEquals(listOf("accept:2", "remove:2"), repository.actions)
            assertTrue((viewModel.uiState.value as FriendsUiState.Content).items.isEmpty())
        }

    @Test
    fun `a failed action surfaces an event and keeps the row`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeSocialRepository().apply {
            friends.value = LoadState.Content(emptyList())
            requests.value = LoadState.Content(
                FriendRequests(incoming = emptyList(), outgoing = listOf(profile(3, RelationshipState.OUTGOING)))
            )
            actionError = AppError.Network
        }
        val viewModel = FriendsViewModel(repository)
        viewModel.selectTab(FriendsTab.REQUESTS)
        advanceUntilIdle()
        val outgoing = ((viewModel.uiState.value as FriendsUiState.Content).items[1] as UserListItem.User).row

        viewModel.onAction(outgoing, UserAction.CANCEL)

        assertEquals(FriendsEvent.ActionFailed(AppError.Network), viewModel.events.first())
        advanceUntilIdle()
        assertEquals(2, (viewModel.uiState.value as FriendsUiState.Content).items.size)
    }

    @Test
    fun `disabled services and errors map to their states while a refresh keeps progress`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeSocialRepository()
            val viewModel = FriendsViewModel(repository)
            advanceUntilIdle()
            assertEquals(FriendsUiState.Loading, viewModel.uiState.value)

            repository.friends.value = LoadState.Disabled
            repository.requests.value = LoadState.Disabled
            advanceUntilIdle()
            assertEquals(FriendsUiState.Disabled, viewModel.uiState.value)

            repository.friends.value = LoadState.Error(AppError.Unauthorized)
            repository.requests.value = LoadState.Content(FriendRequests.EMPTY)
            advanceUntilIdle()
            assertEquals(FriendsUiState.Error(AppError.Unauthorized), viewModel.uiState.value)
        }

    @Test
    fun `an empty tab says which kind of empty it is`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeSocialRepository().apply {
            friends.value = LoadState.Content(emptyList())
            requests.value = LoadState.Content(FriendRequests.EMPTY)
        }
        val viewModel = FriendsViewModel(repository)
        advanceUntilIdle()
        assertEquals(FriendsEmpty.NO_FRIENDS, (viewModel.uiState.value as FriendsUiState.Content).empty)

        viewModel.selectTab(FriendsTab.REQUESTS)
        advanceUntilIdle()
        assertEquals(FriendsEmpty.NO_REQUESTS, (viewModel.uiState.value as FriendsUiState.Content).empty)

        repository.requests.value = LoadState.Content(
            FriendRequests(incoming = listOf(profile(2, RelationshipState.INCOMING)), outgoing = emptyList())
        )
        advanceUntilIdle()
        assertEquals(null, (viewModel.uiState.value as FriendsUiState.Content).empty)
    }

    @Test
    fun `an empty name confirms the removal with the placeholder`() = runTest(mainDispatcherRule.dispatcher) {
        val unnamed = profile(7, RelationshipState.FRIENDS).let { it.copy(user = it.user.copy(name = " ")) }
        val repository = FakeSocialRepository().apply {
            friends.value = LoadState.Content(listOf(unnamed))
            requests.value = LoadState.Content(FriendRequests.EMPTY)
        }
        val viewModel = FriendsViewModel(repository)
        advanceUntilIdle()
        val row = ((viewModel.uiState.value as FriendsUiState.Content).items.single() as UserListItem.User).row
        val placeholder = UiText.Res(Res.string.user_name_placeholder, listOf(7))
        assertEquals(placeholder, row.displayName)

        viewModel.onAction(row, UserAction.REMOVE)

        assertEquals(FriendsEvent.ConfirmRemove(7, placeholder), viewModel.events.first())
    }

    @Test
    fun `entry refreshes silently, a pull joins it with the indicator and a retry forces a new refresh`() =
        runTest(mainDispatcherRule.dispatcher) {
            val gate = CompletableDeferred<Unit>()
            val repository = GatedSocialRepository(gate).apply {
                friends.value = LoadState.Content(emptyList())
                requests.value = LoadState.Content(FriendRequests.EMPTY)
            }
            val viewModel = FriendsViewModel(repository)
            runCurrent()
            assertEquals(1, repository.refreshes)
            assertFalse((viewModel.uiState.value as FriendsUiState.Content).refreshing)

            viewModel.refresh(RefreshMode.Pull)
            runCurrent()
            assertEquals(1, repository.refreshes)
            assertTrue((viewModel.uiState.value as FriendsUiState.Content).refreshing)

            gate.complete(Unit)
            runCurrent()
            assertFalse((viewModel.uiState.value as FriendsUiState.Content).refreshing)

            viewModel.refresh(RefreshMode.Force)
            runCurrent()
            assertEquals(2, repository.refreshes)
        }

    @Test
    fun `an error shows progress while a retry may replace it`() = runTest(mainDispatcherRule.dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val repository = GatedSocialRepository(gate).apply {
            friends.value = LoadState.Error(AppError.Network)
            requests.value = LoadState.Content(FriendRequests.EMPTY)
        }
        val viewModel = FriendsViewModel(repository)
        runCurrent()
        assertEquals(FriendsUiState.Error(AppError.Network), viewModel.uiState.value)

        viewModel.refresh(RefreshMode.Force)
        runCurrent()
        assertEquals(FriendsUiState.Loading, viewModel.uiState.value)

        gate.complete(Unit)
        runCurrent()
        assertEquals(FriendsUiState.Error(AppError.Network), viewModel.uiState.value)
    }

    @Test
    fun `a second tap on a busy row is ignored and an event waits for the next collector`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeSocialRepository().apply {
                friends.value = LoadState.Content(emptyList())
                requests.value = LoadState.Content(
                    FriendRequests(incoming = emptyList(), outgoing = listOf(profile(3, RelationshipState.OUTGOING)))
                )
                actionError = AppError.Network
            }
            val viewModel = FriendsViewModel(repository)
            viewModel.selectTab(FriendsTab.REQUESTS)
            advanceUntilIdle()
            val outgoing = ((viewModel.uiState.value as FriendsUiState.Content).items[1] as UserListItem.User).row

            viewModel.onAction(outgoing, UserAction.CANCEL)
            viewModel.onAction(outgoing, UserAction.CANCEL)
            advanceUntilIdle()

            assertEquals(listOf("cancel:3"), repository.actions)
            val events = mutableListOf<FriendsEvent>()
            backgroundScope.launch { viewModel.events.toList(events) }
            runCurrent()
            assertEquals(listOf<FriendsEvent>(FriendsEvent.ActionFailed(AppError.Network)), events)
        }

    /** A social repository whose refresh waits for [gate], so a test sees the refresh in flight. */
    private class GatedSocialRepository(
        private val gate: CompletableDeferred<Unit>,
        private val delegate: FakeSocialRepository = FakeSocialRepository()
    ) : SocialRepository by delegate {
        val friends get() = delegate.friends
        val requests get() = delegate.requests
        var refreshes = 0

        override suspend fun refresh() {
            refreshes += 1
            gate.await()
        }
    }
}
