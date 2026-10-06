package dev.alllexey.itmowidgets.feature.me.presentation

import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.core.social.FriendRequests
import dev.alllexey.itmowidgets.core.testing.FakeCustomServicesRepository
import dev.alllexey.itmowidgets.core.testing.FakeSessionRepository
import dev.alllexey.itmowidgets.core.testing.FakeSocialRepository
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

class MeViewModelTest {

    private val main = TestMainDispatcher()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    @Test
    fun rendersUserFromTheSharedSession() = runTest(main.dispatcher) {
        val user = CurrentUser(123456, "Иванов Иван", null)
        val repository = FakeSessionRepository(SessionState.SignedIn(user))

        val viewModel = MeViewModel(repository, FakeSocialRepository(), FakeCustomServicesRepository(true))
        advanceUntilIdle()

        assertEquals(user, viewModel.uiState.value.user)
    }

    @Test
    fun summarizesFriendsAndIncomingRequestsFromTheSocialRepository() =
        runTest(main.dispatcher) {
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
    fun disabledServicesAndErrorsAreReportedAsSuch() = runTest(main.dispatcher) {
        val social = FakeSocialRepository().apply { friends.value = LoadState.Disabled }
        val viewModel = MeViewModel(FakeSessionRepository(SessionState.SignedIn(null)), social, FakeCustomServicesRepository(false))
        advanceUntilIdle()
        assertEquals(MeFriendsSummary.Disabled, viewModel.uiState.value.friends)

        social.friends.value = LoadState.Error(AppError.Network)
        advanceUntilIdle()
        assertEquals(MeFriendsSummary.Error, viewModel.uiState.value.friends)
    }

    @Test
    fun reEnablingServicesRefreshesTheSocialData() = runTest(main.dispatcher) {
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
    fun aSilentRefreshOnReturnJoinsTheOneInFlightAndALaterOneReachesTheRepository() =
        runTest(main.dispatcher) {
            val social = FakeSocialRepository()
            val viewModel = MeViewModel(FakeSessionRepository(SessionState.SignedIn(null)), social, FakeCustomServicesRepository(true))

            viewModel.refresh(RefreshMode.Silent)
            advanceUntilIdle()
            assertEquals(1, social.refreshes)

            viewModel.refresh(RefreshMode.Silent)
            advanceUntilIdle()
            assertEquals(2, social.refreshes)
        }

    @Test
    fun webSignInIsOfferedOnlyWithTheConnection() = runTest(main.dispatcher) {
        val services = FakeCustomServicesRepository(false)
        val viewModel = MeViewModel(FakeSessionRepository(SessionState.SignedIn(null)), FakeSocialRepository(), services)
        advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.webLoginAvailable)

        services.enabled.value = true
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.webLoginAvailable)
    }

    @Test
    fun delegatesSignOutOnce() = runTest(main.dispatcher) {
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
