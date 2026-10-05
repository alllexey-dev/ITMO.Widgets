package dev.alllexey.itmowidgets.client.common

import dev.alllexey.itmowidgets.client.json.BackendJson
import dev.alllexey.itmowidgets.client.support.Fixtures
import dev.alllexey.itmowidgets.client.support.assertJsonEquals
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Round trip and strictness of the wire types shared by several areas (07 item 12, SP-02 cases 3, 5, 7). */
class CommonTypesTest {

    private val capabilities = """{"canViewSchedule":true,"canViewSport":false,"canViewFriends":true}"""

    private val userData = UserData(
        isu = 100001,
        name = "Тестовый Студент",
        pictureUrl = null,
        groups = listOf(GroupData(name = "T3100", course = 2, facultyShortName = "ФТФ")),
        capabilities = UserCapabilities(canViewSchedule = true, canViewSport = false, canViewFriends = true),
    )

    private fun user(capabilities: String?) =
        """{"isu":100001,"name":"Тестовый Студент","groups":[]""" +
            (capabilities?.let { ""","capabilities":$it""" } ?: "") + "}"

    @Test
    fun userDataRoundTrips() {
        val fixture = Fixtures.read("support/user-data.json")

        val decoded = BackendJson.decodeFromString(UserData.serializer(), fixture)

        assertEquals(userData, decoded)
        assertJsonEquals(fixture, BackendJson.encodeToString(UserData.serializer(), decoded))
    }

    @Test
    fun pictureUrlMayBeAbsentAndIsOmittedWhenNull() {
        val decoded = BackendJson.decodeFromString(UserData.serializer(), user(capabilities))

        assertNull(decoded.pictureUrl)
        assertEquals(
            """{"isu":100001,"name":"Тестовый Студент","groups":[],"capabilities":$capabilities}""",
            BackendJson.encodeToString(UserData.serializer(), decoded),
        )
    }

    @Test
    fun missingOrNullCapabilitiesFail() {
        for (body in listOf(user(null), user("null"))) {
            assertFailsWith<SerializationException>(body) { BackendJson.decodeFromString(UserData.serializer(), body) }
        }
    }

    @Test
    fun everyCapabilityIsRequiredAndNeverNull() {
        for (field in listOf("canViewSchedule", "canViewSport", "canViewFriends")) {
            val others = listOf("canViewSchedule", "canViewSport", "canViewFriends").filter { it != field }
                .joinToString(",") { """"$it":true""" }
            for (body in listOf("{$others}", """{$others,"$field":null}""")) {
                assertFailsWith<SerializationException>(body) {
                    BackendJson.decodeFromString(UserCapabilities.serializer(), body)
                }
                assertFailsWith<SerializationException>(body) {
                    BackendJson.decodeFromString(UserData.serializer(), user(body))
                }
            }
        }
    }

    @Test
    fun quotedCapabilityBooleanIsAccepted() {
        // 07 Q4 (b), SP-02 case 3: Jackson never quotes booleans; kotlinx reads them and the owner accepted that.
        val body = """{"canViewSchedule":"true","canViewSport":false,"canViewFriends":false}"""

        assertEquals(
            UserCapabilities(canViewSchedule = true, canViewSport = false, canViewFriends = false),
            BackendJson.decodeFromString(UserCapabilities.serializer(), body),
        )
    }

    @Test
    fun groupFieldsAreRequired() {
        // SP-02 case 5: a null name fails instead of a latent null in a non-null field.
        for (body in listOf(
            """{"name":null,"course":2,"facultyShortName":"ФТФ"}""",
            """{"course":2,"facultyShortName":"ФТФ"}""",
            """{"name":"T3100","facultyShortName":"ФТФ"}""",
        )) {
            assertFailsWith<SerializationException>(body) { BackendJson.decodeFromString(GroupData.serializer(), body) }
        }
    }

    @Test
    fun userProfileRoundTripsEveryRelationship() {
        for (state in RelationshipState.entries) {
            val body = """{"user":${user(capabilities)},"relationship":"${state.name}"}"""

            val decoded = BackendJson.decodeFromString(UserProfile.serializer(), body)

            assertEquals(state, decoded.relationship)
            assertJsonEquals(body, BackendJson.encodeToString(UserProfile.serializer(), decoded))
        }
    }

    @Test
    fun relationshipIsStrictAndRequired() {
        for (relationship in listOf("\"BLOCKED_BY_ADMIN\"", "\"friends\"", "null", "3")) {
            val body = """{"user":${user(capabilities)},"relationship":$relationship}"""
            assertFailsWith<SerializationException>(body) {
                BackendJson.decodeFromString(UserProfile.serializer(), body)
            }
        }
        val withoutUser = """{"relationship":"NONE"}"""
        assertFailsWith<SerializationException> { BackendJson.decodeFromString(UserProfile.serializer(), withoutUser) }
    }

    @Test
    fun voteRequestIsMinusOneZeroOrOne() {
        for (value in -1..1) {
            assertEquals(
                """{"value":$value}""",
                BackendJson.encodeToString(ResourceVoteRequest.serializer(), ResourceVoteRequest(value)),
            )
        }
        for (value in listOf(-2, 2)) {
            assertFailsWith<IllegalArgumentException> { ResourceVoteRequest(value) }
        }
    }

    @Test
    fun reportRequestOmitsANullComment() {
        val encode = { request: ModerationReportRequest ->
            BackendJson.encodeToString(ModerationReportRequest.serializer(), request)
        }

        assertEquals("""{"reason":"WRONG_TEACHER"}""", encode(ModerationReportRequest(ReportReason.WRONG_TEACHER)))
        assertEquals(
            """{"reason":"OTHER","comment":"synthetic"}""",
            encode(ModerationReportRequest(ReportReason.OTHER, "synthetic")),
        )
    }

    @Test
    fun reportReasonIsStrict() {
        for (reason in ReportReason.entries) {
            val text = "\"${reason.name}\""
            assertEquals(reason, BackendJson.decodeFromString(ReportReason.serializer(), text))
            assertEquals(text, BackendJson.encodeToString(ReportReason.serializer(), reason))
        }
        assertFailsWith<SerializationException> {
            BackendJson.decodeFromString(ReportReason.serializer(), "\"DUPLICATE\"")
        }
    }
}
