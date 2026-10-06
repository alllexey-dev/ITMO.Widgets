package dev.alllexey.itmowidgets.feature.social.data

import dev.alllexey.itmowidgets.client.common.RelationshipState as ClientRelationshipState
import dev.alllexey.itmowidgets.client.common.UserCapabilities
import dev.alllexey.itmowidgets.client.common.UserData
import dev.alllexey.itmowidgets.client.common.UserProfile as ClientUserProfile
import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.client.friends.FriendsApi
import dev.alllexey.itmowidgets.client.users.IdTokenRequest
import dev.alllexey.itmowidgets.client.users.UserLookupRequest
import dev.alllexey.itmowidgets.client.users.UserLookupResponse
import dev.alllexey.itmowidgets.client.users.UserPrivacySettings
import dev.alllexey.itmowidgets.client.users.UsersApi
import dev.alllexey.itmowidgets.client.users.WebLoginPreview
import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.social.FriendRequests
import dev.alllexey.itmowidgets.core.testing.FakeBackendGate
import dev.alllexey.itmowidgets.core.testing.noDemo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SocialRepositoryImplTest {

    @Test
    fun refreshLoadsFriendsRequestsAndOwnProfile() = runTest {
        val api = FakeSocialApi().apply {
            friends = listOf(profile(1, ClientRelationshipState.FRIENDS))
            incoming = listOf(profile(2, ClientRelationshipState.INCOMING))
            outgoing = listOf(profile(3, ClientRelationshipState.OUTGOING))
            me = user(9)
        }
        val repository = SocialRepositoryImpl(services(enabled = true), api.users, api.friendships, backgroundScope, noDemo(), testAppDispatchers())

        repository.refresh()

        assertEquals(listOf(1), repository.friendIsus())
        assertEquals(listOf(2) to listOf(3), repository.requestIsus())
        assertEquals(9, repository.observeCurrentUser().first()?.isu)
        assertEquals(listOf(1), repository.currentFriends?.map(UserProfile::isu))
    }

    @Test
    fun peopleSeenInListsProfilesAndActionsAreCachedUntilTheSessionIsCleared() = runTest {
        val api = FakeSocialApi().apply {
            friends = listOf(profile(1, ClientRelationshipState.FRIENDS))
            incoming = listOf(profile(2, ClientRelationshipState.INCOMING))
            userFriends = listOf(profile(7, ClientRelationshipState.NONE))
            actionResult = { isu -> profile(isu, ClientRelationshipState.OUTGOING) }
        }
        val repository = SocialRepositoryImpl(services(enabled = true), api.users, api.friendships, backgroundScope, noDemo(), testAppDispatchers())
        assertNull(repository.cachedProfile(1))
        assertNull(repository.cachedUserFriends(1))

        repository.refresh()
        assertEquals(RelationshipState.FRIENDS, repository.cachedProfile(1)?.relationship)
        assertEquals(RelationshipState.INCOMING, repository.cachedProfile(2)?.relationship)
        assertNull(repository.cachedProfile(5))

        repository.profile(5)
        assertEquals(RelationshipState.OUTGOING, repository.cachedProfile(5)?.relationship)
        repository.userFriends(1)
        assertEquals(listOf(7), repository.cachedUserFriends(1)?.map(UserProfile::isu))

        api.actionResult = { isu -> profile(isu, ClientRelationshipState.NONE) }
        repository.cancelRequest(5)
        assertEquals(RelationshipState.NONE, repository.cachedProfile(5)?.relationship)

        repository.clearSessionData()
        assertNull(repository.cachedProfile(1))
        assertNull(repository.cachedProfile(5))
        assertNull(repository.cachedUserFriends(1))
    }

    @Test
    fun disabledServicesSkipTheBackendEntirely() = runTest {
        val api = FakeSocialApi()
        val repository = SocialRepositoryImpl(services(enabled = false), api.users, api.friendships, backgroundScope, noDemo(), testAppDispatchers())

        repository.refresh()

        assertEquals(LoadState.Disabled, repository.observeFriends().first())
        assertEquals(LoadState.Disabled, repository.observeRequests().first())
        assertEquals(0, api.calls)
        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), repository.userFriends(1))
        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), repository.profile(1))
        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), repository.sendRequest(1))
    }

    @Test
    fun ownProfileFailureDoesNotHideTheListsAndAListFailureIsTyped() = runTest {
        val api = FakeSocialApi().apply {
            friends = listOf(profile(1, ClientRelationshipState.FRIENDS))
            meFailure = BackendException.Transport(IOException("offline"))
            outgoingFailure = BackendException.Transport(IOException("offline"))
        }
        val repository = SocialRepositoryImpl(services(enabled = true), api.users, api.friendships, backgroundScope, noDemo(), testAppDispatchers())

        repository.refresh()

        assertEquals(listOf(1), repository.friendIsus())
        assertNull(repository.observeCurrentUser().first())
        assertEquals(LoadState.Error(AppError.Network), repository.observeRequests().first())
    }

    @Test
    fun acceptingARequestMovesThePersonFromIncomingToFriendsWithoutARefresh() = runTest {
        val api = FakeSocialApi().apply {
            incoming = listOf(profile(2, ClientRelationshipState.INCOMING), profile(4, ClientRelationshipState.INCOMING))
            actionResult = { isu -> profile(isu, ClientRelationshipState.FRIENDS) }
        }
        val repository = SocialRepositoryImpl(services(enabled = true), api.users, api.friendships, backgroundScope, noDemo(), testAppDispatchers())
        repository.refresh()

        val result = repository.acceptRequest(2)

        assertTrue(result is AppResult.Success)
        assertEquals(listOf(2), repository.friendIsus())
        assertEquals(listOf(4) to emptyList<Int>(), repository.requestIsus())
        assertEquals(listOf("acceptFriendRequest:2"), api.actions)
    }

    @Test
    fun sendingCancellingAndRemovingUpdateTheCachedLists() = runTest {
        val api = FakeSocialApi().apply {
            friends = listOf(profile(1, ClientRelationshipState.FRIENDS))
        }
        val repository = SocialRepositoryImpl(services(enabled = true), api.users, api.friendships, backgroundScope, noDemo(), testAppDispatchers())
        repository.refresh()

        api.actionResult = { isu -> profile(isu, ClientRelationshipState.OUTGOING) }
        repository.sendRequest(5)
        assertEquals(emptyList<Int>() to listOf(5), repository.requestIsus())

        api.actionResult = { isu -> profile(isu, ClientRelationshipState.NONE) }
        repository.cancelRequest(5)
        assertEquals(emptyList<Int>() to emptyList<Int>(), repository.requestIsus())

        repository.removeFriend(1)
        assertEquals(emptyList<Int>(), repository.friendIsus())
        assertEquals(listOf("sendFriendRequest:5", "cancelFriendRequest:5", "removeFriend:1"), api.actions)
    }

    @Test
    fun aFailedActionLeavesTheListsUntouched() = runTest {
        val api = FakeSocialApi().apply {
            incoming = listOf(profile(2, ClientRelationshipState.INCOMING))
            actionFailure = BackendException.Transport(IOException("offline"))
        }
        val repository = SocialRepositoryImpl(services(enabled = true), api.users, api.friendships, backgroundScope, noDemo(), testAppDispatchers())
        repository.refresh()

        assertEquals(AppResult.Failure(AppError.Network), repository.rejectRequest(2))

        assertEquals(listOf(2) to emptyList<Int>(), repository.requestIsus())
    }

    @Test
    fun lookupDeduplicatesChunksByFiftyAndKeepsRequestOrder() = runTest {
        val api = FakeSocialApi().apply {
            lookupResult = { isus -> isus.map { profile(it, ClientRelationshipState.NONE) } }
        }
        val repository = SocialRepositoryImpl(services(enabled = true), api.users, api.friendships, backgroundScope, noDemo(), testAppDispatchers())
        val isus = (1..60).toList() + 1

        val result = repository.lookup(isus)

        assertEquals((1..60).toList(), (result as AppResult.Success).value.map(UserProfile::isu))
        assertEquals(listOf(50, 10), api.lookups.map { it.size })
        assertEquals(AppResult.Success(emptyList<UserProfile>()), repository.lookup(emptyList()))
    }

    @Test
    fun clearingSessionDataForgetsEverythingLoaded() = runTest {
        val api = FakeSocialApi().apply { friends = listOf(profile(1, ClientRelationshipState.FRIENDS)); me = user(9) }
        val repository = SocialRepositoryImpl(services(enabled = true), api.users, api.friendships, backgroundScope, noDemo(), testAppDispatchers())
        repository.refresh()

        repository.clearSessionData()

        assertEquals(LoadState.Loading, repository.observeFriends().first())
        assertEquals(LoadState.Loading, repository.observeRequests().first())
        assertNull(repository.observeCurrentUser().first())
        assertNull(repository.currentFriends)
    }

    @Test
    fun targetFriendsRetainViewerCapabilitiesAndNeverOverwriteOwnFriendsCache() = runTest {
        val api = FakeSocialApi().apply { friends = listOf(profile(1, ClientRelationshipState.FRIENDS)) }
        val repository = SocialRepositoryImpl(services(enabled = true), api.users, api.friendships, backgroundScope, noDemo(), testAppDispatchers())
        repository.refresh()
        api.userFriends = listOf(profile(2, ClientRelationshipState.NONE).copy(user = user(2).copy(
            capabilities = UserCapabilities(false, false, true)
        )))
        val result = repository.userFriends(99) as AppResult.Success
        assertEquals(listOf(99), api.friendOwners)
        assertEquals(RelationshipState.NONE, result.value.single().relationship)
        assertTrue(result.value.single().user.sharing.friends)
        assertEquals(false, result.value.single().user.sharing.schedule)
        assertEquals(listOf(1), repository.friendIsus())
        api.userFriendsFailure = BackendException.Transport(IOException("offline"))
        assertEquals(AppResult.Failure(AppError.Network), repository.userFriends(99))
    }

    @Test
    fun optingOutClearsEveryBackendCacheAndReconnectingDoesNotRestoreIt() = runTest {
        val services = services(enabled = true)
        val api = populatedApi()
        val repository = SocialRepositoryImpl(services, api.users, api.friendships, backgroundScope, noDemo(), testAppDispatchers())
        repository.populateCaches()
        runCurrent()
        repository.assertCachesAvailable()

        services.optedIn.value = false
        runCurrent()

        repository.assertDisabled()
        services.optedIn.value = true
        runCurrent()
        repository.assertDisabled()
    }

    @Test
    fun refreshClearsBackendCachesEvenWhenTheOptOutCollectorHasNotRun() = runTest {
        val services = services(enabled = true)
        val api = populatedApi()
        val repository = SocialRepositoryImpl(services, api.users, api.friendships, backgroundScope, noDemo(), testAppDispatchers())
        repository.populateCaches()
        runCurrent()
        val calls = api.calls

        services.optedIn.value = false
        repository.refresh()
        services.optedIn.value = true
        runCurrent()
        repository.lookup(listOf(99))

        repository.assertDisabled()
        assertEquals(calls + 1, api.calls)
    }

    @Test
    fun aGatedRequestClearsBackendCachesEvenWhenTheOptOutCollectorHasNotRun() = runTest {
        val services = services(enabled = true)
        val api = populatedApi()
        val repository = SocialRepositoryImpl(services, api.users, api.friendships, backgroundScope, noDemo(), testAppDispatchers())
        repository.populateCaches()
        runCurrent()
        val calls = api.calls

        services.optedIn.value = false
        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), repository.profile(7))
        services.optedIn.value = true
        runCurrent()
        repository.lookup(listOf(99))

        repository.assertDisabled()
        assertEquals(calls + 1, api.calls)
    }

    @Test
    fun pendingProfileAndTargetFriendsCannotRestoreCachesAfterOptingOut() = runTest {
        val services = services(enabled = true)
        val api = populatedApi()
        val repository = SocialRepositoryImpl(services, api.users, api.friendships, backgroundScope, noDemo(), testAppDispatchers())
        repository.populateCaches()
        val gate = ResponseGate(expectedCalls = 2)
        api.beforeResponse = { gate.await() }
        val profile = async { repository.profile(5) }
        val friends = async { repository.userFriends(1) }
        gate.entered.await()

        services.optedIn.value = false
        runCurrent()
        gate.open()

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), profile.await())
        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), friends.await())
        repository.assertDisabled()
    }

    @Test
    fun preDisconnectResponsesCannotReplaceFreshProfileAndFriendsAfterReconnect() = runTest {
        val services = services(enabled = true)
        val api = populatedApi()
        val repository = SocialRepositoryImpl(services, api.users, api.friendships, backgroundScope, noDemo(), testAppDispatchers())
        repository.populateCaches()
        val gate = ResponseGate(expectedCalls = 2)
        api.beforeResponse = { gate.await() }
        val oldProfile = async { repository.profile(5) }
        val oldFriends = async { repository.userFriends(1) }
        gate.entered.await()
        services.optedIn.value = false
        runCurrent()
        services.optedIn.value = true
        runCurrent()
        repository.assertDisabled()

        api.beforeResponse = {}
        api.actionResult = { isu -> profile(isu, ClientRelationshipState.FRIENDS) }
        api.userFriends = listOf(profile(8, ClientRelationshipState.NONE))
        assertTrue(repository.profile(5) is AppResult.Success)
        assertTrue(repository.userFriends(1) is AppResult.Success)
        gate.open()

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), oldProfile.await())
        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), oldFriends.await())
        assertEquals(RelationshipState.FRIENDS, repository.cachedProfile(5)?.relationship)
        assertEquals(listOf(8), repository.cachedUserFriends(1)?.map(UserProfile::isu))
        assertEquals(LoadState.Disabled, repository.observeFriends().first())
        assertEquals(LoadState.Disabled, repository.observeRequests().first())
    }

    @Test
    fun pendingRefreshCannotRestoreListsOrOwnProfileAfterOptingOut() = runTest {
        val services = services(enabled = true)
        val api = populatedApi()
        val repository = SocialRepositoryImpl(services, api.users, api.friendships, backgroundScope, noDemo(), testAppDispatchers())
        repository.populateCaches()
        val gate = ResponseGate(expectedCalls = 4)
        api.beforeResponse = { gate.await() }
        val refresh = launch { repository.refresh() }
        gate.entered.await()

        services.optedIn.value = false
        runCurrent()
        gate.open()
        refresh.join()

        repository.assertDisabled()
    }

    @Test
    fun preDisconnectRefreshCannotReplaceFreshListsOrOwnProfileAfterReconnect() = runTest {
        val services = services(enabled = true)
        val api = populatedApi()
        val repository = SocialRepositoryImpl(services, api.users, api.friendships, backgroundScope, noDemo(), testAppDispatchers())
        repository.populateCaches()
        val gate = ResponseGate(expectedCalls = 4)
        api.beforeResponse = { gate.await() }
        val oldRefresh = launch { repository.refresh() }
        gate.entered.await()
        services.optedIn.value = false
        runCurrent()
        services.optedIn.value = true
        runCurrent()
        repository.assertDisabled()

        api.beforeResponse = {}
        api.friends = listOf(profile(3, ClientRelationshipState.FRIENDS))
        api.incoming = listOf(profile(4, ClientRelationshipState.INCOMING))
        api.me = user(10)
        repository.refresh()
        gate.open()
        oldRefresh.join()

        assertEquals(listOf(3), repository.friendIsus())
        assertEquals(listOf(4) to emptyList<Int>(), repository.requestIsus())
        assertEquals(10, repository.observeCurrentUser().first()?.isu)
    }

    @Test
    fun pendingRelationshipActionCannotRestoreAProfileAfterOptingOut() = runTest {
        val services = services(enabled = true)
        val api = populatedApi()
        val repository = SocialRepositoryImpl(services, api.users, api.friendships, backgroundScope, noDemo(), testAppDispatchers())
        repository.populateCaches()
        api.actionResult = { isu -> profile(isu, ClientRelationshipState.OUTGOING) }
        val gate = ResponseGate()
        api.beforeResponse = { gate.await() }
        val action = async { repository.sendRequest(5) }
        gate.entered.await()

        services.optedIn.value = false
        runCurrent()
        gate.open()

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), action.await())
        repository.assertDisabled()
    }

    @Test
    fun preDisconnectRelationshipActionCannotMutateFreshListsAfterReconnect() = runTest {
        val services = services(enabled = true)
        val api = populatedApi()
        val repository = SocialRepositoryImpl(services, api.users, api.friendships, backgroundScope, noDemo(), testAppDispatchers())
        repository.populateCaches()
        api.actionResult = { isu -> profile(isu, ClientRelationshipState.OUTGOING) }
        val gate = ResponseGate()
        api.beforeResponse = { gate.await() }
        val action = async { repository.sendRequest(5) }
        gate.entered.await()
        services.optedIn.value = false
        runCurrent()
        services.optedIn.value = true
        runCurrent()
        repository.assertDisabled()

        api.beforeResponse = {}
        repository.refresh()
        gate.open()

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), action.await())
        assertEquals(listOf(1), repository.friendIsus())
        assertEquals(listOf(2) to emptyList<Int>(), repository.requestIsus())
        assertNull(repository.cachedProfile(5))
    }

    @Test
    fun clearingASessionInvalidatesPendingReadsRefreshAndRelationshipActions() = runTest {
        val api = populatedApi()
        val repository = SocialRepositoryImpl(services(enabled = true), api.users, api.friendships, backgroundScope, noDemo(), testAppDispatchers())
        repository.populateCaches()
        val gate = ResponseGate(expectedCalls = 7)
        api.beforeResponse = { gate.await() }
        val profile = async { repository.profile(5) }
        val friends = async { repository.userFriends(1) }
        val action = async { repository.cancelRequest(5) }
        val refresh = launch { repository.refresh() }
        gate.entered.await()

        repository.clearSessionData()
        gate.open()

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), profile.await())
        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), friends.await())
        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), action.await())
        refresh.join()
        repository.assertCachesEmpty()
        assertEquals(LoadState.Loading, repository.observeFriends().first())
        assertEquals(LoadState.Loading, repository.observeRequests().first())
        assertNull(repository.observeCurrentUser().first())
        assertNull(repository.currentFriends)
    }

    @Test
    fun initialEnabledObservationDoesNotInvalidateAMatchingSuspendedGateRead() = runTest {
        val services = services(enabled = true)
        val gate = CompletableDeferred<Unit>()
        services.beforeAnswer = { gate.await() }
        val api = populatedApi()
        val repository = SocialRepositoryImpl(services, api.users, api.friendships, backgroundScope, noDemo(), testAppDispatchers())
        val request = async(start = CoroutineStart.UNDISPATCHED) { repository.profile(5) }
        runCurrent()
        assertEquals(0, api.calls)

        gate.complete(Unit)

        assertTrue(request.await() is AppResult.Success)
        assertNotNull(repository.cachedProfile(5))
        assertEquals(1, api.calls)
    }

    @Test
    fun aSuspendedEnabledReadCannotStartARequestAfterDisconnectAndReconnect() = runTest {
        val services = services(enabled = true)
        val api = populatedApi()
        val repository = SocialRepositoryImpl(services, api.users, api.friendships, backgroundScope, noDemo(), testAppDispatchers())
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        services.beforeAnswer = { gate.await() }
        val request = async(start = CoroutineStart.UNDISPATCHED) { repository.profile(5) }

        services.optedIn.value = false
        runCurrent()
        services.optedIn.value = true
        runCurrent()
        gate.complete(Unit)

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), request.await())
        assertEquals(0, api.calls)
        repository.assertDisabled()
    }

    @Test
    fun aSuspendedDisabledReadCannotClearFreshDataAfterReconnect() = runTest {
        val services = services(enabled = false)
        val api = populatedApi()
        val repository = SocialRepositoryImpl(services, api.users, api.friendships, backgroundScope, noDemo(), testAppDispatchers())
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        services.beforeAnswer = { gate.await() }
        val request = async(start = CoroutineStart.UNDISPATCHED) { repository.profile(5) }
        services.optedIn.value = true
        runCurrent()
        services.beforeAnswer = null
        repository.populateCaches()

        gate.complete(Unit)

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), request.await())
        repository.assertCachesAvailable()
        assertEquals(listOf(1), repository.friendIsus())
        assertEquals(listOf(2) to emptyList<Int>(), repository.requestIsus())
        assertEquals(9, repository.observeCurrentUser().first()?.isu)
    }

    @Test
    fun clearingASessionInvalidatesASuspendedOptInReadBeforeItReachesTheBackend() = runTest {
        val services = services(enabled = true)
        val api = populatedApi()
        val repository = SocialRepositoryImpl(services, api.users, api.friendships, backgroundScope, noDemo(), testAppDispatchers())
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        services.beforeAnswer = { gate.await() }
        val request = async(start = CoroutineStart.UNDISPATCHED) { repository.profile(5) }

        repository.clearSessionData()
        gate.complete(Unit)

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), request.await())
        assertEquals(0, api.calls)
        repository.assertCachesEmpty()
    }

    private fun populatedApi() = FakeSocialApi().apply {
        friends = listOf(profile(1, ClientRelationshipState.FRIENDS))
        incoming = listOf(profile(2, ClientRelationshipState.INCOMING))
        userFriends = listOf(profile(7, ClientRelationshipState.NONE))
        me = user(9)
        actionResult = { isu -> profile(isu, ClientRelationshipState.NONE) }
    }

    private suspend fun SocialRepositoryImpl.populateCaches() {
        refresh()
        profile(5)
        userFriends(1)
    }

    private fun SocialRepositoryImpl.assertCachesAvailable() {
        assertNotNull(cachedProfile(1))
        assertNotNull(cachedProfile(2))
        assertNotNull(cachedProfile(5))
        assertNotNull(cachedUserFriends(1))
    }

    private fun SocialRepositoryImpl.assertCachesEmpty() {
        assertNull(cachedProfile(1))
        assertNull(cachedProfile(2))
        assertNull(cachedProfile(5))
        assertNull(cachedUserFriends(1))
    }

    private suspend fun SocialRepositoryImpl.assertDisabled() {
        assertCachesEmpty()
        assertEquals(LoadState.Disabled, observeFriends().first())
        assertEquals(LoadState.Disabled, observeRequests().first())
        assertNull(observeCurrentUser().first())
        assertNull(currentFriends)
    }

    private suspend fun SocialRepositoryImpl.friendIsus(): List<Int> =
        (observeFriends().first() as LoadState.Content).value.map(UserProfile::isu)

    private suspend fun SocialRepositoryImpl.requestIsus(): Pair<List<Int>, List<Int>> {
        val requests = (observeRequests().first() as LoadState.Content<FriendRequests>).value
        return requests.incoming.map(UserProfile::isu) to requests.outgoing.map(UserProfile::isu)
    }

    private fun services(enabled: Boolean) = FakeBackendGate(enabled)


    /**
     * Holds every answer of the fake network until [open]; [entered] completes once [expectedCalls] requests wait.
     * Everything runs on the test scheduler, so a held request suspends instead of blocking a thread.
     */
    private class ResponseGate(private val expectedCalls: Int = 1) {
        val entered = CompletableDeferred<Unit>()
        private var arrived = 0
        private val release = CompletableDeferred<Unit>()

        suspend fun await() {
            arrived += 1
            if (arrived == expectedCalls) entered.complete(Unit)
            release.await()
        }

        fun open() {
            release.complete(Unit)
        }
    }

    private fun user(isu: Int) = UserData(
        isu = isu,
        name = "Пользователь $isu",
        pictureUrl = null,
        groups = emptyList(),
        capabilities = UserCapabilities(canViewSchedule = true, canViewSport = true, canViewFriends = false)
    )

    private fun profile(isu: Int, relationship: ClientRelationshipState) =
        ClientUserProfile(user(isu), relationship)

    /** Core 2.0's users and friends areas in memory; a failure is thrown as the client would throw it. */
    private class FakeSocialApi {
        var friends: List<ClientUserProfile> = emptyList()
        var userFriends: List<ClientUserProfile> = emptyList()
        var userFriendsFailure: Exception? = null
        val friendOwners = mutableListOf<Int>()
        var incoming: List<ClientUserProfile> = emptyList()
        var outgoing: List<ClientUserProfile> = emptyList()
        var me: UserData? = null
        var meFailure: Exception? = null
        var outgoingFailure: Exception? = null
        var actionFailure: Exception? = null
        var actionResult: (Int) -> ClientUserProfile = { error("no action result") }
        var lookupResult: (List<Int>) -> List<ClientUserProfile> = { emptyList() }
        val actions = mutableListOf<String>()
        val lookups = mutableListOf<List<Int>>()
        var calls = 0
            private set
        var beforeResponse: suspend () -> Unit = {}

        private suspend fun <T> answer(produce: () -> T): T {
            calls += 1
            val response = produce()
            beforeResponse()
            return response
        }

        private suspend fun action(name: String, isu: Int): ClientUserProfile = answer {
            actions += "$name:$isu"
            actionFailure?.let { throw it } ?: actionResult(isu)
        }

        val users: UsersApi = object : UsersApi {
            override suspend fun userProfile(isu: Int) = answer { actionResult(isu) }

            override suspend fun userFriends(isu: Int) = answer {
                friendOwners += isu
                userFriendsFailure?.let { throw it } ?: userFriends
            }

            override suspend fun lookupUsers(request: UserLookupRequest) = answer {
                lookups += request.isus
                UserLookupResponse(lookupResult(request.isus))
            }

            override suspend fun myUserData() = answer {
                meFailure?.let { throw it } ?: me ?: throw BackendException.Contract(IllegalStateException("no data"))
            }

            override suspend fun myPrivacySettings(): UserPrivacySettings = unexpected("myPrivacySettings")

            override suspend fun updateMyPrivacySettings(settings: UserPrivacySettings): UserPrivacySettings =
                unexpected("updateMyPrivacySettings")

            override suspend fun updateIdTokenData(request: IdTokenRequest): Unit = unexpected("updateIdTokenData")

            override suspend fun webLoginPreview(code: String): WebLoginPreview = unexpected("webLoginPreview")

            override suspend fun approveWebLogin(challengeId: Uuid): Unit = unexpected("approveWebLogin")
        }

        val friendships: FriendsApi = object : FriendsApi {
            override suspend fun sendFriendRequest(isu: Int) = action("sendFriendRequest", isu)

            override suspend fun acceptFriendRequest(isu: Int) = action("acceptFriendRequest", isu)

            override suspend fun rejectFriendRequest(isu: Int) = action("rejectFriendRequest", isu)

            override suspend fun cancelFriendRequest(isu: Int) = action("cancelFriendRequest", isu)

            override suspend fun removeFriend(isu: Int) = action("removeFriend", isu)

            override suspend fun friends() = answer { friends }

            override suspend fun incomingFriendRequests() = answer { incoming }

            override suspend fun outgoingFriendRequests() = answer { outgoingFailure?.let { throw it } ?: outgoing }
        }

        private fun unexpected(name: String): Nothing = error("Unexpected UsersApi call: $name")
    }
}
