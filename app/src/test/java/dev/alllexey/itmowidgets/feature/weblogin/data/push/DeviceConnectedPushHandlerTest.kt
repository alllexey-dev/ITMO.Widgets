package dev.alllexey.itmowidgets.feature.weblogin.data.push

import dev.alllexey.itmowidgets.core.notification.AppNotification
import dev.alllexey.itmowidgets.core.notification.AppNotificationChannels
import dev.alllexey.itmowidgets.core.notification.AppNotifier
import dev.alllexey.itmowidgets.core.notification.NotificationDestination
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.testing.RecordingDiagnostics
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import java.io.File
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceConnectedPushHandlerTest {
    private val notifier = RecordingNotifier()
    private val diagnostics = RecordingDiagnostics()
    private var enabled = true
    private val services = object : CustomServicesRepository {
        override fun observeEnabled() = flowOf(enabled)
        override suspend fun isEnabled() = enabled
        override suspend fun setEnabled(enabled: Boolean) { this@DeviceConnectedPushHandlerTest.enabled = enabled }
    }
    private val handler = DeviceConnectedPushHandler(services, diagnostics)

    @Test fun `Backend's agent fixture names the agent and opens the connections`() = runTest {
        handler.handle(vendoredPayload(), notifier)

        val title = UiText.Res(CoreStrings.notification_device_connected_title)
        assertEquals(
            AppNotification(
                channel = AppNotificationChannels.CONNECTIONS,
                id = Uuid.parse("00000000-0000-4000-8000-000000000601").hashCode(),
                title = title,
                text = UiText.Res(CoreStrings.notification_device_connected_agent, listOf("nas")),
                destination = NotificationDestination.Connections,
                publicTitle = title,
            ),
            notifier.shown.single(),
        )
    }

    @Test fun `a browser, a nameless agent and an unknown kind get their own texts`() = runTest {
        handler.handle(payload("WEB_SESSION", name = null), notifier)
        handler.handle(payload("AGENT_SESSION", name = "  "), notifier)
        handler.handle(payload("TV", name = "tv"), notifier)

        assertEquals(
            listOf(
                UiText.Res(CoreStrings.notification_device_connected_web),
                UiText.Res(CoreStrings.notification_device_connected_other),
                UiText.Res(CoreStrings.notification_device_connected_other),
            ),
            notifier.shown.map { it.text },
        )
    }

    @Test fun `nothing shows without the opt-in and a malformed push is only recorded`() = runTest {
        enabled = false
        handler.handle(vendoredPayload(), notifier)
        assertTrue(notifier.shown.isEmpty())

        enabled = true
        handler.handle(Json.parseToJsonElement("""{"kind":"AGENT"}"""), notifier)
        assertTrue(notifier.shown.isEmpty())
        assertEquals(listOf("WARNING:DeviceConnectedPush:Invalid device connection push"), diagnostics.messages)
    }

    private fun vendoredPayload(): JsonElement {
        val fixture = File("../shared/backend-client/src/commonTest/resources/contract/fcm/DEVICE_CONNECTED_PAYLOAD.json")
        return Json.parseToJsonElement(fixture.readText()).jsonObject.getValue("data").jsonObject.getValue("payload")
    }

    private fun payload(kind: String, name: String?): JsonElement = Json.parseToJsonElement(
        """{"connectionId":"00000000-0000-4000-8000-000000000501","kind":"$kind",""" +
            """"name":${name?.let { "\"$it\"" }},"connectedAt":"2026-10-05T09:00:00Z"}""",
    )

    private class RecordingNotifier : AppNotifier {
        val shown = mutableListOf<AppNotification>()
        override fun show(notification: AppNotification) { shown += notification }
        override fun cancel(channel: String, id: Int) = Unit
        override fun clear() = Unit
    }
}
