package dev.alllexey.itmowidgets.feature.social.data.push

import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.client.push.FriendshipEvent
import dev.alllexey.itmowidgets.core.notification.*
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.social.SocialRepository
import dev.alllexey.itmowidgets.core.testing.RecordingDiagnostics
import dev.alllexey.itmowidgets.core.text.UiText
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.*
import org.junit.Test

class FriendshipPushHandlerTest {

    @Test fun `handles the stable friendship payload type`() {
        assertEquals("FRIENDSHIP_EVENT_PAYLOAD", Fixture().handler.type)
    }

    @Test fun `both events notify even in foreground and route to the actor with stable id`() = runTest {
        for (event in FriendshipEvent.entries) {
            val fixture = Fixture()
            fixture.loaded = true
            fixture.handler.handle(payload(event.name))
            val notification = fixture.notifier.shown.single()
            assertEquals(AppNotificationChannels.FRIENDS, notification.channel)
            assertEquals(100001, notification.id)
            assertEquals(NotificationDestination.UserProfile(100001), notification.destination)
            assertEquals(UiText.Resource(R.string.notification_channel_friends), notification.title)
            assertEquals(UiText.Resource(
                if (event == FriendshipEvent.REQUEST_RECEIVED) R.string.notification_friend_request else R.string.notification_friend_accepted,
                listOf("Тестовый пользователь")
            ), notification.text)
            assertEquals(1, fixture.refreshes)
            assertTrue(fixture.diagnostics.messages.isEmpty())
        }
    }

    @Test fun `blank name falls back to the isu`() = runTest {
        val fixture = Fixture()
        fixture.handler.handle(payload("REQUEST_ACCEPTED", name = "   "))
        assertEquals(listOf("100001"), (fixture.notifier.shown.single().text as UiText.Resource).arguments)
    }

    @Test fun `cold repository is not refreshed while loaded repository refreshes without notification permission`() = runTest {
        val fixture = Fixture()
        fixture.handler.handle(payload("REQUEST_RECEIVED"))
        assertEquals(1, fixture.notifier.shown.size)
        assertEquals(0, fixture.refreshes)
        fixture.loaded = true
        fixture.notifier.fail = true
        fixture.handler.handle(payload("REQUEST_ACCEPTED"))
        assertEquals(1, fixture.refreshes)
        assertEquals(listOf("WARNING:FriendshipPush:Friendship push operation failed"), fixture.diagnostics.messages)
    }

    @Test fun `unknown event is dropped with a warning`() = runTest {
        val fixture = Fixture()
        fixture.loaded = true
        fixture.handler.handle(payload("UNKNOWN"))
        assertNothingHappened(fixture)
        assertEquals(listOf(INVALID), fixture.diagnostics.messages)
    }

    @Test fun `malformed payloads are dropped with a warning that does not quote them`() = runTest {
        val fixture = Fixture()
        fixture.loaded = true
        val valid = payload("REQUEST_RECEIVED").toString()
        val malformed = listOf(
            "{}",
            "null",
            "[]",
            """{"event":"REQUEST_RECEIVED"}""",
            valid.replace("\"occurredAt\":\"2026-10-05T12:00+03:00\"", "\"occurredAt\":null"),
            valid.replace("\"canViewFriends\":true", "\"canViewFriends\":null"),
        )
        for (wire in malformed) fixture.handler.handle(Json.parseToJsonElement(wire))
        assertNothingHappened(fixture)
        assertEquals(List(malformed.size) { INVALID }, fixture.diagnostics.messages)
        assertTrue(fixture.diagnostics.entries.value.all { it.stackTrace == null })
    }

    @Test fun `non-positive isu is dropped`() = runTest {
        val fixture = Fixture()
        fixture.loaded = true
        fixture.handler.handle(payload("REQUEST_RECEIVED", isu = 0))
        fixture.handler.handle(payload("REQUEST_ACCEPTED", isu = -5))
        assertNothingHappened(fixture)
    }

    @Test fun `opt-in off skips every payload before decoding`() = runTest {
        val fixture = Fixture()
        fixture.loaded = true
        fixture.enabled = false
        fixture.handler.handle(payload("REQUEST_ACCEPTED"))
        fixture.handler.handle(payload("UNKNOWN"))
        assertNothingHappened(fixture)
        assertTrue(fixture.diagnostics.messages.isEmpty())
    }

    private fun assertNothingHappened(fixture: Fixture) {
        assertTrue(fixture.notifier.shown.isEmpty())
        assertEquals(0, fixture.refreshes)
    }

    private fun payload(event: String, isu: Int = 100001, name: String = "  Тестовый пользователь  "): JsonElement =
        Json.parseToJsonElement("""
            {
              "event": "$event",
              "user": {
                "isu": $isu,
                "name": "$name",
                "pictureUrl": null,
                "groups": [{"name": "К3240", "course": 2, "facultyShortName": "ФИТИП"}],
                "capabilities": {"canViewSchedule": false, "canViewSport": false, "canViewFriends": true}
              },
              "occurredAt": "2026-10-05T12:00+03:00"
            }
        """.trimIndent())

    private class RecordingNotifier : AppNotifier {
        var fail = false
        val shown = mutableListOf<AppNotification>()
        override fun show(notification: AppNotification) {
            if (fail) error("Synthetic permission race")
            shown += notification
        }
        override fun cancel(channel: String, id: Int) = Unit
        override fun clear() = Unit
    }

    private inner class Fixture {
        var loaded = false
        var enabled = true
        var refreshes = 0
        val notifier = RecordingNotifier()
        val diagnostics = RecordingDiagnostics()
        private val social = Proxy.newProxyInstance(SocialRepository::class.java.classLoader,
            arrayOf(SocialRepository::class.java)) { _, method, _ ->
            when (method.name) {
                "getCurrentFriends" -> if (loaded) emptyList<dev.alllexey.itmowidgets.core.model.UserProfile>() else null
                "refresh" -> { refreshes++; Unit }
                else -> error("Unexpected social call")
            }
        } as SocialRepository
        private val services = object : CustomServicesRepository {
            override fun observeEnabled() = flowOf(enabled)
            override suspend fun isEnabled() = enabled
            override suspend fun setEnabled(enabled: Boolean) { this@Fixture.enabled = enabled }
        }
        val handler = FriendshipPushHandler(notifier, social, services, diagnostics)
    }

    private companion object {
        const val INVALID = "WARNING:FriendshipPush:Invalid friendship push"
    }
}
