package dev.alllexey.itmowidgets.client.push

import dev.alllexey.itmowidgets.client.json.UuidSerializer
import dev.alllexey.itmowidgets.client.json.WireInstantSerializer
import dev.alllexey.itmowidgets.client.pairing.PairingKind
import dev.alllexey.itmowidgets.client.sport.model.SportLessonDto
import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * `DEVICE_CONNECTED_PAYLOAD`, sent after every claim of a pairing or a web login. [connectionId] is the row of the
 * connections list; [name] is an agent's, [userAgent] a browser's. A [kind] this client does not know is
 * [PairingKind.UNKNOWN] and shows as a generic device. Expires 12 hours after [connectedAt].
 */
@Serializable
data class DeviceConnectedPayload(
    @Serializable(with = UuidSerializer::class) val connectionId: Uuid,
    val kind: PairingKind,
    val name: String?,
    val userAgent: String?,
    @Serializable(with = WireInstantSerializer::class) val connectedAt: Instant,
) {
    companion object {
        const val TYPE: String = "DEVICE_CONNECTED_PAYLOAD"
    }
}

/**
 * `SPORT_AGENT_BOOKED_PAYLOAD`: the user's agent [agentName] booked [sportLessons] (one lesson). Nothing to book
 * on the phone; the message expires at the end of the lesson.
 */
@Serializable
data class SportAgentBookedPayload(
    val sportLessons: List<SportLessonDto>,
    val agentName: String,
) {
    companion object {
        const val TYPE: String = "SPORT_AGENT_BOOKED_PAYLOAD"
    }
}
