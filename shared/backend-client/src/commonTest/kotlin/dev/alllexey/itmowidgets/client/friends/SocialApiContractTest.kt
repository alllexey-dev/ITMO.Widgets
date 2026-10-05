package dev.alllexey.itmowidgets.client.friends

import dev.alllexey.itmowidgets.client.BackendClient
import dev.alllexey.itmowidgets.client.common.RelationshipState
import dev.alllexey.itmowidgets.client.common.UserCapabilities
import dev.alllexey.itmowidgets.client.common.UserProfile
import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.client.json.BackendJson
import dev.alllexey.itmowidgets.client.support.MockBackend
import dev.alllexey.itmowidgets.client.support.assertJsonEquals
import dev.alllexey.itmowidgets.client.support.errorEnvelope
import dev.alllexey.itmowidgets.client.support.json
import dev.alllexey.itmowidgets.client.support.record
import dev.alllexey.itmowidgets.client.support.runSuspend
import dev.alllexey.itmowidgets.client.users.SyntheticUsers
import dev.alllexey.itmowidgets.client.users.SyntheticUsers.answering
import dev.alllexey.itmowidgets.client.users.SyntheticUsers.profile
import dev.alllexey.itmowidgets.client.users.SyntheticUsers.profileJson
import dev.alllexey.itmowidgets.client.users.UserLookupRequest
import dev.alllexey.itmowidgets.client.users.UserLookupResponse
import dev.alllexey.itmowidgets.client.users.UsersRouteCases
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Port of Core 1.7.0 `social/SocialApiContractTest`: friendships, public profiles and lookup. A duplicate key keeps
 * its last value by 07 Q4 (b); the 1.x Retrofit reflection check runs over the recorded route cases.
 */
class SocialApiContractTest {

    private fun decodeProfile(text: String) = BackendJson.decodeFromString(UserProfile.serializer(), text)

    private fun encodeProfile(value: UserProfile) = BackendJson.encodeToString(UserProfile.serializer(), value)

    private fun assertBodiless(request: HttpRequestData, method: HttpMethod, path: String) {
        assertEquals(method, request.method, path)
        assertEquals(path, request.url.encodedPath)
        assertIs<OutgoingContent.NoContent>(request.body, path)
        assertEquals(emptyList(), request.url.parameters.names().toList(), path)
        assertNull(request.headers[HttpHeaders.Authorization], path)
    }

    @Test
    fun allRelationshipStatesAndCapabilityCombinationsRoundTripWithExactWireFields() {
        val booleans = listOf(false, true)
        for (relationship in RelationshipState.entries) {
            for (schedule in booleans) for (sport in booleans) for (friends in booleans) {
                val value = profile(relationship, UserCapabilities(schedule, sport, friends))
                val encoded = BackendJson.parseToJsonElement(encodeProfile(value)).jsonObject
                assertEquals(setOf("user", "relationship"), encoded.keys)
                assertJsonEquals("\"${relationship.name}\"", encoded.getValue("relationship").toString())
                assertEquals(setOf("isu", "name", "groups", "capabilities"), encoded.getValue("user").jsonObject.keys)
                assertEquals(value, decodeProfile(encoded.toString()))
            }
        }
        val request = UserLookupRequest(listOf(SyntheticUsers.ISU, SyntheticUsers.OTHER_ISU))
        val requestText = BackendJson.encodeToString(UserLookupRequest.serializer(), request)
        assertEquals("""{"isus":[123456,456789]}""", requestText)
        assertEquals(request, BackendJson.decodeFromString(UserLookupRequest.serializer(), requestText))
        val response = UserLookupResponse(listOf(profile(RelationshipState.FRIENDS)))
        val responseText = BackendJson.encodeToString(UserLookupResponse.serializer(), response)
        assertEquals(response, BackendJson.decodeFromString(UserLookupResponse.serializer(), responseText))
    }

    @Test
    fun unknownNullAndMalformedRelationshipsAreRejectedInsteadOfBecomingNone() {
        val user = SyntheticUsers.userJson()
        for (wire in listOf("null", "42", "true", "{}", "[]", "\"friends\"", "\"UNKNOWN\"")) {
            assertFailsWith<SerializationException>(wire) {
                BackendJson.decodeFromString(RelationshipState.serializer(), wire)
            }
            assertFailsWith<SerializationException>(wire) { decodeProfile("""{"user":$user,"relationship":$wire}""") }
        }
        val incomplete = listOf(
            "{}",
            """{"user":$user}""",
            """{"relationship":"NONE"}""",
            """{"user":null,"relationship":"NONE"}""",
            """{"user":{"isu":123456,"name":"Synthetic user","groups":[]},"relationship":"FRIENDS"}""",
        )
        for (wire in incomplete) {
            assertFailsWith<SerializationException>(wire) { decodeProfile(wire) }
        }
        val duplicate = """{"user":$user,"relationship":"NONE","relationship":"FRIENDS"}"""
        assertEquals(RelationshipState.FRIENDS, decodeProfile(duplicate).relationship)
    }

    @Test
    fun everyRelationshipActionUsesTheTargetIsuPathNoBodyAndReadsAFreshProfile() = runSuspend {
        val actions: List<Triple<HttpMethod, String, suspend BackendClient.(Int) -> UserProfile>> = listOf(
            Triple(HttpMethod.Post, "/request") { friends.sendFriendRequest(it) },
            Triple(HttpMethod.Post, "/accept") { friends.acceptFriendRequest(it) },
            Triple(HttpMethod.Post, "/reject") { friends.rejectFriendRequest(it) },
            Triple(HttpMethod.Post, "/cancel") { friends.cancelFriendRequest(it) },
            Triple(HttpMethod.Delete, "") { friends.removeFriend(it) },
        )
        for ((method, suffix, call) in actions) {
            val backend = answering(profileJson("INCOMING"))

            val result = backend.client.call(SyntheticUsers.ISU)

            assertEquals(profile(RelationshipState.INCOMING), result)
            assertBodiless(backend.lastRequest, method, "/api/friends/123456$suffix")
        }
    }

    @Test
    fun everyFriendshipListUsesTheProfileWrapperAndAnExactGetRoute() = runSuspend {
        val reads: List<Pair<String, suspend BackendClient.() -> List<UserProfile>>> = listOf(
            "" to { friends.friends() },
            "/requests/incoming" to { friends.incomingFriendRequests() },
            "/requests/outgoing" to { friends.outgoingFriendRequests() },
        )
        for ((suffix, call) in reads) {
            val backend = answering("[${profileJson("FRIENDS")}]")

            assertEquals(listOf(profile(RelationshipState.FRIENDS)), backend.client.call())
            assertBodiless(backend.lastRequest, HttpMethod.Get, "/api/friends$suffix")
        }
    }

    @Test
    fun publicProfileUsesAnIsuPathAndNotTheOwnDataRoute() = runSuspend {
        val backend = answering(profileJson("OUTGOING"))

        assertEquals(profile(RelationshipState.OUTGOING), backend.client.users.userProfile(SyntheticUsers.ISU))
        assertBodiless(backend.lastRequest, HttpMethod.Get, "/api/users/123456")
    }

    @Test
    fun lookupSendsTheExactIsuListAsJsonAndReturnsRegisteredProfilesOnly() = runSuspend {
        val backend = answering("""{"users":[${profileJson("NONE")}]}""")

        val result = backend.client.users.lookupUsers(UserLookupRequest(listOf(123456, 999999, 123456)))

        assertEquals(UserLookupResponse(listOf(profile(RelationshipState.NONE))), result)
        val request = backend.lastRequest
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/users/lookup", request.url.encodedPath)
        assertJsonEquals("""{"isus":[123456,999999,123456]}""", assertIs<TextContent>(request.body).text)
        assertEquals(emptyList(), request.url.parameters.names().toList())
        assertNull(request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun emptyLookupStaysTyped() = runSuspend {
        val backend = answering("""{"users":[]}""")

        assertEquals(emptyList(), backend.client.users.lookupUsers(UserLookupRequest(emptyList())).users)
        assertEquals("""{"isus":[]}""", assertIs<TextContent>(backend.lastRequest.body).text)
    }

    @Test
    fun incompleteProfileFailsWhileAnErrorKeepsItsCode() = runSuspend {
        val incomplete = answering("""{"user":${SyntheticUsers.userJson()}}""")
        assertFailsWith<BackendException.Contract> { incomplete.client.users.userProfile(SyntheticUsers.ISU) }

        val missing = MockBackend { json(HttpStatusCode.NotFound, errorEnvelope("not_found")) }
        val error = assertFailsWith<BackendException.NotFound> { missing.client.users.userProfile(SyntheticUsers.ISU) }
        assertEquals("not_found", error.code)
    }

    @Test
    fun obsoleteFriendFunctionsAndRoutesAreAbsent() = runSuspend {
        val cases = UsersRouteCases.all + FriendsRouteCases.all
        assertFalse(cases.any { it.name == "addFriend" || it.name == "myFriends" })

        val paths = cases.map { it.record().path }
        for (route in listOf("/api/friends/add", "/api/friends/remove", "/api/friends/get")) {
            assertFalse(route in paths, route)
        }
    }

    @Test
    fun targetFriendsUseTheExactIsuPathAndKeepViewerRelativeRelationships() = runSuspend {
        val backend = answering("[${profileJson("NONE")}]")

        val result = backend.client.users.userFriends(SyntheticUsers.OTHER_ISU)

        assertEquals(listOf(profile(RelationshipState.NONE)), result)
        assertBodiless(backend.lastRequest, HttpMethod.Get, "/api/users/456789/friends")
    }
}
