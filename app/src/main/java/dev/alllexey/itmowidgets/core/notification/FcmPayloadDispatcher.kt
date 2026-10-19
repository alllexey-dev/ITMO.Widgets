package dev.alllexey.itmowidgets.core.notification

import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.client.push.FcmDecoder
import dev.alllexey.itmowidgets.core.diagnostics.AppDiagnostics
import dev.alllexey.itmowidgets.core.storage.PushCategoryPreferences
import kotlinx.coroutines.CancellationException

/**
 * Routes Backend's data message to the handler of its `type` through Core 2.0's envelope. An unknown type is
 * recorded and dropped; a message that is not an envelope (not JSON, a JSON `null`, no `type`, a `payload` that is
 * not an object) is dropped with the fact recorded. A handler of a category the user turned off still runs (a sport
 * push still books) but posts nothing.
 */
class FcmPayloadDispatcher(
    handlers: List<FcmPayloadHandler>,
    private val notifier: AppNotifier,
    private val categories: PushCategoryPreferences,
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
            val posting = if (categories.isEnabled(handler.channel)) notifier else MutedNotifier(notifier)
            handler.handle(envelope.payload, posting)
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

/** Shows nothing; removing what is already shown still works. */
private class MutedNotifier(private val delegate: AppNotifier) : AppNotifier by delegate {
    override fun show(notification: AppNotification) = Unit
}
