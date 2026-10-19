package dev.alllexey.itmowidgets.core.notification

import dev.alllexey.itmowidgets.client.push.FcmDecoder
import dev.alllexey.itmowidgets.client.push.FriendshipEvent
import dev.alllexey.itmowidgets.client.push.FriendshipEventPayload
import dev.alllexey.itmowidgets.client.push.SportAutoSignLessonsPayload
import dev.alllexey.itmowidgets.client.push.SportFreeSignLessonsPayload
import dev.alllexey.itmowidgets.core.network.Core2Harness.Companion.contractFixture
import dev.alllexey.itmowidgets.core.storage.PushCategoryPreferences
import dev.alllexey.itmowidgets.core.testing.InMemoryPreferencesDataStore
import dev.alllexey.itmowidgets.core.testing.RecordingAppNotifier
import dev.alllexey.itmowidgets.core.testing.RecordingDiagnostics
import dev.alllexey.itmowidgets.core.text.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class FcmPayloadDispatcherTest {

    @Test fun `routes independent types and ignores unknown malformed and missing payloads`() = runTest {
        val received = mutableListOf<String>()
        fun handler(name: String) = object : FcmPayloadHandler {
            override val type = name
            override val channel = AppNotificationChannels.SPORT
            override suspend fun handle(payload: JsonElement, notifier: AppNotifier) { received += name + payload.jsonObject["id"]!!.jsonPrimitive.int }
        }
        val dispatcher = dispatcher(listOf(handler("first"), handler("second")), RecordingDiagnostics())
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

    @Test fun `Backend's friendship and sport messages reach their handlers with the payload Core 2_0 decodes`() = runTest {
        val types = listOf(FriendshipEventPayload.TYPE, SportFreeSignLessonsPayload.TYPE, SportAutoSignLessonsPayload.TYPE)
        val recording = RecordingHandlers(*types.toTypedArray())
        val dispatcher = dispatcher(recording.handlers, RecordingDiagnostics())

        for (type in types) dispatcher.dispatch(fcmData(type).toString())

        assertEquals(types, recording.received.map { it.first })
        for ((type, payload) in recording.received) assertEquals(fcmData(type)["payload"], payload)
        val friendship = FcmDecoder.friendshipEvent(recording.received[0].second)
        assertEquals(FriendshipEvent.REQUEST_RECEIVED, friendship.event)
        assertEquals(100002, friendship.user.isu)
        for ((_, payload) in recording.received.drop(1)) {
            assertEquals(9001L, FcmDecoder.sportFreeSignLessons(payload).sportLessons.first().id)
        }
    }

    @Test fun `an unknown event of a known type still reaches its handler, which owns the payload`() = runTest {
        val recording = RecordingHandlers(FriendshipEventPayload.TYPE)
        val dispatcher = dispatcher(recording.handlers, RecordingDiagnostics())
        val known = fcmData(FriendshipEventPayload.TYPE)
        val payload = JsonObject(known["payload"]!!.jsonObject + ("event" to JsonPrimitive("FUTURE_EVENT")))

        dispatcher.dispatch(JsonObject(known + ("payload" to payload)).toString())

        assertEquals(payload, recording.received.single().second)
    }

    @Test fun `an unknown type is recorded and a blank or oversized message is dropped silently`() = runTest {
        val recording = RecordingHandlers("known")
        val diagnostics = RecordingDiagnostics()
        val dispatcher = dispatcher(recording.handlers, diagnostics)

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
        for (wire in listOf("""{"type":"known","payload":{"name":"Тестовый пользователь"""", """{"payload":{"name":"Тестовый"}}""")) {
            val diagnostics = RecordingDiagnostics()
            val dispatcher = dispatcher(RecordingHandlers("known").handlers, diagnostics)

            dispatcher.dispatch(wire)

            val entry = diagnostics.entries.value.single()
            assertEquals("FCM dispatch failed: malformed message", entry.message)
            assertNull(entry.stackTrace)
        }
    }

    @Test fun `one handler failure does not poison the next message and cancellation propagates`() = runTest {
        var fail = true
        var handled = false
        val handler = object : FcmPayloadHandler {
            override val type = "type"
            override val channel = AppNotificationChannels.SPORT
            override suspend fun handle(payload: JsonElement, notifier: AppNotifier) {
                if (fail) error("Synthetic failure")
                handled = true
            }
        }
        val dispatcher = dispatcher(listOf(handler), RecordingDiagnostics())
        val wire = """{"type":"type","payload":{}}"""
        dispatcher.dispatch(wire)
        fail = false
        dispatcher.dispatch(wire)
        assertTrue(handled)
        val cancelled = object : FcmPayloadHandler {
            override val type = "type"
            override val channel = AppNotificationChannels.SPORT
            override suspend fun handle(payload: JsonElement, notifier: AppNotifier) { throw CancellationException() }
        }
        try {
            dispatcher(listOf(cancelled), RecordingDiagnostics()).dispatch(wire)
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }

    @Test fun `a type the user turned off still runs its handler but shows nothing, and the others still show`() = runTest {
        val notifier = RecordingAppNotifier()
        val categories = PushCategoryPreferences(InMemoryPreferencesDataStore())
        fun posting(name: String, category: String) = object : FcmPayloadHandler {
            override val type = name
            override val channel = category
            override suspend fun handle(payload: JsonElement, notifier: AppNotifier) {
                notifier.cancel(category, 0)
                notifier.show(AppNotification(category, 1, UiText.Dynamic(name), UiText.Dynamic(name), NotificationDestination.Sport))
            }
        }
        val dispatcher = FcmPayloadDispatcher(
            listOf(posting("sport", AppNotificationChannels.SPORT), posting("friends", AppNotificationChannels.FRIENDS)),
            notifier,
            categories,
            RecordingDiagnostics(),
        )
        categories.setEnabled(AppNotificationChannels.SPORT, false)

        dispatcher.dispatch("""{"type":"sport","payload":{}}""")
        dispatcher.dispatch("""{"type":"friends","payload":{}}""")

        assertEquals(listOf(AppNotificationChannels.FRIENDS), notifier.shown.map { it.channel })
        assertEquals(listOf(AppNotificationChannels.SPORT to 0, AppNotificationChannels.FRIENDS to 0), notifier.cancelled)
    }

    private fun dispatcher(handlers: List<FcmPayloadHandler>, diagnostics: RecordingDiagnostics) = FcmPayloadDispatcher(
        handlers,
        RecordingAppNotifier(),
        PushCategoryPreferences(InMemoryPreferencesDataStore()),
        diagnostics,
    )

    /** The `data` string of Backend's golden FCM message of [type], as the FCM service receives it. */
    private fun fcmData(type: String): JsonObject =
        Json.parseToJsonElement(contractFixture("fcm/$type.json")).jsonObject["data"]!!.jsonObject

    private class RecordingHandlers(vararg types: String) {
        val received = mutableListOf<Pair<String, JsonElement>>()
        val handlers: List<FcmPayloadHandler> = types.map { name ->
            object : FcmPayloadHandler {
                override val type = name
                override val channel = AppNotificationChannels.SPORT
                override suspend fun handle(payload: JsonElement, notifier: AppNotifier) { received += name to payload }
            }
        }
    }
}
