package dev.alllexey.itmowidgets.core.notification

import dev.alllexey.itmowidgets.core.diagnostics.AppDiagnostics
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject

class FcmPayloadDispatcher @Inject constructor(
    handlers: Set<@JvmSuppressWildcards FcmPayloadHandler>,
    private val diagnostics: AppDiagnostics
) {
    private val handlersByType = handlers.associateBy(FcmPayloadHandler::type).also {
        require(it.size == handlers.size) { "Duplicate FCM payload handlers" }
    }

    suspend fun dispatch(json: String) {
        try {
            if (json.isBlank() || json.encodeToByteArray().size > MAX_PAYLOAD_BYTES) return
            val envelope = try {
                decodeEnvelope(json)
            } catch (_: SerializationException) {
                // kotlinx puts the input into its message; the payload names people, so only the fact is recorded.
                diagnostics.warn(TAG, "FCM dispatch failed: malformed message")
                return
            } ?: return
            val handler = handlersByType[envelope.type]
            if (handler == null) {
                diagnostics.warn(TAG, "Unknown FCM payload type: ${envelope.type}")
                return
            }
            val payload = envelope.payload as? JsonObject ?: return
            handler.handle(payload)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            diagnostics.warn(TAG, "FCM dispatch failed", error)
        }
    }

    /** Null for a JSON null, which 2.2 read as no message at all. */
    private fun decodeEnvelope(json: String): FcmEnvelope? {
        val element = ENVELOPE_JSON.parseToJsonElement(json)
        if (element is JsonNull) return null
        return ENVELOPE_JSON.decodeFromJsonElement(FcmEnvelope.serializer(), element)
    }

    /** Backend's `{"type": ..., "payload": {...}}`; a missing type reports as unknown, a missing payload is ignored. */
    @Serializable
    private class FcmEnvelope(val type: String? = null, val payload: JsonElement? = null)

    companion object {
        const val MAX_PAYLOAD_BYTES = 4096
        private const val TAG = "FcmPayloadDispatcher"
        private val ENVELOPE_JSON = Json { ignoreUnknownKeys = true }
    }
}
