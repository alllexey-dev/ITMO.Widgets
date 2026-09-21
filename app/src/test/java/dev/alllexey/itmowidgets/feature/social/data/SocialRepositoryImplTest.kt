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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialRepositoryImplTest {

    @Test
    fun `refresh loads friends requests and own profile`() = runTest {
        val api = FakeApi().apply {
            friends = listOf(profile(1, CoreRelationshipState.FRIENDS))
            incoming = listOf(profile(2, CoreRelationshipState.INCOMING))
            outgoing = listOf(profile(3, CoreRelationshipState.OUTGOING))
            me = user(9)
        }
        val repository = SocialRepositoryImpl(services(enabled = true), api.instance)

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
        val repository = SocialRepositoryImpl(services(enabled = true), api.instance)
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
        val repository = SocialRepositoryImpl(services(enabled = false), api.instance)

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
        val repository = SocialRepositoryImpl(services(enabled = true), api.instance)

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
        val repository = SocialRepositoryImpl(services(enabled = true), api.instance)
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
        val repository = SocialRepositoryImpl(services(enabled = true), api.instance)
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
        val repository = SocialRepositoryImpl(services(enabled = true), api.instance)
        repository.refresh()

        assertEquals(AppResult.Failure(AppError.Network), repository.rejectRequest(2))

        assertEquals(listOf(2) to emptyList<Int>(), repository.requestIsus())
    }

    @Test
    fun `lookup deduplicates chunks by fifty and keeps request order`() = runTest {
        val api = FakeApi().apply {
            lookupResult = { isus -> isus.map { profile(it, CoreRelationshipState.NONE) } }
        }
        val repository = SocialRepositoryImpl(services(enabled = true), api.instance)
        val isus = (1..60).toList() + 1

        val result = repository.lookup(isus)

        assertEquals((1..60).toList(), (result as AppResult.Success).value.map(UserProfile::isu))
        assertEquals(listOf(50, 10), api.lookups.map { it.size })
        assertEquals(AppResult.Success(emptyList<UserProfile>()), repository.lookup(emptyList()))
    }

    @Test
    fun `clearing session data forgets everything loaded`() = runTest {
        val api = FakeApi().apply { friends = listOf(profile(1, CoreRelationshipState.FRIENDS)); me = user(9) }
        val repository = SocialRepositoryImpl(services(enabled = true), api.instance)
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
        val repository = SocialRepositoryImpl(services(enabled = true), api.instance)
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

    private suspend fun SocialRepositoryImpl.friendIsus(): List<Int> =
        (observeFriends().first() as SocialState.Content).value.map(UserProfile::isu)

    private suspend fun SocialRepositoryImpl.requestIsus(): Pair<List<Int>, List<Int>> {
        val requests = (observeRequests().first() as SocialState.Content<FriendRequests>).value
        return requests.incoming.map(UserProfile::isu) to requests.outgoing.map(UserProfile::isu)
    }

    private fun services(enabled: Boolean) = object : CustomServicesRepository {
        override fun observeEnabled(): Flow<Boolean> = flowOf(enabled)
        override suspend fun isEnabled(): Boolean = enabled
        override suspend fun setEnabled(enabled: Boolean) = error("not used")
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
        var calls = 0
            private set

        val instance: ItmoWidgetsApi = Proxy.newProxyInstance(
            ItmoWidgetsApi::class.java.classLoader,
            arrayOf(ItmoWidgetsApi::class.java)
        ) { proxy, method, arguments ->
            when (method.name) {
                "equals" -> return@newProxyInstance proxy === arguments?.firstOrNull()
                "hashCode" -> return@newProxyInstance System.identityHashCode(proxy)
                "toString" -> return@newProxyInstance "FakeApi"
            }
            calls += 1
            when (method.name) {
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
        } as ItmoWidgetsApi
    }
}
