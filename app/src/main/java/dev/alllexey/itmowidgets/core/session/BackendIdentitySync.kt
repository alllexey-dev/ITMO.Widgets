package dev.alllexey.itmowidgets.core.session

import android.content.Context
import api.myitmo.MyItmo
import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.diagnostics.AppDiagnostics
import dev.alllexey.itmowidgets.core.model.IdTokenRequest
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface BackendIdentitySync {

    /**
     * Publishes the ITMO.ID identity to Backend. Returns false only when an upload
     * was due and failed; a failed upload is retried by [IdentitySyncWork] unless
     * [scheduleRetry] is false, which the worker itself uses.
     */
    suspend fun sync(scheduleRetry: Boolean = true): Boolean
}

class DefaultBackendIdentitySync(
    private val context: Context,
    private val settings: AppSettingsStorage,
    private val myItmo: MyItmo,
    private val widgetsApi: ItmoWidgetsApi,
    private val diagnostics: AppDiagnostics
) : BackendIdentitySync {

    override suspend fun sync(scheduleRetry: Boolean): Boolean {
        if (!settings.getCustomServicesEnabled()) return true

        return try {
            withContext(Dispatchers.IO) {
                val idToken = myItmo.validTokens?.idToken
                if (idToken == null) {
                    diagnostics.warn(TAG, "No id token in storage; identity not published")
                    return@withContext true
                }
                val response = widgetsApi.updateIdTokenData(IdTokenRequest(idToken))
                check(response.success) { "Backend rejected the identity update" }
                true
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            // Best effort: a stale profile must never block the application.
            diagnostics.warn(TAG, "Failed to publish identity to backend", error)
            if (scheduleRetry) IdentitySyncWork.schedule(context)
            false
        }
    }

    private companion object {
        const val TAG = "BackendIdentitySync"
    }
}
