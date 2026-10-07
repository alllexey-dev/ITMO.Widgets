package dev.alllexey.itmowidgets.core.notification

import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.client.push.FcmDecoder
import dev.alllexey.itmowidgets.core.diagnostics.AppDiagnostics
import kotlinx.coroutines.CancellationException

/**
 * Routes Backend's data message to the handler of its `type` through Core 2.0's envelope. An unknown type is
 * recorded and dropped; a message that is not an envelope (not JSON, a JSON `null`, no `type`, a `payload` that is
 * not an object) is dropped with the fact recorded, as 2.2 dropped it.
 */
class FcmPayloadDispatcher(
    handlers: List<FcmPayloadHandler>,
    private val diagnostics: AppDiagnostics
) {
    private val handlersByType = handlers.associateBy(FcmPayloadHandler::type).also {
        require(it.size == handlers.size) { "Duplicate FCM payload handlers" }
    }

    suspend fun dispatch(json: String) {
        try {
            if (json.isBlank() || json.encodeToByteArray().size > MAX_PAYLOAD_BYTES) return
            val envelope = try {
                FcmDecoder.envelope(json)
            } catch (_: BackendException.Contract) {
                // The cause quotes the input, and the payload names people, so only the fact is recorded.
                diagnostics.warn(TAG, "FCM dispatch failed: malformed message")
                return
            }
            val handler = handlersByType[envelope.type]
            if (handler == null) {
                diagnostics.warn(TAG, "Unknown FCM payload type: ${envelope.type}")
                return
            }
            handler.handle(envelope.payload)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            diagnostics.warn(TAG, "FCM dispatch failed", error)
        }
    }

    companion object {
        const val MAX_PAYLOAD_BYTES = 4096
        private const val TAG = "FcmPayloadDispatcher"
    }
}
