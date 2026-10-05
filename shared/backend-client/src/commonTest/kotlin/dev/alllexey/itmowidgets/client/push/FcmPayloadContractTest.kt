package dev.alllexey.itmowidgets.client.push

import dev.alllexey.itmowidgets.client.common.UserCapabilities
import dev.alllexey.itmowidgets.client.common.UserData
import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.client.json.BackendJson
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
 * Port of Core 1.7.0 `FcmPayloadContractTest`. 1.x wrote the wire with Gson and kept an `OffsetDateTime`; 2.0 only
 * decodes and keeps an `Instant`, so the offset time is checked as the same instant. 1.x Gson exceptions become
 * [BackendException.Contract].
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
}
