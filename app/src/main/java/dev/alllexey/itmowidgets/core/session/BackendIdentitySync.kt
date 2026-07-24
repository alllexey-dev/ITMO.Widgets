package dev.alllexey.itmowidgets.core.session

import android.util.Log
import api.myitmo.MyItmo
import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.model.IdTokenRequest
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface BackendIdentitySync {

    suspend fun sync()
}

class DefaultBackendIdentitySync(
    private val settings: AppSettingsStorage,
    private val myItmo: MyItmo,
    private val widgetsApi: ItmoWidgetsApi
) : BackendIdentitySync {

    override suspend fun sync() {
        if (!settings.getCustomServicesEnabled()) return

        try {
            withContext(Dispatchers.IO) {
                val idToken = myItmo.validTokens?.idToken ?: return@withContext
                widgetsApi.updateIdTokenData(IdTokenRequest(idToken))
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            // Best effort: a stale profile must never block the application.
            Log.w(TAG, "Failed to publish identity to backend", error)
        }
    }

    private companion object {
        const val TAG = "BackendIdentitySync"
    }
}
