package dev.alllexey.itmowidgets.core.session

import android.content.Context
import api.myitmo.MyItmo
import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.diagnostics.AppDiagnostics
import dev.alllexey.itmowidgets.core.model.IdTokenRequest
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.services.BackendGate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

class DefaultBackendIdentitySync(
    private val context: Context,
    private val gate: BackendGate,
    private val myItmo: MyItmo,
    private val widgetsApi: ItmoWidgetsApi,
    private val diagnostics: AppDiagnostics,
    private val demo: DemoMode,
    private val dispatchers: AppDispatchers
) : BackendIdentitySync {

    override suspend fun sync(scheduleRetry: Boolean): Boolean {
        if (demo.isActive() || !gate.mayCallBackend()) return true

        return try {
            withContext(dispatchers.io) {
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
