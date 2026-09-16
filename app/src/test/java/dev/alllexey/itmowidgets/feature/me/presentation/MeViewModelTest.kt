package dev.alllexey.itmowidgets.feature.me.presentation

import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.core.social.FriendRequests
import dev.alllexey.itmowidgets.core.social.SocialRepository
import dev.alllexey.itmowidgets.core.social.SocialState
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

        val viewModel = MeViewModel(repository, FakeSocialRepository(), FakeServices(true))
        advanceUntilIdle()

        assertEquals(user, viewModel.uiState.value.user)
    }

    @Test
    fun `summarizes friends and incoming requests from the social repository`() =
        runTest(mainDispatcherRule.dispatcher) {
            val social = FakeSocialRepository().apply {
                friends.value = SocialState.Content(listOf(friendProfile(1), friendProfile(2)))
                requests.value = SocialState.Content(FriendRequests(listOf(friendProfile(3)), emptyList()))
                currentUser.value = friendProfile(9).user
            }

            val viewModel = MeViewModel(FakeSessionRepository(SessionState.SignedIn(null)), social, FakeServices(true))
            advanceUntilIdle()

            assertEquals(MeFriendsSummary.Content(friends = 2, incomingRequests = 1), viewModel.uiState.value.friends)
            assertEquals(9, viewModel.uiState.value.backendUser?.isu)
            assertEquals(1, social.refreshes)
        }

    @Test
    fun `disabled services and errors are reported as such`() = runTest(mainDispatcherRule.dispatcher) {
        val social = FakeSocialRepository().apply { friends.value = SocialState.Disabled }
        val viewModel = MeViewModel(FakeSessionRepository(SessionState.SignedIn(null)), social, FakeServices(false))
        advanceUntilIdle()
        assertEquals(MeFriendsSummary.Disabled, viewModel.uiState.value.friends)

        social.friends.value = SocialState.Error(AppError.Network)
        advanceUntilIdle()
        assertEquals(MeFriendsSummary.Error, viewModel.uiState.value.friends)
    }

    @Test
    fun `re-enabling services refreshes the social data`() = runTest(mainDispatcherRule.dispatcher) {
        val social = FakeSocialRepository()
        val services = FakeServices(false)
        MeViewModel(FakeSessionRepository(SessionState.SignedIn(null)), social, services)
        advanceUntilIdle()
        assertEquals(1, social.refreshes)

        services.enabled.value = true
        advanceUntilIdle()

        assertEquals(2, social.refreshes)
    }

    @Test
    fun `delegates sign out once`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeSessionRepository(SessionState.SignedIn(null))
        val viewModel = MeViewModel(repository, FakeSocialRepository(), FakeServices(true))

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

    private class FakeServices(enabled: Boolean) : CustomServicesRepository {
        val enabled = MutableStateFlow(enabled)
        override fun observeEnabled(): Flow<Boolean> = enabled
        override suspend fun isEnabled(): Boolean = enabled.value
        override suspend fun setEnabled(enabled: Boolean) = Unit
    }

    private class FakeSocialRepository : SocialRepository {
        val friends = MutableStateFlow<SocialState<List<UserProfile>>>(SocialState.Loading)
        val requests = MutableStateFlow<SocialState<FriendRequests>>(SocialState.Loading)
        val currentUser = MutableStateFlow<UserSummary?>(null)
        var refreshes = 0

        override fun observeFriends(): Flow<SocialState<List<UserProfile>>> = friends
        override fun observeRequests(): Flow<SocialState<FriendRequests>> = requests
        override fun observeCurrentUser(): Flow<UserSummary?> = currentUser
        override val currentFriends: List<UserProfile>? get() = (friends.value as? SocialState.Content)?.value
        override suspend fun refresh() {
            refreshes += 1
        }
        override suspend fun userFriends(isu: Int): AppResult<List<UserProfile>> = AppResult.Success(emptyList())
        override suspend fun profile(isu: Int): AppResult<UserProfile> = error("not used")
        override suspend fun lookup(isus: List<Int>): AppResult<List<UserProfile>> = error("not used")
        override suspend fun sendRequest(isu: Int): AppResult<UserProfile> = error("not used")
        override suspend fun acceptRequest(isu: Int): AppResult<UserProfile> = error("not used")
        override suspend fun rejectRequest(isu: Int): AppResult<UserProfile> = error("not used")
        override suspend fun cancelRequest(isu: Int): AppResult<UserProfile> = error("not used")
        override suspend fun removeFriend(isu: Int): AppResult<UserProfile> = error("not used")
    }

    private class FakeSessionRepository(
        initialState: SessionState
    ) : SessionRepository {
        private val mutableState = MutableStateFlow(initialState)
        override val state: StateFlow<SessionState> = mutableState
        var signOutRequests = 0

        override suspend fun initialize() = Unit

        override suspend fun completeItmoIdLogin(tokenResponseJson: String): AppResult<Unit> =
            AppResult.Success(Unit)

        override suspend fun signInWithRefreshToken(refreshToken: String): AppResult<Unit> =
            AppResult.Success(Unit)

        override suspend fun signOut() {
            signOutRequests += 1
            mutableState.value = SessionState.SignedOut
        }
    }
}
