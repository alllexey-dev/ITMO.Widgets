package dev.alllexey.itmowidgets.client.push

import dev.alllexey.itmowidgets.client.common.UserCapabilities
import dev.alllexey.itmowidgets.client.common.UserData
import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.client.json.BackendJson
import dev.alllexey.itmowidgets.client.connections.ConnectionClient
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.time.Instant

/**
 * The FCM payloads. The client only decodes and keeps an `Instant`, so the offset time is checked as the same
 * instant; a malformed payload is [BackendException.Contract].
 */
class FcmPayloadContractTest {

    private val actor = UserData(
        isu = 100001,
        name = "Synthetic actor",
        pictureUrl = null,
        groups = emptyList(),
        capabilities = UserCapabilities(canViewSchedule = false, canViewSport = true, canViewFriends = false),
    )
    private val time = Instant.parse("2026-09-15T12:34:56+03:00")

    private val actorJson =
        """{"isu":100001,"name":"Synthetic actor","groups":[],""" +
            """"capabilities":{"canViewSchedule":false,"canViewSport":true,"canViewFriends":false}}"""

    private fun payloadJson(event: String) =
        """{"event":"$event","user":$actorJson,"occurredAt":"2026-09-15T12:34:56+03:00"}"""

    private fun wire(event: String) = """{"type":"FRIENDSHIP_EVENT_PAYLOAD","payload":${payloadJson(event)}}"""

    @Test
    fun bothEventsPreserveTheDataWrapperActorCapabilitiesAndOffsetTime() {
        for (event in FriendshipEvent.entries) {
            val wire = wire(event.name)

            val envelope = FcmDecoder.envelope(wire)

            assertEquals(FriendshipEventPayload.TYPE, envelope.type)
            assertEquals(FriendshipEventPayload(event, actor, time), FcmDecoder.friendshipEvent(envelope.payload))
            assertEquals(setOf("type", "payload"), BackendJson.parseToJsonElement(wire).jsonObject.keys)
            assertEquals(setOf("event", "user", "occurredAt"), envelope.payload.keys)
            assertFalse(wire.contains("settings"))
        }
    }

    @Test
    fun unknownEventAndIncompletePayloadFailClosed() {
        val valid = BackendJson.parseToJsonElement(payloadJson("REQUEST_RECEIVED")).jsonObject
        for (field in listOf("event", "user", "occurredAt")) {
            val missing = JsonObject(valid - field)
            assertFailsWith<BackendException.Contract>(field) { FcmDecoder.friendshipEvent(missing) }
            val nil = JsonObject(valid + (field to JsonNull))
            assertFailsWith<BackendException.Contract>(field) { FcmDecoder.friendshipEvent(nil) }
        }
        for (event in listOf("UNKNOWN", "request_received", "")) {
            val unknown = JsonObject(valid + ("event" to JsonPrimitive(event)))
            assertFailsWith<BackendException.Contract>(event) { FcmDecoder.friendshipEvent(unknown) }
        }
        for (wire in listOf("null", "[]", "42", "{}")) {
            val element = BackendJson.parseToJsonElement(wire)
            assertFailsWith<BackendException.Contract>(wire) { FcmDecoder.friendshipEvent(element) }
        }
    }

    @Test
    fun aDeviceConnectionKeepsAnUnknownClientAndNeedsItsFields() {
        val valid = BackendJson.parseToJsonElement(
            """{"connectionId":"00000000-0000-4000-8000-000000000601","client":"web",""" +
                """"name":"Chrome, macOS","connectedAt":"2026-10-05T12:00+03:00"}""",
        ).jsonObject

        val web = FcmDecoder.deviceConnected(valid)
        assertEquals(ConnectionClient.WEB, web.client)
        assertEquals("Chrome, macOS", web.name)
        assertEquals(Instant.parse("2026-10-05T09:00:00Z"), web.connectedAt)
        val unknown = JsonObject(valid + ("client" to JsonPrimitive("tv")))
        assertEquals(ConnectionClient.UNKNOWN, FcmDecoder.deviceConnected(unknown).client)
        for (field in listOf("connectionId", "client", "name", "connectedAt")) {
            assertFailsWith<BackendException.Contract>(field) { FcmDecoder.deviceConnected(JsonObject(valid - field)) }
        }
    }

    @Test
    fun anAgentBookingNeedsTheAgentsName() {
        val payload = JsonObject(mapOf("sportLessons" to BackendJson.parseToJsonElement("[]")))

        assertFailsWith<BackendException.Contract> { FcmDecoder.sportAgentBooked(payload) }
        val named = JsonObject(payload + ("agentName" to JsonPrimitive("nas")))
        assertEquals(SportAgentBookedPayload(emptyList(), "nas"), FcmDecoder.sportAgentBooked(named))
    }
}
