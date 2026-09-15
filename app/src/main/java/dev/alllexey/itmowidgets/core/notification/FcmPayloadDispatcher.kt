package dev.alllexey.itmowidgets.core.notification

import android.util.Log
import com.google.gson.Gson
import dev.alllexey.itmowidgets.core.model.fcm.FcmJsonWrapper
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class FcmPayloadDispatcher @Inject constructor(
    private val gson: Gson,
    handlers: Set<@JvmSuppressWildcards FcmPayloadHandler>
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
                Log.w(TAG, "Unknown FCM payload type")
                return
            }
            // Gson's reflective adapter can supply null for a missing non-null Kotlin field.
            @Suppress("SENSELESS_COMPARISON")
            if (wrapper.payload == null || !wrapper.payload.isJsonObject) return
            handler.handle(wrapper.payload)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "FCM dispatch failed: ${error.javaClass.simpleName}")
        }
    }

    companion object {
        const val MAX_PAYLOAD_BYTES = 4096
        private const val TAG = "FcmPayloadDispatcher"
    }
}
