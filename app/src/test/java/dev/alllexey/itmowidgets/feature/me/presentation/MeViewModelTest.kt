package dev.alllexey.itmowidgets.feature.me.presentation

import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.core.social.FriendRequests
import dev.alllexey.itmowidgets.core.testing.FakeCustomServicesRepository
import dev.alllexey.itmowidgets.core.testing.FakeSessionRepository
import dev.alllexey.itmowidgets.core.testing.FakeSocialRepository
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `renders user from the shared session`() = runTest(mainDispatcherRule.dispatcher) {
        val user = CurrentUser(123456, "Иванов Иван", null)
        val repository = FakeSessionRepository(SessionState.SignedIn(user))

        val viewModel = MeViewModel(repository, FakeSocialRepository(), FakeCustomServicesRepository(true))
        advanceUntilIdle()

        assertEquals(user, viewModel.uiState.value.user)
    }

    @Test
    fun `summarizes friends and incoming requests from the social repository`() =
        runTest(mainDispatcherRule.dispatcher) {
            val social = FakeSocialRepository().apply {
                friends.value = LoadState.Content(listOf(friendProfile(1), friendProfile(2)))
                requests.value = LoadState.Content(FriendRequests(listOf(friendProfile(3)), emptyList()))
                currentUser.value = friendProfile(9).user
            }

            val viewModel = MeViewModel(FakeSessionRepository(SessionState.SignedIn(null)), social, FakeCustomServicesRepository(true))
            advanceUntilIdle()

            assertEquals(MeFriendsSummary.Content(friends = 2, incomingRequests = 1), viewModel.uiState.value.friends)
            assertEquals(9, viewModel.uiState.value.backendUser?.isu)
            assertEquals(1, social.refreshes)
        }

    @Test
    fun `disabled services and errors are reported as such`() = runTest(mainDispatcherRule.dispatcher) {
        val social = FakeSocialRepository().apply { friends.value = LoadState.Disabled }
        val viewModel = MeViewModel(FakeSessionRepository(SessionState.SignedIn(null)), social, FakeCustomServicesRepository(false))
        advanceUntilIdle()
        assertEquals(MeFriendsSummary.Disabled, viewModel.uiState.value.friends)

        social.friends.value = LoadState.Error(AppError.Network)
        advanceUntilIdle()
        assertEquals(MeFriendsSummary.Error, viewModel.uiState.value.friends)
    }

    @Test
    fun `re-enabling services refreshes the social data`() = runTest(mainDispatcherRule.dispatcher) {
        val social = FakeSocialRepository()
        val services = FakeCustomServicesRepository(false)
        MeViewModel(FakeSessionRepository(SessionState.SignedIn(null)), social, services)
        advanceUntilIdle()
        assertEquals(1, social.refreshes)

        services.enabled.value = true
        advanceUntilIdle()

        assertEquals(2, social.refreshes)
    }

    @Test
    fun `web sign-in is offered only with the connection`() = runTest(mainDispatcherRule.dispatcher) {
        val services = FakeCustomServicesRepository(false)
        val viewModel = MeViewModel(FakeSessionRepository(SessionState.SignedIn(null)), FakeSocialRepository(), services)
        advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.webLoginAvailable)

        services.enabled.value = true
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.webLoginAvailable)
    }

    @Test
    fun `delegates sign out once`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeSessionRepository(SessionState.SignedIn(null))
        val viewModel = MeViewModel(repository, FakeSocialRepository(), FakeCustomServicesRepository(true))

        viewModel.signOut()
        viewModel.signOut()
        advanceUntilIdle()

        assertEquals(1, repository.signOutRequests)
        assertEquals(false, viewModel.uiState.value.signOutInProgress)
    }

    private fun friendProfile(isu: Int) = UserProfile(
        UserSummary(isu, "Пользователь $isu", null, listOf(UserGroup("M3100", 1, "ФИТиП")), UserSharing(true, true)),
        RelationshipState.FRIENDS
    )
}
