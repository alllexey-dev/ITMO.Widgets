package dev.alllexey.itmowidgets.feature.social.data

import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.social.FriendRequests
import dev.alllexey.itmowidgets.core.testing.FakeBackendGate
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.feature.social.data.SocialRemoteFixtures.FRIEND_ISU
import dev.alllexey.itmowidgets.feature.social.data.SocialRemoteFixtures.INCOMING_ISU
import dev.alllexey.itmowidgets.feature.social.data.SocialRemoteFixtures.ME_ISU
import dev.alllexey.itmowidgets.feature.social.data.SocialRemoteFixtures.OUTGOING_ISU
import dev.alllexey.itmowidgets.feature.social.data.SocialRemoteFixtures.SECOND_FRIEND_ISU
import dev.alllexey.itmowidgets.testkit.bodyText
import dev.alllexey.itmowidgets.testkit.respondJson
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** `SocialRepositoryImpl` over the real Core 2.0 client and a MockEngine: routes, mapping, errors and the gates. */
class SocialRepositoryRemoteTest {

    @Test
    fun refreshReadsTheOwnDataFriendsAndBothRequestListsWithTheStoredToken() = runTest {
        val harness = harness { request ->
            when (request.url.encodedPath) {
                "/api/users/me/data" -> respondJson(SocialRemoteFixtures.MY_USER_DATA)
                "/api/friends" -> respondJson(SocialRemoteFixtures.FRIENDS)
                "/api/friends/requests/incoming" -> respondJson(SocialRemoteFixtures.INCOMING)
                "/api/friends/requests/outgoing" -> respondJson(SocialRemoteFixtures.OUTGOING)
                else -> throw AssertionError("Unexpected ${request.url.encodedPath}")
            }
        }
        val repository = repository(harness, backgroundScope)

        repository.refresh()

        assertEquals(
            listOf("/api/friends", "/api/friends/requests/incoming", "/api/friends/requests/outgoing", "/api/users/me/data"),
            harness.requests.map { it.url.encodedPath }.sorted()
        )
        assertTrue(harness.requests.all { it.method == HttpMethod.Get })
        assertTrue(harness.requests.all { it.headers[HttpHeaders.Authorization] == "Bearer stored-access" })
        assertEquals(
            UserSummary(
                ME_ISU, "Студент Тестовый", "https://example.org/avatars/100001.jpg",
                listOf(UserGroup("К3240", 2, "ФИТИП")), UserSharing(sport = true, schedule = true, friends = true)
            ),
            repository.observeCurrentUser().first()
        )
        val friends = (repository.observeFriends().first() as LoadState.Content).value
        assertEquals(listOf(FRIEND_ISU, SECOND_FRIEND_ISU), friends.map(UserProfile::isu))
        assertTrue(friends.all { it.relationship == RelationshipState.FRIENDS })
        assertEquals(UserSharing(sport = false, schedule = true, friends = true), friends[1].user.sharing)
        assertEquals(null, friends[1].user.pictureUrl)
        val requests = (repository.observeRequests().first() as LoadState.Content<FriendRequests>).value
        assertEquals(listOf(INCOMING_ISU), requests.incoming.map(UserProfile::isu))
        assertEquals(null, requests.incoming.single().user.pictureUrl)
        assertEquals(UserSharing(sport = false, schedule = false, friends = false), requests.incoming.single().user.sharing)
        assertEquals(listOf(OUTGOING_ISU), requests.outgoing.map(UserProfile::isu))
        assertEquals(RelationshipState.OUTGOING, requests.outgoing.single().relationship)
    }

    @Test
    fun aProfileAndAnotherUsersFriendsAreReadByISUAndCached() = runTest {
        val harness = harness { request ->
            when (request.url.encodedPath) {
                "/api/users/$FRIEND_ISU" -> respondJson(SocialRemoteFixtures.profile(FRIEND_ISU, "FRIENDS"))
                "/api/users/$FRIEND_ISU/friends" -> respondJson(SocialRemoteFixtures.USER_FRIENDS)
                else -> throw AssertionError("Unexpected ${request.url.encodedPath}")
            }
        }
        val repository = repository(harness, backgroundScope)

        val profile = (repository.profile(FRIEND_ISU) as AppResult.Success).value
        val friends = (repository.userFriends(FRIEND_ISU) as AppResult.Success).value

        assertEquals(RelationshipState.FRIENDS, profile.relationship)
        assertEquals("Пользователь $FRIEND_ISU", profile.user.name)
        assertEquals(UserSharing(sport = false, schedule = true, friends = true), profile.user.sharing)
        assertEquals(profile, repository.cachedProfile(FRIEND_ISU))
        assertEquals(listOf(INCOMING_ISU to RelationshipState.INCOMING, SECOND_FRIEND_ISU to RelationshipState.NONE),
            friends.map { it.isu to it.relationship })
        assertEquals(friends, repository.cachedUserFriends(FRIEND_ISU))
        assertTrue(harness.requests.all { it.method == HttpMethod.Get })
    }

    @Test
    fun lookupPostsDistinctISUsInChunksOfFiftyAndKeepsTheAnswerOrder() = runTest {
        val harness = harness { request ->
            respondJson(SocialRemoteFixtures.lookup(request.lookupIsus()))
        }
        val repository = repository(harness, backgroundScope)

        val result = repository.lookup((1..60).toList() + 1)

        assertEquals((1..60).toList(), (result as AppResult.Success).value.map(UserProfile::isu))
        assertEquals(listOf((1..50).toList(), (51..60).toList()), harness.requests.map { it.lookupIsus() })
        assertTrue(harness.requests.all { it.method == HttpMethod.Post && it.url.encodedPath == "/api/users/lookup" })
    }

    @Test
    fun eachRelationshipActionCallsItsRouteAndFoldsTheAnswerIntoTheLists() = runTest {
        val answers = mapOf(
            "POST /api/friends/$OUTGOING_ISU/cancel" to "NONE",
            "POST /api/friends/$INCOMING_ISU/accept" to "FRIENDS",
            "POST /api/friends/$ME_ISU/request" to "OUTGOING",
            "POST /api/friends/$ME_ISU/reject" to "NONE",
            "DELETE /api/friends/$FRIEND_ISU" to "NONE",
        )
        val harness = harness { request ->
            when (val key = "${request.method.value} ${request.url.encodedPath}") {
                "GET /api/users/me/data" -> respondJson(SocialRemoteFixtures.MY_USER_DATA)
                "GET /api/friends" -> respondJson(SocialRemoteFixtures.FRIENDS)
                "GET /api/friends/requests/incoming" -> respondJson(SocialRemoteFixtures.INCOMING)
                "GET /api/friends/requests/outgoing" -> respondJson(SocialRemoteFixtures.OUTGOING)
                in answers -> respondJson(SocialRemoteFixtures.profile(request.url.encodedPath.split('/')[3].toInt(), answers.getValue(key)))
                else -> throw AssertionError("Unexpected $key")
            }
        }
        val repository = repository(harness, backgroundScope)
        repository.refresh()

        assertTrue(repository.cancelRequest(OUTGOING_ISU) is AppResult.Success)
        assertTrue(repository.acceptRequest(INCOMING_ISU) is AppResult.Success)
        assertEquals(RelationshipState.OUTGOING, (repository.sendRequest(ME_ISU) as AppResult.Success).value.relationship)
        assertTrue(repository.rejectRequest(ME_ISU) is AppResult.Success)
        assertTrue(repository.removeFriend(FRIEND_ISU) is AppResult.Success)

        assertEquals(answers.keys.toList(), harness.requests.drop(4).map { "${it.method.value} ${it.url.encodedPath}" })
        assertEquals(listOf(INCOMING_ISU, SECOND_FRIEND_ISU), repository.friendIsus())
        assertEquals(emptyList<Int>() to emptyList<Int>(), repository.requestIsus())
        assertEquals(RelationshipState.NONE, repository.cachedProfile(FRIEND_ISU)?.relationship)
    }

    @Test
    fun backendErrorsKeepTheReleasedSemantics() = runTest {
        val cases = listOf(
            Answer(HttpStatusCode.Forbidden, SocialRemoteFixtures.error("permission_denied")) to AppError.Forbidden,
            Answer(HttpStatusCode.Forbidden, SocialRemoteFixtures.error("restricted")) to AppError.Restricted,
            Answer(HttpStatusCode.Unauthorized, SocialRemoteFixtures.error("unauthorized")) to AppError.Unauthorized,
            Answer(HttpStatusCode.NotFound, SocialRemoteFixtures.error("not_found")) to AppError.NotFound,
        )
        for ((answer, expected) in cases) {
            val harness = harness { respondJson(answer.body, answer.status) }
            val repository = repository(harness, backgroundScope)

            assertEquals(AppResult.Failure(expected), repository.profile(FRIEND_ISU), "$answer profile")
            assertEquals(AppResult.Failure(expected), repository.userFriends(FRIEND_ISU), "$answer friends")
            assertEquals(AppResult.Failure(expected), repository.lookup(listOf(FRIEND_ISU)), "$answer lookup")
            assertEquals(AppResult.Failure(expected), repository.sendRequest(FRIEND_ISU), "$answer action")
            repository.refresh()
            assertEquals(LoadState.Error(expected), repository.observeFriends().first(), "$answer list")
            assertEquals(LoadState.Error(expected), repository.observeRequests().first(), "$answer requests")
            assertEquals(null, repository.cachedProfile(FRIEND_ISU))
        }
    }

    @Test
    fun anUnknownRelationshipFailsTheAnswerInsteadOfBecomingNone() = runTest {
        val harness = harness { respondJson(SocialRemoteFixtures.UNKNOWN_RELATIONSHIP) }
        val repository = repository(harness, backgroundScope)

        val profile = repository.profile(FRIEND_ISU)
        val action = repository.acceptRequest(FRIEND_ISU)

        assertTrue(profile is AppResult.Failure && profile.error is AppError.Unknown)
        assertTrue(action is AppResult.Failure && action.error is AppError.Unknown)
        assertEquals(null, repository.cachedProfile(FRIEND_ISU))
    }

    @Test
    fun noRequestLeavesTheDeviceWithTheOptInOff() = runTest {
        val harness = harness { request -> throw AssertionError("The opted-out session asked ${request.url.encodedPath}") }
        val repository = repository(harness, backgroundScope, gate = FakeBackendGate(optedIn = false))

        repository.refresh()

        assertEquals(LoadState.Disabled, repository.observeFriends().first())
        for (result in repository.everyRemoteCall()) {
            assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), result)
        }
        assertEquals(emptyList<HttpRequestData>(), harness.requests)
    }

    @Test
    fun theDemoSessionSendsNoRequest() = runTest {
        val demo = FakeDemoMode(active = true)
        val harness = harness { request -> throw AssertionError("The demo session asked ${request.url.encodedPath}") }
        val repository = repository(harness, backgroundScope, demo = demo, gate = FakeBackendGate(optedIn = true, demo))

        repository.refresh()
        repository.everyRemoteCall()

        assertTrue(repository.observeFriends().first() is LoadState.Content)
        assertEquals(emptyList<HttpRequestData>(), harness.requests)
    }

    private data class Answer(val status: HttpStatusCode, val body: String)

    private fun harness(
        backend: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = BackendHarness(backend)

    private fun TestScope.repository(
        harness: BackendHarness,
        scope: CoroutineScope,
        demo: FakeDemoMode = FakeDemoMode(active = false),
        gate: FakeBackendGate = FakeBackendGate(optedIn = true, demo),
    ) = SocialRepositoryImpl(gate, harness.client.users, harness.client.friends, scope, demo, testAppDispatchers())

    private suspend fun SocialRepositoryImpl.everyRemoteCall(): List<AppResult<*>> = listOf(
        profile(FRIEND_ISU),
        userFriends(FRIEND_ISU),
        lookup(listOf(FRIEND_ISU)),
        sendRequest(FRIEND_ISU),
        acceptRequest(FRIEND_ISU),
        rejectRequest(FRIEND_ISU),
        cancelRequest(FRIEND_ISU),
        removeFriend(FRIEND_ISU),
    )

    private suspend fun SocialRepositoryImpl.friendIsus(): List<Int> =
        (observeFriends().first() as LoadState.Content).value.map(UserProfile::isu)

    private suspend fun SocialRepositoryImpl.requestIsus(): Pair<List<Int>, List<Int>> {
        val requests = (observeRequests().first() as LoadState.Content<FriendRequests>).value
        return requests.incoming.map(UserProfile::isu) to requests.outgoing.map(UserProfile::isu)
    }

    private fun HttpRequestData.lookupIsus(): List<Int> =
        Json.parseToJsonElement(bodyText()).jsonObject.getValue("isus").jsonArray.map { it.jsonPrimitive.int }
}
