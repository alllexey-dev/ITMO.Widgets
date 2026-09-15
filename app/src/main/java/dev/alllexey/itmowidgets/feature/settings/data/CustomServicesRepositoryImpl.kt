package dev.alllexey.itmowidgets.feature.settings.data

import android.util.Log
import dev.alllexey.itmowidgets.core.notification.FcmTokenSync
import dev.alllexey.itmowidgets.core.session.BackendDeviceSession
import dev.alllexey.itmowidgets.core.session.BackendIdentitySync
import kotlinx.coroutines.CancellationException
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class CustomServicesRepositoryImpl @Inject constructor(
    private val settings: AppSettingsStorage,
    private val identitySync: BackendIdentitySync,
    private val tokenSync: FcmTokenSync,
    private val devices: BackendDeviceSession
) : CustomServicesRepository {

    override fun observeEnabled(): Flow<Boolean> {
        return settings.observeCustomServicesEnabled()
    }

    override suspend fun isEnabled(): Boolean {
        return settings.getCustomServicesEnabled()
    }

    override suspend fun setEnabled(enabled: Boolean) {
        if (!enabled) bestEffort { devices.unregisterCurrentDevice() }
        settings.setCustomServicesEnabled(enabled)
        if (enabled) {
            // Opting in is the first moment the backend may learn who the user is;
            // without this the stored profile stays empty until the next launch.
            bestEffort { identitySync.sync() }
            bestEffort { tokenSync.sync() }
            bestEffort { devices.registerCurrentDevice() }
        }
    }

    private suspend fun bestEffort(block: suspend () -> Unit) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w("CustomServices", "Device session sync failed: ${error.javaClass.simpleName}")
        }
    }
}
