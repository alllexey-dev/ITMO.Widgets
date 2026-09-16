package dev.alllexey.itmowidgets.feature.social.data.push

import api.myitmo.MyItmo
import com.google.gson.JsonParser
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.ItmoWidgetsImpl
import dev.alllexey.itmowidgets.core.model.UserCapabilities
import dev.alllexey.itmowidgets.core.model.UserData
import dev.alllexey.itmowidgets.core.model.fcm.impl.FriendshipEvent
import dev.alllexey.itmowidgets.core.model.fcm.impl.FriendshipEventPayload
import dev.alllexey.itmowidgets.core.notification.*
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.social.SocialRepository
import dev.alllexey.itmowidgets.core.text.UiText
import java.lang.reflect.Proxy
import java.time.OffsetDateTime
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import dev.alllexey.itmowidgets.core.testing.RecordingDiagnostics

class FriendshipPushHandlerTest {
    private val gson = ItmoWidgetsImpl(MyItmo()).gson
    private val actor = UserData(100001, "  Тестовый пользователь  ", null, emptyList(), UserCapabilities(false, false))

    @Test fun `both events notify even in foreground and route to the actor with stable id`() = runTest {
        for (event in FriendshipEvent.entries) {
            val fixture = Fixture()
            fixture.loaded = true
            fixture.handler.handle(payload(event))
            val notification = fixture.notifications.single()
            assertEquals(AppNotificationChannels.FRIENDS, notification.channel)
            assertEquals(100001, notification.id)
            assertEquals(NotificationDestination.UserProfile(100001), notification.destination)
            assertEquals(UiText.Resource(
                if (event == FriendshipEvent.REQUEST_RECEIVED) R.string.notification_friend_request else R.string.notification_friend_accepted,
                listOf("Тестовый пользователь")
            ), notification.text)
            assertEquals(1, fixture.refreshes)
        }
    }

    @Test fun `cold repository is not refreshed while loaded repository refreshes without notification permission`() = runTest {
        val fixture = Fixture()
        fixture.handler.handle(payload(FriendshipEvent.REQUEST_RECEIVED))
        assertEquals(0, fixture.refreshes)
        fixture.loaded = true
        fixture.failNotifier = true
        fixture.handler.handle(payload(FriendshipEvent.REQUEST_ACCEPTED))
        assertEquals(1, fixture.refreshes)
    }

    @Test fun `unknown malformed and disabled events cannot notify or refresh`() = runTest {
        val fixture = Fixture()
        fixture.loaded = true
        for (wire in listOf("{}", "null", """{"event":"UNKNOWN"}""")) fixture.handler.handle(JsonParser.parseString(wire))
        val unknown = payload(FriendshipEvent.REQUEST_RECEIVED).asJsonObject.apply { addProperty("event", "UNKNOWN") }
        fixture.handler.handle(unknown)
        fixture.enabled = false
        fixture.handler.handle(payload(FriendshipEvent.REQUEST_ACCEPTED))
        assertTrue(fixture.notifications.isEmpty())
        assertEquals(0, fixture.refreshes)
    }

    private fun payload(event: FriendshipEvent) = gson.toJsonTree(
        FriendshipEventPayload(event, actor, OffsetDateTime.parse("2026-09-15T10:00:00+03:00")))

    private inner class Fixture {
        var loaded = false
        var enabled = true
        var refreshes = 0
        var failNotifier = false
        val notifications = mutableListOf<AppNotification>()
        private val social = Proxy.newProxyInstance(SocialRepository::class.java.classLoader,
            arrayOf(SocialRepository::class.java)) { _, method, _ ->
            when (method.name) {
                "getCurrentFriends" -> if (loaded) emptyList<dev.alllexey.itmowidgets.core.model.UserProfile>() else null
                "refresh" -> { refreshes++; Unit }
                else -> error("Unexpected social call")
            }
        } as SocialRepository
        val handler = FriendshipPushHandler(gson, object : AppNotifier {
            override fun show(notification: AppNotification) {
                if (failNotifier) error("Synthetic permission race")
                notifications += notification
            }
            override fun clear() = Unit
        }, social, object : CustomServicesRepository {
            override fun observeEnabled() = flowOf(enabled)
            override suspend fun isEnabled() = enabled
            override suspend fun setEnabled(enabled: Boolean) { this@Fixture.enabled = enabled }
        }, RecordingDiagnostics())
    }
}
