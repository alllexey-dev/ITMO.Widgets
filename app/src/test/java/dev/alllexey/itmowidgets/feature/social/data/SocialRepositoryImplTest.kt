package dev.alllexey.itmowidgets.feature.social.data

import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.model.ApiResponse
import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.model.UserCapabilities
import dev.alllexey.itmowidgets.core.model.UserData
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.model.social.UserLookupRequest
import dev.alllexey.itmowidgets.core.model.social.UserLookupResponse
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.social.FriendRequests
import dev.alllexey.itmowidgets.core.social.SocialState
import dev.alllexey.itmowidgets.core.model.social.RelationshipState as CoreRelationshipState
import dev.alllexey.itmowidgets.core.model.social.UserProfile as CoreUserProfile
import java.io.IOException
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SocialRepositoryImplTest {

    @Test
    fun `refresh loads friends requests and own profile`() = runTest {
        val api = FakeApi().apply {
            friends = listOf(profile(1, CoreRelationshipState.FRIENDS))
            incoming = listOf(profile(2, CoreRelationshipState.INCOMING))
            outgoing = listOf(profile(3, CoreRelationshipState.OUTGOING))
            me = user(9)
        }
        val repository = SocialRepositoryImpl(services(enabled = true), api.instance, backgroundScope)

        repository.refresh()

        assertEquals(listOf(1), repository.friendIsus())
        assertEquals(listOf(2) to listOf(3), repository.requestIsus())
        assertEquals(9, repository.observeCurrentUser().first()?.isu)
        assertEquals(listOf(1), repository.currentFriends?.map(UserProfile::isu))
    }

    @Test
    fun `people seen in lists profiles and actions are cached until the session is cleared`() = runTest {
        val api = FakeApi().apply {
            friends = listOf(profile(1, CoreRelationshipState.FRIENDS))
            incoming = listOf(profile(2, CoreRelationshipState.INCOMING))
            userFriends = listOf(profile(7, CoreRelationshipState.NONE))
            actionResult = { isu -> profile(isu, CoreRelationshipState.OUTGOING) }
        }
        val repository = SocialRepositoryImpl(services(enabled = true), api.instance, backgroundScope)
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

        api.actionResult = { isu -> profile(isu, CoreRelationshipState.NONE) }
        repository.cancelRequest(5)
        assertEquals(RelationshipState.NONE, repository.cachedProfile(5)?.relationship)

        repository.clearSessionData()
        assertNull(repository.cachedProfile(1))
        assertNull(repository.cachedProfile(5))
        assertNull(repository.cachedUserFriends(1))
    }

    @Test
    fun `disabled services skip the backend entirely`() = runTest {
        val api = FakeApi()
        val repository = SocialRepositoryImpl(services(enabled = false), api.instance, backgroundScope)

        repository.refresh()

        assertEquals(SocialState.Disabled, repository.observeFriends().first())
        assertEquals(SocialState.Disabled, repository.observeRequests().first())
        assertEquals(0, api.calls)
        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), repository.userFriends(1))
        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), repository.profile(1))
        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), repository.sendRequest(1))
    }

    @Test
    fun `own profile failure does not hide the lists and a list failure is typed`() = runTest {
        val api = FakeApi().apply {
            friends = listOf(profile(1, CoreRelationshipState.FRIENDS))
            meFailure = IOException("offline")
            outgoingFailure = IOException("offline")
        }
        val repository = SocialRepositoryImpl(services(enabled = true), api.instance, backgroundScope)

        repository.refresh()

        assertEquals(listOf(1), repository.friendIsus())
        assertNull(repository.observeCurrentUser().first())
        assertEquals(SocialState.Error(AppError.Network), repository.observeRequests().first())
    }

    @Test
    fun `accepting a request moves the person from incoming to friends without a refresh`() = runTest {
        val api = FakeApi().apply {
            incoming = listOf(profile(2, CoreRelationshipState.INCOMING), profile(4, CoreRelationshipState.INCOMING))
            actionResult = { isu -> profile(isu, CoreRelationshipState.FRIENDS) }
        }
        val repository = SocialRepositoryImpl(services(enabled = true), api.instance, backgroundScope)
        repository.refresh()

        val result = repository.acceptRequest(2)

        assertTrue(result is AppResult.Success)
        assertEquals(listOf(2), repository.friendIsus())
        assertEquals(listOf(4) to emptyList<Int>(), repository.requestIsus())
        assertEquals(listOf("acceptFriendRequest:2"), api.actions)
    }

    @Test
    fun `sending cancelling and removing update the cached lists`() = runTest {
        val api = FakeApi().apply {
            friends = listOf(profile(1, CoreRelationshipState.FRIENDS))
        }
        val repository = SocialRepositoryImpl(services(enabled = true), api.instance, backgroundScope)
        repository.refresh()

        api.actionResult = { isu -> profile(isu, CoreRelationshipState.OUTGOING) }
        repository.sendRequest(5)
        assertEquals(emptyList<Int>() to listOf(5), repository.requestIsus())

        api.actionResult = { isu -> profile(isu, CoreRelationshipState.NONE) }
        repository.cancelRequest(5)
        assertEquals(emptyList<Int>() to emptyList<Int>(), repository.requestIsus())

        repository.removeFriend(1)
        assertEquals(emptyList<Int>(), repository.friendIsus())
        assertEquals(listOf("sendFriendRequest:5", "cancelFriendRequest:5", "removeFriend:1"), api.actions)
    }

    @Test
    fun `a failed action leaves the lists untouched`() = runTest {
        val api = FakeApi().apply {
            incoming = listOf(profile(2, CoreRelationshipState.INCOMING))
            actionFailure = IOException("offline")
        }
        val repository = SocialRepositoryImpl(services(enabled = true), api.instance, backgroundScope)
        repository.refresh()

        assertEquals(AppResult.Failure(AppError.Network), repository.rejectRequest(2))

        assertEquals(listOf(2) to emptyList<Int>(), repository.requestIsus())
    }

    @Test
    fun `lookup deduplicates chunks by fifty and keeps request order`() = runTest {
        val api = FakeApi().apply {
            lookupResult = { isus -> isus.map { profile(it, CoreRelationshipState.NONE) } }
        }
        val repository = SocialRepositoryImpl(services(enabled = true), api.instance, backgroundScope)
        val isus = (1..60).toList() + 1

        val result = repository.lookup(isus)

        assertEquals((1..60).toList(), (result as AppResult.Success).value.map(UserProfile::isu))
        assertEquals(listOf(50, 10), api.lookups.map { it.size })
        assertEquals(AppResult.Success(emptyList<UserProfile>()), repository.lookup(emptyList()))
    }

    @Test
    fun `clearing session data forgets everything loaded`() = runTest {
        val api = FakeApi().apply { friends = listOf(profile(1, CoreRelationshipState.FRIENDS)); me = user(9) }
        val repository = SocialRepositoryImpl(services(enabled = true), api.instance, backgroundScope)
        repository.refresh()

        repository.clearSessionData()

        assertEquals(SocialState.Loading, repository.observeFriends().first())
        assertEquals(SocialState.Loading, repository.observeRequests().first())
        assertNull(repository.observeCurrentUser().first())
        assertNull(repository.currentFriends)
    }

    @Test
    fun `target friends retain viewer capabilities and never overwrite own friends cache`() = runTest {
        val api = FakeApi().apply { friends = listOf(profile(1, CoreRelationshipState.FRIENDS)) }
        val repository = SocialRepositoryImpl(services(enabled = true), api.instance, backgroundScope)
        repository.refresh()
        api.userFriends = listOf(profile(2, CoreRelationshipState.NONE).copy(user = user(2).copy(
            capabilities = UserCapabilities(false, false, true)
        )))
        val result = repository.userFriends(99) as AppResult.Success
        assertEquals(listOf(99), api.friendOwners)
        assertEquals(RelationshipState.NONE, result.value.single().relationship)
        assertTrue(result.value.single().user.sharing.friends)
        assertEquals(false, result.value.single().user.sharing.schedule)
        assertEquals(listOf(1), repository.friendIsus())
        api.userFriendsFailure = IOException("offline")
        assertEquals(AppResult.Failure(AppError.Network), repository.userFriends(99))
    }

    @Test
    fun `opting out clears every backend cache and reconnecting does not restore it`() = runTest {
        val services = services(enabled = true)
        val api = populatedApi()
        val repository = SocialRepositoryImpl(services, api.instance, backgroundScope)
        repository.populateCaches()
        runCurrent()
        repository.assertCachesAvailable()

        services.enabled.value = false
        runCurrent()

        repository.assertDisabled()
        services.enabled.value = true
        runCurrent()
        repository.assertDisabled()
    }

    @Test
    fun `refresh clears backend caches even when the opt-out collector has not run`() = runTest {
        val services = services(enabled = true)
        val api = populatedApi()
        val repository = SocialRepositoryImpl(services, api.instance, backgroundScope)
        repository.populateCaches()
        runCurrent()
        val calls = api.calls

        services.enabled.value = false
        repository.refresh()
        services.enabled.value = true
        runCurrent()
        repository.lookup(listOf(99))

        repository.assertDisabled()
        assertEquals(calls + 1, api.calls)
    }

    @Test
    fun `a gated request clears backend caches even when the opt-out collector has not run`() = runTest {
        val services = services(enabled = true)
        val api = populatedApi()
        val repository = SocialRepositoryImpl(services, api.instance, backgroundScope)
        repository.populateCaches()
        runCurrent()
        val calls = api.calls

        services.enabled.value = false
        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), repository.profile(7))
        services.enabled.value = true
        runCurrent()
        repository.lookup(listOf(99))

        repository.assertDisabled()
        assertEquals(calls + 1, api.calls)
    }

    @Test
    fun `pending profile and target friends cannot restore caches after opting out`() = runTest {
        val services = services(enabled = true)
        val api = populatedApi()
        val repository = SocialRepositoryImpl(services, api.instance, backgroundScope)
        repository.populateCaches()
        val gate = ResponseGate(expectedCalls = 2)
        api.beforeResponse = { gate.await() }
        val profile = async { repository.profile(5) }
        val friends = async { repository.userFriends(1) }
        gate.entered.await()

        services.enabled.value = false
        runCurrent()
        gate.open()

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), profile.await())
        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), friends.await())
        repository.assertDisabled()
    }

    @Test
    fun `pre-disconnect responses cannot replace fresh profile and friends after reconnect`() = runTest {
        val services = services(enabled = true)
        val api = populatedApi()
        val repository = SocialRepositoryImpl(services, api.instance, backgroundScope)
        repository.populateCaches()
        val gate = ResponseGate(expectedCalls = 2)
        api.beforeResponse = { gate.await() }
        val oldProfile = async { repository.profile(5) }
        val oldFriends = async { repository.userFriends(1) }
        gate.entered.await()
        services.enabled.value = false
        runCurrent()
        services.enabled.value = true
        runCurrent()
        repository.assertDisabled()

        api.beforeResponse = {}
        api.actionResult = { isu -> profile(isu, CoreRelationshipState.FRIENDS) }
        api.userFriends = listOf(profile(8, CoreRelationshipState.NONE))
        assertTrue(repository.profile(5) is AppResult.Success)
        assertTrue(repository.userFriends(1) is AppResult.Success)
        gate.open()

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), oldProfile.await())
        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), oldFriends.await())
        assertEquals(RelationshipState.FRIENDS, repository.cachedProfile(5)?.relationship)
        assertEquals(listOf(8), repository.cachedUserFriends(1)?.map(UserProfile::isu))
        assertEquals(SocialState.Disabled, repository.observeFriends().first())
        assertEquals(SocialState.Disabled, repository.observeRequests().first())
    }

    @Test
    fun `pending refresh cannot restore lists or own profile after opting out`() = runTest {
        val services = services(enabled = true)
        val api = populatedApi()
        val repository = SocialRepositoryImpl(services, api.instance, backgroundScope)
        repository.populateCaches()
        val gate = ResponseGate(expectedCalls = 4)
        api.beforeResponse = { gate.await() }
        val refresh = launch { repository.refresh() }
        gate.entered.await()

        services.enabled.value = false
        runCurrent()
        gate.open()
        refresh.join()

        repository.assertDisabled()
    }

    @Test
    fun `pre-disconnect refresh cannot replace fresh lists or own profile after reconnect`() = runTest {
        val services = services(enabled = true)
        val api = populatedApi()
        val repository = SocialRepositoryImpl(services, api.instance, backgroundScope)
        repository.populateCaches()
        val gate = ResponseGate(expectedCalls = 4)
        api.beforeResponse = { gate.await() }
        val oldRefresh = launch { repository.refresh() }
        gate.entered.await()
        services.enabled.value = false
        runCurrent()
        services.enabled.value = true
        runCurrent()
        repository.assertDisabled()

        api.beforeResponse = {}
        api.friends = listOf(profile(3, CoreRelationshipState.FRIENDS))
        api.incoming = listOf(profile(4, CoreRelationshipState.INCOMING))
        api.me = user(10)
        repository.refresh()
        gate.open()
        oldRefresh.join()

        assertEquals(listOf(3), repository.friendIsus())
        assertEquals(listOf(4) to emptyList<Int>(), repository.requestIsus())
        assertEquals(10, repository.observeCurrentUser().first()?.isu)
    }

    @Test
    fun `pending relationship action cannot restore a profile after opting out`() = runTest {
        val services = services(enabled = true)
        val api = populatedApi()
        val repository = SocialRepositoryImpl(services, api.instance, backgroundScope)
        repository.populateCaches()
        api.actionResult = { isu -> profile(isu, CoreRelationshipState.OUTGOING) }
        val gate = ResponseGate()
        api.beforeResponse = { gate.await() }
        val action = async { repository.sendRequest(5) }
        gate.entered.await()

        services.enabled.value = false
        runCurrent()
        gate.open()

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), action.await())
        repository.assertDisabled()
    }

    @Test
    fun `pre-disconnect relationship action cannot mutate fresh lists after reconnect`() = runTest {
        val services = services(enabled = true)
        val api = populatedApi()
        val repository = SocialRepositoryImpl(services, api.instance, backgroundScope)
        repository.populateCaches()
        api.actionResult = { isu -> profile(isu, CoreRelationshipState.OUTGOING) }
        val gate = ResponseGate()
        api.beforeResponse = { gate.await() }
        val action = async { repository.sendRequest(5) }
        gate.entered.await()
        services.enabled.value = false
        runCurrent()
        services.enabled.value = true
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
    fun `clearing a session invalidates pending reads refresh and relationship actions`() = runTest {
        val api = populatedApi()
        val repository = SocialRepositoryImpl(services(enabled = true), api.instance, backgroundScope)
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
        assertEquals(SocialState.Loading, repository.observeFriends().first())
        assertEquals(SocialState.Loading, repository.observeRequests().first())
        assertNull(repository.observeCurrentUser().first())
        assertNull(repository.currentFriends)
    }

    @Test
    fun `initial enabled observation does not invalidate a matching suspended gate read`() = runTest {
        val services = services(enabled = true)
        val gate = CompletableDeferred<Unit>()
        services.readGate = { gate.await() }
        val api = populatedApi()
        val repository = SocialRepositoryImpl(services, api.instance, backgroundScope)
        val request = async(start = CoroutineStart.UNDISPATCHED) { repository.profile(5) }
        runCurrent()
        assertEquals(0, api.calls)

        gate.complete(Unit)

        assertTrue(request.await() is AppResult.Success)
        assertNotNull(repository.cachedProfile(5))
        assertEquals(1, api.calls)
    }

    @Test
    fun `a suspended enabled read cannot start a request after disconnect and reconnect`() = runTest {
        val services = services(enabled = true)
        val api = populatedApi()
        val repository = SocialRepositoryImpl(services, api.instance, backgroundScope)
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        services.readGate = { gate.await() }
        val request = async(start = CoroutineStart.UNDISPATCHED) { repository.profile(5) }

        services.enabled.value = false
        runCurrent()
        services.enabled.value = true
        runCurrent()
        gate.complete(Unit)

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), request.await())
        assertEquals(0, api.calls)
        repository.assertDisabled()
    }

    @Test
    fun `a suspended disabled read cannot clear fresh data after reconnect`() = runTest {
        val services = services(enabled = false)
        val api = populatedApi()
        val repository = SocialRepositoryImpl(services, api.instance, backgroundScope)
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        services.readGate = { gate.await() }
        val request = async(start = CoroutineStart.UNDISPATCHED) { repository.profile(5) }
        services.enabled.value = true
        runCurrent()
        services.readGate = null
        repository.populateCaches()

        gate.complete(Unit)

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), request.await())
        repository.assertCachesAvailable()
        assertEquals(listOf(1), repository.friendIsus())
        assertEquals(listOf(2) to emptyList<Int>(), repository.requestIsus())
        assertEquals(9, repository.observeCurrentUser().first()?.isu)
    }

    @Test
    fun `clearing a session invalidates a suspended opt-in read before it reaches the backend`() = runTest {
        val services = services(enabled = true)
        val api = populatedApi()
        val repository = SocialRepositoryImpl(services, api.instance, backgroundScope)
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        services.readGate = { gate.await() }
        val request = async(start = CoroutineStart.UNDISPATCHED) { repository.profile(5) }

        repository.clearSessionData()
        gate.complete(Unit)

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), request.await())
        assertEquals(0, api.calls)
        repository.assertCachesEmpty()
    }

    private fun populatedApi() = FakeApi().apply {
        friends = listOf(profile(1, CoreRelationshipState.FRIENDS))
        incoming = listOf(profile(2, CoreRelationshipState.INCOMING))
        userFriends = listOf(profile(7, CoreRelationshipState.NONE))
        me = user(9)
        actionResult = { isu -> profile(isu, CoreRelationshipState.NONE) }
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
        assertEquals(SocialState.Disabled, observeFriends().first())
        assertEquals(SocialState.Disabled, observeRequests().first())
        assertNull(observeCurrentUser().first())
        assertNull(currentFriends)
    }

    private suspend fun SocialRepositoryImpl.friendIsus(): List<Int> =
        (observeFriends().first() as SocialState.Content).value.map(UserProfile::isu)

    private suspend fun SocialRepositoryImpl.requestIsus(): Pair<List<Int>, List<Int>> {
        val requests = (observeRequests().first() as SocialState.Content<FriendRequests>).value
        return requests.incoming.map(UserProfile::isu) to requests.outgoing.map(UserProfile::isu)
    }

    private fun services(enabled: Boolean) = FakeServices(enabled)

    private class FakeServices(enabled: Boolean) : CustomServicesRepository {
        val enabled = MutableStateFlow(enabled)
        var readGate: (suspend () -> Unit)? = null

        override fun observeEnabled(): Flow<Boolean> = enabled
        override suspend fun isEnabled(): Boolean {
            val on = enabled.value
            readGate?.invoke()
            return on
        }
        override suspend fun setEnabled(enabled: Boolean) { this.enabled.value = enabled }
    }

    private class ResponseGate(private val expectedCalls: Int = 1) {
        val entered = CompletableDeferred<Unit>()
        private val arrived = AtomicInteger()
        private val release = CountDownLatch(1)

        fun await() {
            if (arrived.incrementAndGet() == expectedCalls) entered.complete(Unit)
            assertTrue("Synthetic response gate was not released", release.await(10, TimeUnit.SECONDS))
        }

        fun open() = release.countDown()
    }

    private fun user(isu: Int) = UserData(
        isu = isu,
        name = "Пользователь $isu",
        pictureUrl = null,
        groups = emptyList(),
        capabilities = UserCapabilities(canViewSchedule = true, canViewSport = true)
    )

    private fun profile(isu: Int, relationship: CoreRelationshipState) =
        CoreUserProfile(user(isu), relationship)

    private class FakeApi {
        var friends: List<CoreUserProfile> = emptyList()
        var userFriends: List<CoreUserProfile> = emptyList()
        var userFriendsFailure: Exception? = null
        val friendOwners = mutableListOf<Int>()
        var incoming: List<CoreUserProfile> = emptyList()
        var outgoing: List<CoreUserProfile> = emptyList()
        var me: UserData? = null
        var meFailure: Exception? = null
        var outgoingFailure: Exception? = null
        var actionFailure: Exception? = null
        var actionResult: (Int) -> CoreUserProfile = { error("no action result") }
        var lookupResult: (List<Int>) -> List<CoreUserProfile> = { emptyList() }
        val actions = mutableListOf<String>()
        val lookups = mutableListOf<List<Int>>()
        private val callCount = AtomicInteger()
        val calls: Int get() = callCount.get()
        var beforeResponse: () -> Unit = {}

        val instance: ItmoWidgetsApi = Proxy.newProxyInstance(
            ItmoWidgetsApi::class.java.classLoader,
            arrayOf(ItmoWidgetsApi::class.java)
        ) { proxy, method, arguments ->
            when (method.name) {
                "equals" -> return@newProxyInstance proxy === arguments?.firstOrNull()
                "hashCode" -> return@newProxyInstance System.identityHashCode(proxy)
                "toString" -> return@newProxyInstance "FakeApi"
            }
            callCount.incrementAndGet()
            val response = when (method.name) {
                "myUserData" -> meFailure?.let { throw it } ?: ApiResponse.success(me)
                "friends" -> ApiResponse.success(friends)
                "userFriends" -> {
                    friendOwners += arguments[0] as Int
                    userFriendsFailure?.let { throw it } ?: ApiResponse.success(userFriends)
                }
                "incomingFriendRequests" -> ApiResponse.success(incoming)
                "outgoingFriendRequests" -> outgoingFailure?.let { throw it } ?: ApiResponse.success(outgoing)
                "userProfile" -> ApiResponse.success(actionResult(arguments[0] as Int))
                "lookupUsers" -> {
                    val request = arguments[0] as UserLookupRequest
                    lookups += request.isus
                    ApiResponse.success(UserLookupResponse(lookupResult(request.isus)))
                }
                "sendFriendRequest", "acceptFriendRequest", "rejectFriendRequest",
                "cancelFriendRequest", "removeFriend" -> {
                    val isu = arguments[0] as Int
                    actions += "${method.name}:$isu"
                    actionFailure?.let { throw it } ?: ApiResponse.success(actionResult(isu))
                }
                else -> error("Unexpected ItmoWidgetsApi call: ${method.name}")
            }
            beforeResponse()
            response
        } as ItmoWidgetsApi
    }
}
