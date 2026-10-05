package dev.alllexey.itmowidgets.client.push

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Backend's FCM data message: the JSON string of the `data` key, `{"type": "<TYPE>", "payload": {...}}`
 * (`FcmTypedWrapper`). Both keys are required; other keys are ignored. [type] may be one this client does not know:
 * [FcmDecoder.envelope] still returns it and the app logs and drops the message.
 *
 * The FCM message also carries `recipient_isu` beside `data`; checking it against the signed-in user, the size
 * guard and the custom-services opt-in stay in the app.
 */
@Serializable
data class FcmEnvelope(
    val type: String,
    val payload: JsonObject,
)
