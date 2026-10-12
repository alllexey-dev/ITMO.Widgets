package dev.alllexey.itmowidgets.feature.sport.data.push

import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.client.push.FcmDecoder
import dev.alllexey.itmowidgets.client.push.SportAgentBookedPayload
import dev.alllexey.itmowidgets.core.diagnostics.AppDiagnostics
import dev.alllexey.itmowidgets.core.notification.AppNotifier
import dev.alllexey.itmowidgets.core.notification.FcmPayloadHandler
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import kotlinx.serialization.json.JsonElement

/** The user's agent booked a lesson: nothing to book here, only the `sport` notification that says so. */
class SportAgentBookedPushHandler(
    private val notifier: AppNotifier,
    private val services: CustomServicesRepository,
    private val diagnostics: AppDiagnostics,
) : FcmPayloadHandler {
    override val type: String = SportAgentBookedPayload.TYPE

    override suspend fun handle(payload: JsonElement) {
        if (!services.isEnabled()) return
        val booked = try {
            FcmDecoder.sportAgentBooked(payload)
        } catch (_: BackendException) {
            // The decoder's cause may quote the payload.
            diagnostics.warn(TAG, "Invalid agent booking push")
            return
        }
        booked.toAppNotification()?.let(notifier::show)
    }

    private companion object {
        const val TAG = "SportAgentBookedPush"
    }
}
