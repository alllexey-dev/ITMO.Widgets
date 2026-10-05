package dev.alllexey.itmowidgets.client.users

import dev.alllexey.itmowidgets.client.common.RelationshipState
import dev.alllexey.itmowidgets.client.common.UserCapabilities
import dev.alllexey.itmowidgets.client.common.UserData
import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.client.friends.FriendsRouteCases
import dev.alllexey.itmowidgets.client.json.BackendJson
import dev.alllexey.itmowidgets.client.support.MockBackend
import dev.alllexey.itmowidgets.client.support.assertJsonEquals
import dev.alllexey.itmowidgets.client.support.errorEnvelope
import dev.alllexey.itmowidgets.client.support.json
import dev.alllexey.itmowidgets.client.support.ok
import dev.alllexey.itmowidgets.client.support.record
import dev.alllexey.itmowidgets.client.support.runSuspend
import dev.alllexey.itmowidgets.client.users.SyntheticUsers.answering
import dev.alllexey.itmowidgets.client.users.SyntheticUsers.capabilitiesJson
import dev.alllexey.itmowidgets.client.users.SyntheticUsers.userJson
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Port of Core 1.7.0 `PrivacyApiContractTest` (07 CORE-K1, item 12, Verification #21). 1.x Gson rejected duplicate
 * keys and quoted booleans; Core 2.0 accepts both by the owner's 07 Q4 (b), so those cases now assert the accepted
 * behaviour. The 1.x sport-bookings case belongs to the sport area (CO-04).
 */
class PrivacyApiContractTest {

    private val audiences = listOf("scheduleVisibility", "sportVisibility", "friendsVisibility")

    private fun settings(text: String) = BackendJson.decodeFromString(UserPrivacySettings.serializer(), text)

    private fun encode(value: UserPrivacySettings) = BackendJson.encodeToString(UserPrivacySettings.serializer(), value)

    private fun capabilities(text: String) = BackendJson.decodeFromString(UserCapabilities.serializer(), text)

    private fun userData(text: String) = BackendJson.decodeFromString(UserData.serializer(), text)

    private fun encodedUser(value: UserData): JsonObject =
        BackendJson.parseToJsonElement(BackendJson.encodeToString(UserData.serializer(), value)).jsonObject

    /** A settings body with [field] set to the raw JSON [value] and the other audiences valid. */
    private fun settingsWith(field: String, value: String?): String =
        audiences.mapNotNull { name ->
            when {
                name != field -> """"$name":"FRIENDS""""
                value == null -> null
                else -> """"$name":$value"""
            }
        }.joinToString(",", "{", "}")

    @Test
    fun allPrivacyAudienceCombinationsRoundTripWithExactWireNames() {
        for (schedule in SharingVisibility.entries) for (sport in SharingVisibility.entries) {
            val value = UserPrivacySettings(schedule, sport, SharingVisibility.ALL)
            val text = encode(value)

            assertJsonEquals(
                """{"scheduleVisibility":"${schedule.name}","sportVisibility":"${sport.name}",""" +
                    """"friendsVisibility":"ALL"}""",
                text,
            )
            assertEquals(value, settings(text))
        }
    }

    @Test
    fun unknownNullAndNonStringVisibilityValuesFailForEveryAudience() {
        for (field in audiences) {
            for (value in listOf("\"UNKNOWN\"", "\"friends\"", "null", "42", "true", "{}", "[]")) {
                val text = settingsWith(field, value)
                assertFailsWith<SerializationException>("$field must not become a default: $value") { settings(text) }
            }
        }
    }

    @Test
    fun absentPrivacyFieldsAreRejectedInsteadOfFabricatingDefaults() {
        val partial = listOf("{}", """{"scheduleVisibility":"FRIENDS"}""", """{"sportVisibility":"NOBODY"}""") +
            audiences.map { settingsWith(it, null) }
        for (text in partial) {
            assertFailsWith<SerializationException>("Every audience is required: $text") { settings(text) }
        }
    }

    @Test
    fun standaloneVisibilityAcceptsKnownValuesAndRejectsUnsupportedOnes() {
        for (visibility in SharingVisibility.entries) {
            assertEquals(
                visibility,
                BackendJson.decodeFromString(SharingVisibility.serializer(), "\"${visibility.name}\""),
            )
        }
        for (text in listOf("\"PUBLIC\"", "\"all\"", "null", "0", "false", "{}", "[]")) {
            assertFailsWith<SerializationException>(text) {
                BackendJson.decodeFromString(SharingVisibility.serializer(), text)
            }
        }
    }

    @Test
    fun unknownAdditionalResponseFieldsDoNotBreakValidSettings() {
        val text = """{
            "scheduleVisibility":"ALL",
            "sportVisibility":"NOBODY","friendsVisibility":"ALL",
            "futureField":{"nested":[true,42,"new"]}
        }"""

        assertEquals(
            UserPrivacySettings(SharingVisibility.ALL, SharingVisibility.NOBODY, SharingVisibility.ALL),
            settings(text),
        )
    }

    @Test
    fun viewerCapabilitiesAndUserDataRoundTripWithoutOwnerPrivacySettings() {
        val value = UserCapabilities(canViewSchedule = true, canViewSport = false, canViewFriends = false)
        val text = """{"canViewSchedule":true,"canViewSport":false,"canViewFriends":false}"""

        assertJsonEquals(text, BackendJson.encodeToString(UserCapabilities.serializer(), value))
        assertEquals(value, capabilities(text))

        val user = SyntheticUsers.identity.copy(capabilities = value)
        val encoded = encodedUser(user)
        assertEquals(setOf("isu", "name", "groups", "capabilities"), encoded.keys)
        assertJsonEquals(text, encoded.getValue("capabilities").toString())
        assertEquals(user, userData(encoded.toString()))
    }

    @Test
    fun privacyGetUsesTheDedicatedAudienceRoute() = runSuspend {
        val backend =
            answering("""{"scheduleVisibility":"FRIENDS","sportVisibility":"ALL","friendsVisibility":"ALL"}""")

        val result = backend.client.users.myPrivacySettings()

        assertEquals(
            UserPrivacySettings(SharingVisibility.FRIENDS, SharingVisibility.ALL, SharingVisibility.ALL),
            result,
        )
        val request = backend.lastRequest
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/api/users/me/privacy", request.url.encodedPath)
        assertEquals(0L, request.body.contentLength ?: 0L)
        assertNull(request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun privacyPutSendsAllAudiencesAsAFullReplacementAndReadsTheAnswer() = runSuspend {
        val value = UserPrivacySettings(SharingVisibility.ALL, SharingVisibility.NOBODY, SharingVisibility.ALL)
        val backend = answering(encode(value))

        val result = backend.client.users.updateMyPrivacySettings(value)

        assertEquals(value, result)
        val request = backend.lastRequest
        assertEquals(HttpMethod.Put, request.method)
        assertEquals("/api/users/me/privacy", request.url.encodedPath)
        assertJsonEquals(encode(value), assertIs<TextContent>(request.body).text)
        assertNull(request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun allViewerCapabilityCombinationsRoundTripWithExactBooleansAndNoOwnerSettings() {
        for (schedule in listOf(false, true)) for (sport in listOf(false, true)) for (friends in listOf(false, true)) {
            val value = UserCapabilities(schedule, sport, friends)
            val text = capabilitiesJson(schedule, sport, friends)
            assertJsonEquals(text, BackendJson.encodeToString(UserCapabilities.serializer(), value))
            assertEquals(value, capabilities(text))

            val user = SyntheticUsers.identity.copy(
                pictureUrl = "https://example.invalid/synthetic-avatar",
                groups = listOf(SyntheticUsers.group),
                capabilities = value,
            )
            val encoded = encodedUser(user)
            assertEquals(setOf("isu", "name", "pictureUrl", "groups", "capabilities"), encoded.keys)
            assertJsonEquals(text, encoded.getValue("capabilities").toString())
            for (ownerSetting in listOf("settings", "scheduleVisibility", "sportVisibility", "friendsVisibility")) {
                assertFalse(encoded.containsKey(ownerSetting), ownerSetting)
            }
            assertEquals(user, userData(encoded.toString()))
        }
    }

    @Test
    fun nullAndNonBooleanPermissionsAreRejectedStandaloneAndInProfiles() {
        val fields = listOf("canViewSchedule", "canViewSport", "canViewFriends")
        for (field in fields) {
            val others = fields.filter { it != field }.joinToString(",") { """"$it":false""" }
            for (invalid in listOf("null", "0", "1", "\"yes\"", "{}", "[]")) {
                val text = """{"$field":$invalid,$others}"""
                assertFailsWith<SerializationException>(text) { capabilities(text) }
                assertFailsWith<SerializationException>(text) { userData(userJson(text)) }
            }
            for ((quoted, expected) in listOf("\"true\"" to true, "\"false\"" to false)) {
                // 07 Q4 (b): Jackson never quotes booleans; kotlinx reads quoted ones and the owner accepted that.
                val text = """{"$field":$quoted,$others}"""
                val decoded = capabilities(text)
                val flags = listOf(decoded.canViewSchedule, decoded.canViewSport, decoded.canViewFriends)
                assertEquals(expected, flags[fields.indexOf(field)], text)
            }
        }
    }

    @Test
    fun missingPermissionFieldsAreRejectedAndNeverSuppliedAsDefaults() {
        val partial = listOf(
            "{}",
            """{"canViewSchedule":false}""",
            """{"canViewSport":true}""",
            """{"canViewSchedule":true,"canViewSport":true}""",
        )
        for (text in partial) {
            assertFailsWith<SerializationException>(text) { capabilities(text) }
            assertFailsWith<SerializationException>(text) { userData(userJson(text)) }
        }
    }

    @Test
    fun capabilitiesMustBeAnObjectRatherThanNullOrAScalar() {
        for (text in listOf("null", "true", "false", "0", "[]", "\"capabilities\"")) {
            assertFailsWith<SerializationException>(text) { capabilities(text) }
            assertFailsWith<SerializationException>(text) { userData(userJson(text)) }
        }
    }

    @Test
    fun duplicatePermissionFieldsKeepTheLastValue() {
        // 1.x rejected duplicates; 07 Q4 (b) accepts them with the last value winning, as Jackson writes none.
        val text = """{"canViewSchedule":false,"canViewSport":false,"canViewFriends":false,"canViewSchedule":true}"""

        assertEquals(
            UserCapabilities(canViewSchedule = true, canViewSport = false, canViewFriends = false),
            capabilities(text),
        )
        assertEquals(true, userData(userJson(text)).capabilities.canViewSchedule)
    }

    @Test
    fun unknownResponseMetadataIsIgnoredWithoutBecomingAPermissionOrOwnerSetting() {
        val text = """{
            "canViewSchedule":false,"canViewSport":true,"canViewFriends":false,
            "futureField":{"nested":[true,42,"new"]},
            "sportVisibility":"ALL","scheduleVisibility":"ALL"
        }"""
        val expected = UserCapabilities(canViewSchedule = false, canViewSport = true, canViewFriends = false)

        assertEquals(expected, capabilities(text))
        val user = userData(userJson(text))
        assertEquals(expected, user.capabilities)
        assertJsonEquals(SyntheticUsers.CAPABILITIES, encodedUser(user).getValue("capabilities").toString())
    }

    @Test
    fun missingCapabilitiesIncludingTheLegacySettingsOnlyProfileFailClosed() {
        val noCapabilities = """{"isu":123456,"name":"Synthetic user","pictureUrl":null,"groups":[]}"""
        val oldProfile = """{"isu":123456,"name":"Synthetic user","pictureUrl":null,"groups":[],""" +
            """"settings":{"sportSharing":true,"scheduleSharing":true}}"""
        for (text in listOf(noCapabilities, oldProfile)) {
            val failure = assertFailsWith<SerializationException> { userData(text) }
            assertFalse(failure.message.orEmpty().contains("Synthetic user"))
            assertFalse(failure.message.orEmpty().contains("sportSharing"))
        }
    }

    @Test
    fun clientHasNoLegacyPrivacyRouteAndBothPrivacyCallsUseTheAudienceRoute() = runSuspend {
        // The 1.x reflection check on Retrofit annotations, now over the recorded requests of every case.
        val requests = (UsersRouteCases.all + FriendsRouteCases.all).map { it.record() }

        assertFalse(requests.any { it.path == "/api/users/me/settings" })
        assertEquals(
            setOf(HttpMethod.Get, HttpMethod.Put),
            requests.filter { it.path == "/api/users/me/privacy" }.map { it.method }.toSet(),
        )
        assertEquals(2, requests.count { it.path == "/api/users/me/privacy" })
    }

    @Test
    fun ownUserDataConsumesCapabilitiesFromTheProfileRoute() = runSuspend {
        val everything = UserCapabilities(canViewSchedule = true, canViewSport = true, canViewFriends = true)
        val backend = answering(userJson(capabilitiesJson(schedule = true, sport = true, friends = true)))

        val result = backend.client.users.myUserData()

        assertEquals(SyntheticUsers.identity.copy(capabilities = everything), result)
        val request = backend.lastRequest
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/api/users/me/data", request.url.encodedPath)
        assertNull(request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun friendsRouteConsumesEveryPermissionCombinationWithoutOwnerAudiences() = runSuspend {
        val combinations = listOf(false, true).flatMap { schedule ->
            listOf(false, true).flatMap { sport ->
                listOf(false, true).map { friends -> Triple(schedule, sport, friends) }
            }
        }
        val body = combinations.joinToString(",", "[", "]") { (schedule, sport, friends) ->
            SyntheticUsers.profileJson("FRIENDS", capabilitiesJson(schedule, sport, friends))
        }
        val backend = answering(body)

        val result = backend.client.friends.friends()

        assertEquals(
            combinations.map { (schedule, sport, friends) ->
                SyntheticUsers.profile(RelationshipState.FRIENDS, UserCapabilities(schedule, sport, friends))
            },
            result,
        )
        assertEquals("/api/friends", backend.lastRequest.url.encodedPath)
        assertEquals(emptyList(), backend.lastRequest.url.parameters.names().toList())
    }

    @Test
    fun oldProfileWireShapeIsRejectedThroughTheRealClient() = runSuspend {
        val oldProfile = """{"isu":123456,"name":"Synthetic user","pictureUrl":null,"groups":[],""" +
            """"settings":{"sportSharing":true,"scheduleSharing":true}}"""
        val backend = answering(oldProfile)

        val error = assertFailsWith<BackendException.Contract> { backend.client.users.myUserData() }

        assertIs<SerializationException>(error.cause)
        assertEquals("/api/users/me/data", backend.lastRequest.url.encodedPath)
    }

    @Test
    fun failedProfileAnswerWithNullDataNeverBecomesAUser() = runSuspend {
        val denied = MockBackend { json(HttpStatusCode.Forbidden, errorEnvelope("permission_denied")) }
        val error = assertFailsWith<BackendException.Forbidden> { denied.client.users.myUserData() }
        assertEquals("permission_denied", error.code)

        val unsuccessful = MockBackend {
            ok("""{"success":false,"data":null,"error":{"message":"synthetic message","code":"permission_denied"}}""")
        }
        assertFailsWith<BackendException.Contract> { unsuccessful.client.users.myUserData() }
    }

    @Test
    fun friendsAudienceAndCapabilityRoundTripForEveryValue() {
        for (schedule in SharingVisibility.entries) for (sport in SharingVisibility.entries) {
            for (friends in SharingVisibility.entries) {
                val value = UserPrivacySettings(schedule, sport, friends)
                val encoded = BackendJson.parseToJsonElement(encode(value)).jsonObject
                assertEquals(audiences.toSet(), encoded.keys)
                assertJsonEquals("\"${friends.name}\"", encoded.getValue("friendsVisibility").toString())
                assertEquals(value, settings(encoded.toString()))
            }
        }
        for (schedule in listOf(false, true)) for (sport in listOf(false, true)) for (friends in listOf(false, true)) {
            val value = UserCapabilities(schedule, sport, friends)
            assertEquals(value, capabilities(BackendJson.encodeToString(UserCapabilities.serializer(), value)))
        }
    }

    @Test
    fun friendsFieldsFailClosedWhenMissingNullOrMalformed() {
        for (field in listOf("", ""","friendsVisibility":null""", ""","friendsVisibility":"UNKNOWN"""",
            ""","friendsVisibility":true""")) {
            assertFailsWith<SerializationException>(field) {
                settings("""{"scheduleVisibility":"ALL","sportVisibility":"ALL"$field}""")
            }
        }
        for (field in listOf("", ""","canViewFriends":null""", ""","canViewFriends":1""")) {
            assertFailsWith<SerializationException>(field) {
                capabilities("""{"canViewSchedule":true,"canViewSport":true$field}""")
            }
        }
        // 07 Q4 (b): a duplicate keeps the last value instead of failing.
        val duplicate = """{"scheduleVisibility":"ALL","sportVisibility":"ALL",""" +
            """"friendsVisibility":"ALL","friendsVisibility":"NOBODY"}"""
        assertEquals(SharingVisibility.NOBODY, settings(duplicate).friendsVisibility)
    }
}
