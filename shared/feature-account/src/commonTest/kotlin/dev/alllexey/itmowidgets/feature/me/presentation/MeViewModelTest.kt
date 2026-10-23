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
import dev.alllexey.itmowidgets.core.testing.FakeDevicesOverview
import dev.alllexey.itmowidgets.core.testing.FakeSessionRepository
import dev.alllexey.itmowidgets.core.testing.FakeSocialRepository
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.core.CorePlurals
import dev.alllexey.itmowidgets.shared.feature.account.AccountStrings
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

        val viewModel = MeViewModel(repository, FakeSocialRepository(), FakeCustomServicesRepository(true), FakeDevicesOverview())
        advanceUntilIdle()

        val header = viewModel.uiState.value.header
        assertEquals(UiText.Dynamic("Иванов Иван"), header.name)
        assertEquals("ИИ", header.initials)
        assertEquals(123456, header.isu)
        assertEquals(true, header.shareable)
    }

    @Test
    fun theHeaderFallsBackToBackendAndTakesTheGroupFromIt() = runTest(main.dispatcher) {
        val social = FakeSocialRepository().apply { currentUser.value = friendProfile(9).user }
        val viewModel = MeViewModel(FakeSessionRepository(SessionState.SignedIn(null)), social, FakeCustomServicesRepository(true), FakeDevicesOverview())
        advanceUntilIdle()

        val header = viewModel.uiState.value.header
        assertEquals(UiText.Dynamic("Пользователь 9"), header.name)
        assertEquals(UiText.Res(AccountStrings.me_group_format, listOf("M3100", 1, "ФИТиП")), header.group)
        assertEquals(9, header.isu)
    }

    @Test
    fun theFriendsRowCountsFriendsAndRequests() {
        fun subtitle(friends: Int, requests: Int) = MeUiState(friends = MeFriendsSummary.Content(friends, requests)).friendsSubtitle
        assertEquals(UiText.Res(AccountStrings.me_friends_none), subtitle(0, 0))
        assertEquals(UiText.Plural(CorePlurals.friends_count, 3, listOf(3)), subtitle(3, 0))
        assertEquals(
            UiText.Joined(
                listOf(
                    UiText.Plural(CorePlurals.friends_count, 3, listOf(3)),
                    UiText.Plural(CorePlurals.friend_requests_count, 1, listOf(1)),
                ),
                ", ",
            ),
            subtitle(3, 1),
        )
        assertEquals(null, MeUiState(friends = MeFriendsSummary.Disabled).friendsSubtitle)
    }

    @Test
    fun summarizesFriendsAndIncomingRequestsFromTheSocialRepository() =
        runTest(main.dispatcher) {
            val social = FakeSocialRepository().apply {
                friends.value = LoadState.Content(listOf(friendProfile(1), friendProfile(2)))
                requests.value = LoadState.Content(FriendRequests(listOf(friendProfile(3)), emptyList()))
                currentUser.value = friendProfile(9).user
            }

            val viewModel = MeViewModel(FakeSessionRepository(SessionState.SignedIn(null)), social, FakeCustomServicesRepository(true), FakeDevicesOverview())
            advanceUntilIdle()

            assertEquals(MeFriendsSummary.Content(friends = 2, incomingRequests = 1), viewModel.uiState.value.friends)
            assertEquals(1, viewModel.uiState.value.incomingRequests)
            assertEquals(1, social.refreshes)
        }

    @Test
    fun disabledServicesAndErrorsAreReportedAsSuch() = runTest(main.dispatcher) {
        val social = FakeSocialRepository().apply { friends.value = LoadState.Disabled }
        val viewModel = MeViewModel(FakeSessionRepository(SessionState.SignedIn(null)), social, FakeCustomServicesRepository(false), FakeDevicesOverview())
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
        MeViewModel(FakeSessionRepository(SessionState.SignedIn(null)), social, services, FakeDevicesOverview())
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
            val viewModel = MeViewModel(FakeSessionRepository(SessionState.SignedIn(null)), social, FakeCustomServicesRepository(true), FakeDevicesOverview())

            viewModel.refresh(RefreshMode.Silent)
            advanceUntilIdle()
            assertEquals(1, social.refreshes)

            viewModel.refresh(RefreshMode.Silent)
            advanceUntilIdle()
            assertEquals(2, social.refreshes)
        }

    @Test
    fun delegatesSignOutOnce() = runTest(main.dispatcher) {
        val repository = FakeSessionRepository(SessionState.SignedIn(null))
        val viewModel = MeViewModel(repository, FakeSocialRepository(), FakeCustomServicesRepository(true), FakeDevicesOverview())

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
