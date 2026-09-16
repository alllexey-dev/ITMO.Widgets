package dev.alllexey.itmowidgets.core.notification

import com.google.gson.Gson
import dev.alllexey.itmowidgets.core.diagnostics.AppDiagnostics
import dev.alllexey.itmowidgets.core.model.fcm.FcmJsonWrapper
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class FcmPayloadDispatcher @Inject constructor(
    private val gson: Gson,
    handlers: Set<@JvmSuppressWildcards FcmPayloadHandler>,
    private val diagnostics: AppDiagnostics
) {
    private val handlersByType = handlers.associateBy(FcmPayloadHandler::type).also {
        require(it.size == handlers.size) { "Duplicate FCM payload handlers" }
    }

    suspend fun dispatch(json: String) {
        try {
            if (json.isBlank() || json.toByteArray(Charsets.UTF_8).size > MAX_PAYLOAD_BYTES) return
            val wrapper = gson.fromJson(json, FcmJsonWrapper::class.java) ?: return
            val handler = handlersByType[wrapper.type]
            if (handler == null) {
                diagnostics.warn(TAG, "Unknown FCM payload type: ${wrapper.type}")
                return
            }
            // Gson's reflective adapter can supply null for a missing non-null Kotlin field.
            @Suppress("SENSELESS_COMPARISON")
            if (wrapper.payload == null || !wrapper.payload.isJsonObject) return
            handler.handle(wrapper.payload)
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
