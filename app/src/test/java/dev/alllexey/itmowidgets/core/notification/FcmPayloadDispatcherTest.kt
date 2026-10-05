package dev.alllexey.itmowidgets.core.notification

import api.myitmo.MyItmo
import dev.alllexey.itmowidgets.core.ItmoWidgetsImpl
import dev.alllexey.itmowidgets.core.model.UserCapabilities
import dev.alllexey.itmowidgets.core.model.UserData
import dev.alllexey.itmowidgets.core.model.fcm.FcmJsonWrapper
import dev.alllexey.itmowidgets.core.model.fcm.impl.FriendshipEvent
import dev.alllexey.itmowidgets.core.model.fcm.impl.FriendshipEventPayload
import dev.alllexey.itmowidgets.core.model.fcm.impl.SportAutoSignLessonsPayload
import dev.alllexey.itmowidgets.core.model.fcm.impl.SportFreeSignLessonsPayload
import dev.alllexey.itmowidgets.core.testing.RecordingDiagnostics
import java.time.OffsetDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class FcmPayloadDispatcherTest {
    /** Core 1.x's Gson writes the envelope exactly as Backend sends it. */
    private val gson = ItmoWidgetsImpl(MyItmo()).gson

    @Test fun `routes independent types and ignores unknown malformed and missing payloads`() = runTest {
        val received = mutableListOf<String>()
        fun handler(name: String) = object : FcmPayloadHandler {
            override val type = name
            override suspend fun handle(payload: JsonElement) { received += name + payload.jsonObject["id"]!!.jsonPrimitive.int }
        }
        val dispatcher = FcmPayloadDispatcher(setOf(handler("first"), handler("second")), RecordingDiagnostics())
        for (wire in listOf("{", "null", "{}", "[]", "", "{\"type\":\"first\"}",
            "{\"type\":\"first\",\"payload\":null}", "{\"type\":\"first\",\"payload\":[1]}",
            "{\"type\":\"third\",\"payload\":{}}")) {
            dispatcher.dispatch(wire)
        }
        assertTrue(received.isEmpty())
        dispatcher.dispatch("""{"type":"second","payload":{"id":2}}""")
        dispatcher.dispatch("""{"type":"first","payload":{"id":1},"sentAt":"ignored"}""")
        assertEquals(listOf("second2", "first1"), received)
    }

    @Test fun `friendship and sport envelopes reach their handlers with a payload Core 1_x decodes unchanged`() = runTest {
        val friendship = FriendshipEventPayload(
            FriendshipEvent.REQUEST_RECEIVED,
            UserData(100001, "Тестовый пользователь <a&b>", null, emptyList(), UserCapabilities(false, false)),
            OffsetDateTime.parse("2026-09-15T10:00:00+03:00")
        )
        val lessons = """{"sportLessons":[{"id":42,"sectionName":"Секция","start":"2026-09-21T12:00:00+03:00","end":"2026-09-21T13:00:00+03:00"}]}"""
        val recording = RecordingHandlers(FriendshipEventPayload.TYPE, SportFreeSignLessonsPayload.TYPE, SportAutoSignLessonsPayload.TYPE)
        val dispatcher = FcmPayloadDispatcher(recording.handlers, RecordingDiagnostics())

        dispatcher.dispatch(gson.toJson(FcmJsonWrapper(FriendshipEventPayload.TYPE, gson.toJsonTree(friendship))))
        dispatcher.dispatch("""{"type":"${SportFreeSignLessonsPayload.TYPE}","payload":$lessons}""")
        dispatcher.dispatch("""{"type":"${SportAutoSignLessonsPayload.TYPE}","payload":$lessons}""")

        assertEquals(3, recording.received.size)
        val (friendshipType, friendshipPayload) = recording.received[0]
        assertEquals(FriendshipEventPayload.TYPE, friendshipType)
        assertEquals(friendship, gson.fromJson(friendshipPayload.toString(), FriendshipEventPayload::class.java))
        for ((index, type) in listOf(SportFreeSignLessonsPayload.TYPE, SportAutoSignLessonsPayload.TYPE).withIndex()) {
            val (receivedType, payload) = recording.received[index + 1]
            assertEquals(type, receivedType)
            val lesson = gson.fromJson(payload.toString(), SportFreeSignLessonsPayload::class.java).sportLessons.single()
            assertEquals(42L, lesson.id)
            assertEquals(OffsetDateTime.parse("2026-09-21T13:00:00+03:00"), lesson.end)
        }
    }

    @Test fun `an unknown type is recorded and a blank or oversized message is dropped silently`() = runTest {
        val recording = RecordingHandlers("known")
        val diagnostics = RecordingDiagnostics()
        val dispatcher = FcmPayloadDispatcher(recording.handlers, diagnostics)

        dispatcher.dispatch("""{"type":"FUTURE_PAYLOAD","payload":{}}""")
        dispatcher.dispatch("   ")
        // Under the cap in characters, over it in UTF-8 bytes.
        val oversized = """{"type":"known","payload":{"text":"${"я".repeat(FcmPayloadDispatcher.MAX_PAYLOAD_BYTES / 2)}"}}"""
        assertTrue(oversized.length < FcmPayloadDispatcher.MAX_PAYLOAD_BYTES)
        dispatcher.dispatch(oversized)

        assertTrue(recording.received.isEmpty())
        assertEquals(listOf("WARNING:FcmPayloadDispatcher:Unknown FCM payload type: FUTURE_PAYLOAD"), diagnostics.messages)
    }

    @Test fun `a malformed message is recorded without its content`() = runTest {
        val diagnostics = RecordingDiagnostics()
        val dispatcher = FcmPayloadDispatcher(RecordingHandlers("known").handlers, diagnostics)

        dispatcher.dispatch("""{"type":"known","payload":{"name":"Тестовый пользователь"""")

        val entry = diagnostics.entries.value.single()
        assertEquals("FCM dispatch failed: malformed message", entry.message)
        assertNull(entry.stackTrace)
    }

    @Test fun `one handler failure does not poison the next message and cancellation propagates`() = runTest {
        var fail = true
        var handled = false
        val handler = object : FcmPayloadHandler {
            override val type = "type"
            override suspend fun handle(payload: JsonElement) {
                if (fail) error("Synthetic failure")
                handled = true
            }
        }
        val dispatcher = FcmPayloadDispatcher(setOf(handler), RecordingDiagnostics())
        val wire = """{"type":"type","payload":{}}"""
        dispatcher.dispatch(wire)
        fail = false
        dispatcher.dispatch(wire)
        assertTrue(handled)
        val cancelled = object : FcmPayloadHandler {
            override val type = "type"
            override suspend fun handle(payload: JsonElement) { throw CancellationException() }
        }
        try {
            FcmPayloadDispatcher(setOf(cancelled), RecordingDiagnostics()).dispatch(wire)
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }

    private class RecordingHandlers(vararg types: String) {
        val received = mutableListOf<Pair<String, JsonElement>>()
        val handlers: Set<FcmPayloadHandler> = types.map { name ->
            object : FcmPayloadHandler {
                override val type = name
                override suspend fun handle(payload: JsonElement) { received += name to payload }
            }
        }.toSet()
    }
}
