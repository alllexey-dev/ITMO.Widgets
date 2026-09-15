package dev.alllexey.itmowidgets.feature.social.presentation

import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.social.FriendRequests
import dev.alllexey.itmowidgets.core.social.SocialState
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.text.UiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
                friends.value = SocialState.Content(listOf(profile(1, RelationshipState.FRIENDS)))
                requests.value = SocialState.Content(
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
                friends.value = SocialState.Content(emptyList())
                requests.value = SocialState.Content(
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
            assertEquals(FriendsEvent.ConfirmRemove(2, "Пользователь 2"), viewModel.eventFlow.first())
            assertEquals(listOf("accept:2"), repository.actions)

            viewModel.removeFriend(2)
            advanceUntilIdle()
            assertEquals(listOf("accept:2", "remove:2"), repository.actions)
            assertTrue((viewModel.uiState.value as FriendsUiState.Content).items.isEmpty())
        }

    @Test
    fun `a failed action surfaces an event and keeps the row`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeSocialRepository().apply {
            friends.value = SocialState.Content(emptyList())
            requests.value = SocialState.Content(
                FriendRequests(incoming = emptyList(), outgoing = listOf(profile(3, RelationshipState.OUTGOING)))
            )
            actionError = AppError.Network
        }
        val viewModel = FriendsViewModel(repository)
        viewModel.selectTab(FriendsTab.REQUESTS)
        advanceUntilIdle()
        val outgoing = ((viewModel.uiState.value as FriendsUiState.Content).items[1] as UserListItem.User).row

        viewModel.onAction(outgoing, UserAction.CANCEL)

        assertEquals(FriendsEvent.ActionFailed(AppError.Network), viewModel.eventFlow.first())
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

            repository.friends.value = SocialState.Disabled
            repository.requests.value = SocialState.Disabled
            advanceUntilIdle()
            assertEquals(FriendsUiState.Disabled, viewModel.uiState.value)

            repository.friends.value = SocialState.Error(AppError.Unauthorized)
            repository.requests.value = SocialState.Content(FriendRequests.EMPTY)
            advanceUntilIdle()
            assertEquals(FriendsUiState.Error(AppError.Unauthorized), viewModel.uiState.value)
        }
}
