package dev.alllexey.itmowidgets.client.links

import dev.alllexey.itmowidgets.client.common.ModerationReportRequest
import dev.alllexey.itmowidgets.client.common.ReportReason
import dev.alllexey.itmowidgets.client.common.ReportReasonSerializer
import dev.alllexey.itmowidgets.client.common.ResourceVoteRequest
import dev.alllexey.itmowidgets.client.json.BackendJson
import dev.alllexey.itmowidgets.client.links.ResourceContractFixtures.decode
import dev.alllexey.itmowidgets.client.links.ResourceContractFixtures.jsonOf
import dev.alllexey.itmowidgets.client.links.ResourceContractFixtures.with
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Port of Core 1.x `resources/SubjectLinkContractTest`, one test per 1.x test with the same name. Not ported: the
 * moderation cases (`automatic decisions require a policy actor without a fictional moderator`, `link moderation
 * target decodes polymorphically under SUBJECT_RESOURCE and rejects unknown targets`) and the moderation types in
 * the other tests, since the moderation API is not mirrored (CO-02); 07 Q4 (b) drops the duplicate-key and
 * quoted-number cases.
 *
 * A malformed answer fails with a [SerializationException] or, for a model `init` invariant, an
 * [IllegalArgumentException]; both are [IllegalArgumentException]s, which the client turns into a contract failure.
 */
class SubjectLinkContractTest {

    private val fixtures = ResourceContractFixtures

    private fun <T> assertRoundTrips(serializer: KSerializer<T>, value: T) =
        assertEquals(value, BackendJson.decodeFromString(serializer, BackendJson.encodeToString(serializer, value)))

    private fun <T> assertDecodeFails(serializer: KSerializer<T>, json: JsonObject, message: String) {
        assertFailsWith<IllegalArgumentException>(message) { decode(serializer, json) }
    }

    private fun <T> assertTextDecodeFails(serializer: KSerializer<T>, text: String, message: String) {
        assertFailsWith<SerializationException>(message) { BackendJson.decodeFromString(serializer, text) }
    }

    @Test
    fun everyPublicLinkAndModerationDtoRoundTrips() {
        // Moderation DTOs are CO-02's; the restriction and the report body are the ones the app sends and reads.
        for (link in listOf(fixtures.link, fixtures.ownLink, fixtures.previousLink)) {
            assertRoundTrips(SubjectLink.serializer(), link)
        }
        assertRoundTrips(LinkAudience.serializer(), fixtures.audience)
        assertRoundTrips(SubjectLinksResponse.serializer(), fixtures.links)
        assertRoundTrips(
            SubjectLinksResponse.serializer(),
            fixtures.links.copy(pinnedId = null, mine = emptyList(), audiences = emptyList()),
        )
        assertRoundTrips(SaveSubjectLinkRequest.serializer(), fixtures.save)
        assertRoundTrips(SaveSubjectLinkRequest.serializer(), fixtures.privateSave.copy(title = "Очередь"))
        assertRoundTrips(PinSubjectLinkRequest.serializer(), PinSubjectLinkRequest("2026-1", fixtures.id))
        assertRoundTrips(PinSubjectLinkRequest.serializer(), PinSubjectLinkRequest("2026-1"))
        assertRoundTrips(ResourceVoteRequest.serializer(), ResourceVoteRequest(-1))
        assertRoundTrips(UserRestriction.serializer(), fixtures.restriction)
        assertRoundTrips(UserRestriction.serializer(), fixtures.restriction.copy(expiresAt = fixtures.now))
        assertRoundTrips(ModerationReportRequest.serializer(), ModerationReportRequest(ReportReason.OTHER, null))
    }

    @Test
    fun linkModelsUseTheExactWireFieldNames() {
        assertEquals(
            setOf(
                "id", "subjectId", "subjectName", "periodKey", "category", "url", "title", "visibility", "flowId",
                "audienceLabel", "status", "reviewNote", "score", "myVote", "isMine", "reportedByMe", "author",
                "updatedAt",
            ),
            jsonOf(SubjectLink.serializer(), fixtures.link.copy(reviewNote = "Проверено")).keys,
        )
        assertEquals(
            setOf("mine", "shared", "previous", "pinnedId", "audiences", "premoderation"),
            jsonOf(SubjectLinksResponse.serializer(), fixtures.links).keys,
        )
        assertEquals(
            setOf("flowId", "label", "typeId", "depth"),
            jsonOf(LinkAudience.serializer(), fixtures.audience).keys,
        )
        assertEquals(
            BackendJson.parseToJsonElement(
                """{"subjectId":42,"subjectName":"Предмет","periodKey":"2026-1","category":"QUEUE",
                "url":"https://example.invalid/queue","visibility":"FLOW","flowId":7103}""",
            ),
            jsonOf(SaveSubjectLinkRequest.serializer(), fixtures.save),
        )
        assertEquals(
            BackendJson.parseToJsonElement(
                """{"subjectId":42,"subjectName":"Предмет","periodKey":"2026-1","category":"QUEUE",
                "url":"https://example.invalid/queue","visibility":"PRIVATE"}""",
            ),
            jsonOf(SaveSubjectLinkRequest.serializer(), fixtures.privateSave),
        )
        assertEquals(
            BackendJson.parseToJsonElement("""{"periodKey":"2026-1","linkId":"${fixtures.id}"}"""),
            jsonOf(PinSubjectLinkRequest.serializer(), PinSubjectLinkRequest("2026-1", fixtures.id)),
        )
        assertEquals(
            BackendJson.parseToJsonElement("""{"value":-1}"""),
            jsonOf(ResourceVoteRequest.serializer(), ResourceVoteRequest(-1)),
        )
        assertEquals(
            setOf("id", "capability", "reason", "startsAt", "expiresAt"),
            jsonOf(UserRestriction.serializer(), fixtures.restriction.copy(expiresAt = fixtures.now)).keys,
        )
    }

    @Test
    fun optionalLinkFieldsDecodeFromBothNullAndAbsence() {
        val optional = listOf("title", "flowId", "audienceLabel", "reviewNote", "author")
        val publicLink = jsonOf(SubjectLink.serializer(), fixtures.link).with("visibility", "\"ALL\"")
        val expected = fixtures.link.copy(
            visibility = LinkVisibility.ALL,
            title = null,
            flowId = null,
            audienceLabel = null,
            reviewNote = null,
            author = null,
        )
        val explicitNulls = optional.fold(publicLink) { json, field -> json.with(field, "null") }
        assertEquals(expected, decode(SubjectLink.serializer(), explicitNulls))
        val absent = optional.fold(publicLink) { json, field -> json.with(field, null) }
        assertEquals(expected, decode(SubjectLink.serializer(), absent))

        val privateSave = jsonOf(SaveSubjectLinkRequest.serializer(), fixtures.privateSave).with("flowId", "null")
        assertNull(decode(SaveSubjectLinkRequest.serializer(), privateSave).flowId)
        val noPin = jsonOf(SubjectLinksResponse.serializer(), fixtures.links).with("pinnedId", "null")
        assertNull(decode(SubjectLinksResponse.serializer(), noPin).pinnedId)
        val noExpiry = jsonOf(UserRestriction.serializer(), fixtures.restriction).with("expiresAt", "null")
        assertNull(decode(UserRestriction.serializer(), noExpiry).expiresAt)
    }

    @Test
    fun aLinkFromABackendThatStillSendsIsSavedDecodesWithoutIt() {
        val legacy = jsonOf(SubjectLink.serializer(), fixtures.link).with("isSaved", "true")

        assertEquals(fixtures.link, decode(SubjectLink.serializer(), legacy))
    }

    @Test
    fun strictEnumsRejectUnknownNullNumericAndMalformedValuesWhileFutureRestrictionBlocksAll() {
        assertStrictEnum(LinkVisibilitySerializer, LinkVisibility.entries)
        assertStrictEnum(ReportReasonSerializer, ReportReason.entries)
        // 2.0 policy (13 Q7 (b)): category and status are display enums, so an unknown name decodes as UNKNOWN
        // instead of failing the answer; 1.x rejected it. Non-strings still fail and UNKNOWN is never encoded.
        assertDisplayEnum(LinkCategorySerializer, LinkCategory.entries, LinkCategory.UNKNOWN)
        assertDisplayEnum(SubjectLinkStatusSerializer, SubjectLinkStatus.entries, SubjectLinkStatus.UNKNOWN)

        val link = jsonOf(SubjectLink.serializer(), fixtures.link)
        val unknownCategory = decode(SubjectLink.serializer(), link.with("category", "\"UNKNOWN\""))
        assertEquals(LinkCategory.UNKNOWN, unknownCategory.category)
        for ((field, wire) in listOf("visibility" to "3", "status" to "null", "category" to "null")) {
            assertDecodeFails(SubjectLink.serializer(), link.with(field, wire), "SubjectLink.$field: $wire")
        }
        for (wire in listOf("\"GROUP\"", "\"FLOW_ALL\"")) {
            assertDecodeFails(SubjectLink.serializer(), link.with("visibility", wire), "SubjectLink.visibility: $wire")
        }

        assertEquals(
            RestrictionCapability.ALL,
            BackendJson.decodeFromString(RestrictionCapabilitySerializer, "\"FUTURE_CAPABILITY\""),
        )
        for (value in RestrictionCapability.entries) assertRoundTripsEnum(RestrictionCapabilitySerializer, value)
        for (wire in listOf("null", "0", "false", "{}")) {
            assertTextDecodeFails(RestrictionCapabilitySerializer, wire, "RestrictionCapability: $wire")
        }
    }

    @Test
    fun requiredLinkResponseRestrictionAndPolicyFieldsNeverBecomeJvmDefaults() {
        // ModerationPolicy is CO-02's. The 1.x quoted-number and duplicate-key cases are dropped (07 Q4 (b)).
        assertRequired(
            SubjectLink.serializer(),
            fixtures.link,
            "id", "subjectId", "subjectName", "periodKey", "category", "url", "visibility", "status", "score",
            "myVote", "isMine", "reportedByMe", "updatedAt",
        )
        assertRequired(
            SubjectLinksResponse.serializer(),
            fixtures.links,
            "mine", "shared", "previous", "audiences", "premoderation",
        )
        assertRequired(LinkAudience.serializer(), fixtures.audience, "flowId", "label", "typeId", "depth")
        assertRequired(
            SaveSubjectLinkRequest.serializer(),
            fixtures.save,
            "subjectId", "subjectName", "periodKey", "category", "url", "visibility",
        )
        assertRequired(PinSubjectLinkRequest.serializer(), PinSubjectLinkRequest("2026-1"), "periodKey")
        assertRequired(UserRestriction.serializer(), fixtures.restriction, "id", "capability", "reason", "startsAt")

        val link = jsonOf(SubjectLink.serializer(), fixtures.link)
        for (wire in listOf("1.5", "2147483648", "true")) {
            assertDecodeFails(SubjectLink.serializer(), link.with("score", wire), "SubjectLink.score: $wire")
        }
        // 2.0 policy (07 Q4 (b)): a quoted boolean is accepted; 1.x rejected it.
        assertEquals(true, decode(SubjectLink.serializer(), link.with("isMine", "\"true\"")).isMine)
        assertEquals(true, decode(SubjectLink.serializer(), link.with("reportedByMe", "\"true\"")).reportedByMe)
        for (vote in listOf("2", "-2")) {
            assertDecodeFails(SubjectLink.serializer(), link.with("myVote", vote), "SubjectLink.myVote: $vote")
        }
        val audience = jsonOf(LinkAudience.serializer(), fixtures.audience)
        for ((field, wire) in listOf("flowId" to "1.5", "flowId" to "true", "depth" to "0", "typeId" to "1.5")) {
            assertDecodeFails(LinkAudience.serializer(), audience.with(field, wire), "LinkAudience.$field: $wire")
        }
        val links = jsonOf(SubjectLinksResponse.serializer(), fixtures.links)
        for (list in listOf("mine", "shared", "previous", "audiences")) {
            assertDecodeFails(SubjectLinksResponse.serializer(), links.with(list, "[null]"), list)
        }
        assertDecodeFails(SubjectLinksResponse.serializer(), links.with("pinnedId", "\"not-a-uuid\""), "pinnedId")
    }

    @Test
    fun aFlowIdTravelsExactlyWithFlowVisibility() {
        // SubjectLinkRevision is CO-02's. A quoted flowId is dropped (07 Q4 (b)).
        val cases = listOf(
            jsonOf(SubjectLink.serializer(), fixtures.link) to { json: JsonObject ->
                decode(SubjectLink.serializer(), json).flowId
            },
            jsonOf(SaveSubjectLinkRequest.serializer(), fixtures.save) to { json: JsonObject ->
                decode(SaveSubjectLinkRequest.serializer(), json).flowId
            },
        )
        for ((json, flowIdOf) in cases) {
            assertEquals(ResourceContractFixtures.FLOW_ID, flowIdOf(json))
            for (wire in listOf("null", "1.5", "9223372036854775808", "{}")) {
                assertFailsWith<IllegalArgumentException>("flowId: $wire") { flowIdOf(json.with("flowId", wire)) }
            }
            assertFailsWith<IllegalArgumentException>("without flowId") { flowIdOf(json.with("flowId", null)) }
            for (visibility in listOf("PRIVATE", "ALL")) {
                assertFailsWith<IllegalArgumentException>("$visibility with flowId") {
                    flowIdOf(json.with("visibility", "\"$visibility\""))
                }
            }
        }
        // 2.0: the app cannot build a request Backend would refuse with 400.
        assertFailsWith<IllegalArgumentException> { fixtures.save.copy(flowId = null) }
        assertFailsWith<IllegalArgumentException> {
            fixtures.privateSave.copy(flowId = ResourceContractFixtures.FLOW_ID)
        }
    }

    private val malformedEnums = listOf("null", "0", "3", "true", "{}", "[]")

    private fun <E> assertStrictEnum(serializer: KSerializer<E>, entries: List<E>) {
        for (value in entries) assertRoundTripsEnum(serializer, value)
        for (wire in listOf("\"UNKNOWN\"") + malformedEnums) {
            assertTextDecodeFails(serializer, wire, "${serializer.descriptor.serialName}: $wire")
        }
    }

    private fun <E> assertDisplayEnum(serializer: KSerializer<E>, entries: List<E>, unknown: E) {
        val name = serializer.descriptor.serialName
        for (value in entries - unknown) assertRoundTripsEnum(serializer, value)
        for (wire in listOf("\"UNKNOWN\"", "\"FUTURE\"")) {
            assertEquals(unknown, BackendJson.decodeFromString(serializer, wire), "$name: $wire")
        }
        for (wire in malformedEnums) assertTextDecodeFails(serializer, wire, "$name: $wire")
        assertFailsWith<SerializationException> { BackendJson.encodeToString(serializer, unknown) }
    }

    private fun <E> assertRoundTripsEnum(serializer: KSerializer<E>, value: E) {
        val text = BackendJson.encodeToString(serializer, value)
        assertEquals("\"$value\"", text)
        assertEquals(value, BackendJson.decodeFromString(serializer, text))
    }

    private fun <T> assertRequired(serializer: KSerializer<T>, value: T, vararg names: String) {
        val json = jsonOf(serializer, value)
        for (name in names) {
            assertDecodeFails(serializer, json.with(name, null), "${serializer.descriptor.serialName}.$name missing")
            assertDecodeFails(serializer, json.with(name, "null"), "${serializer.descriptor.serialName}.$name null")
        }
    }
}
