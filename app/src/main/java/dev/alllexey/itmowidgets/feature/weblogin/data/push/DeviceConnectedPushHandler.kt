package dev.alllexey.itmowidgets.feature.weblogin.data.push

import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.client.pairing.PairingKind
import dev.alllexey.itmowidgets.client.push.DeviceConnectedPayload
import dev.alllexey.itmowidgets.client.push.FcmDecoder
import dev.alllexey.itmowidgets.core.diagnostics.AppDiagnostics
import dev.alllexey.itmowidgets.core.notification.AppNotification
import dev.alllexey.itmowidgets.core.notification.AppNotificationChannels
import dev.alllexey.itmowidgets.core.notification.AppNotifier
import dev.alllexey.itmowidgets.core.notification.FcmPayloadHandler
import dev.alllexey.itmowidgets.core.notification.NotificationDestination
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import kotlinx.serialization.json.JsonElement

/**
 * Backend's push after every new connection (a pairing or a web sign-in): one notification per connection that
 * opens «Подключённые устройства», so a forwarded or malicious link does not go unnoticed. An agent is named; the
 * lock screen shows only the title.
 */
class DeviceConnectedPushHandler(
    private val notifier: AppNotifier,
    private val services: CustomServicesRepository,
    private val diagnostics: AppDiagnostics,
) : FcmPayloadHandler {
    override val type: String = DeviceConnectedPayload.TYPE

    override suspend fun handle(payload: JsonElement) {
        if (!services.isEnabled()) return
        val connected = try {
            FcmDecoder.deviceConnected(payload)
        } catch (_: BackendException) {
            diagnostics.warn(TAG, "Invalid device connection push")
            return
        }
        val title = UiText.Res(CoreStrings.notification_device_connected_title)
        notifier.show(
            AppNotification(
                channel = AppNotificationChannels.CONNECTIONS,
                id = connected.connectionId.hashCode(),
                title = title,
                text = connected.text(),
                destination = NotificationDestination.Connections,
                publicTitle = title,
            ),
        )
    }

    private fun DeviceConnectedPayload.text(): UiText {
        val agent = name?.trim()?.takeIf { it.isNotEmpty() }
        return when {
            kind == PairingKind.WEB_SESSION -> UiText.Res(CoreStrings.notification_device_connected_web)
            agent != null && (kind == PairingKind.AGENT || kind == PairingKind.AGENT_SESSION) ->
                UiText.Res(CoreStrings.notification_device_connected_agent, listOf(agent))
            else -> UiText.Res(CoreStrings.notification_device_connected_other)
        }
    }

    private companion object {
        const val TAG = "DeviceConnectedPush"
    }
}
