package dev.alllexey.itmowidgets.client.push

import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.client.json.BackendJson
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement

/**
 * Decodes Backend's FCM data messages without a [dev.alllexey.itmowidgets.client.BackendClient], so the Android FCM
 * service and the iOS notification service extension use it alone. Semantics are in Backend's `notifications.md`
 * contract.
 *
 * Date-times accept both writers: Backend 1.7.0 (production until gate R) writes FCM with Gson as
 * `OffsetDateTime.toString()`, which drops `:00` seconds, and newer Backends write it with Jackson.
 *
 * Every failure is [BackendException.Contract]. Its message is fixed, but its cause comes from kotlinx and may
 * quote the input, which names people: log only the fact, never the cause's message.
 */
object FcmDecoder {

    /**
     * The envelope of the `data` string. An unknown `type` is returned, not thrown; a string that is not JSON, a
     * JSON `null`, a missing `type` or a `payload` that is not an object fails.
     */
    @Throws(BackendException::class)
    fun envelope(data: String): FcmEnvelope = decode {
        BackendJson.decodeFromString(FcmEnvelope.serializer(), data)
    }

    /** The payload of a [FriendshipEventPayload.TYPE] envelope; an unknown `event` fails. */
    @Throws(BackendException::class)
    fun friendshipEvent(payload: JsonElement): FriendshipEventPayload =
        decode(FriendshipEventPayload.serializer(), payload)

    /** The payload of a [SportFreeSignLessonsPayload.TYPE] envelope. */
    @Throws(BackendException::class)
    fun sportFreeSignLessons(payload: JsonElement): SportFreeSignLessonsPayload =
        decode(SportFreeSignLessonsPayload.serializer(), payload)

    /** The payload of a [SportAutoSignLessonsPayload.TYPE] envelope. */
    @Throws(BackendException::class)
    fun sportAutoSignLessons(payload: JsonElement): SportAutoSignLessonsPayload =
        decode(SportAutoSignLessonsPayload.serializer(), payload)

    private fun <T> decode(serializer: KSerializer<T>, payload: JsonElement): T = decode {
        BackendJson.decodeFromJsonElement(serializer, payload)
    }

    private inline fun <T> decode(block: () -> T): T =
        try {
            block()
        } catch (error: IllegalArgumentException) {
            // A SerializationException (not JSON, shape, missing field, unknown strict enum) or a model invariant.
            throw BackendException.Contract(error)
        }
}
