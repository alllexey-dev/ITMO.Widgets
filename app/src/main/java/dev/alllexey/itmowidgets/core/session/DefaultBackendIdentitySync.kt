package dev.alllexey.itmowidgets.core.session

import android.content.Context
import dev.alllexey.itmoapi.itmoid.TokenManager
import dev.alllexey.itmoapi.itmoid.TokenStorage
import dev.alllexey.itmowidgets.client.users.IdTokenRequest
import dev.alllexey.itmowidgets.client.users.UsersApi
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.diagnostics.AppDiagnostics
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.services.BackendGate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/**
 * Publishes the id token of the MyItmoApi 2.x session through Core 2.0. [tokens] refreshes an expiring session first,
 * so the id token read back from [storage] is the one of the current access token; [tokens] stays the only writer.
 */
class DefaultBackendIdentitySync(
    private val context: Context,
    private val gate: BackendGate,
    private val tokens: TokenManager,
    private val storage: TokenStorage,
    private val users: UsersApi,
    private val diagnostics: AppDiagnostics,
    private val demo: DemoMode,
    private val dispatchers: AppDispatchers
) : BackendIdentitySync {

    override suspend fun sync(scheduleRetry: Boolean): Boolean {
        if (demo.isActive() || !gate.mayCallBackend()) return true

        return try {
            withContext(dispatchers.io) {
                val idToken = currentIdToken()
                if (idToken == null) {
                    diagnostics.warn(TAG, "No id token in storage; identity not published")
                    return@withContext true
                }
                users.updateIdTokenData(IdTokenRequest(idToken))
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

    private suspend fun currentIdToken(): String? {
        if (tokens.isRefreshTokenExpired()) return null
        tokens.validAccessToken()
        return storage.read()?.idToken
    }

    private companion object {
        const val TAG = "BackendIdentitySync"
    }
}
