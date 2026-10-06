package dev.alllexey.itmowidgets.feature.settings.data

import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.diagnostics.AppDiagnostics
import dev.alllexey.itmowidgets.core.notification.FcmTokenSync
import dev.alllexey.itmowidgets.core.session.BackendDeviceSession
import dev.alllexey.itmowidgets.core.session.BackendIdentitySync
import kotlinx.coroutines.CancellationException
import dev.alllexey.itmowidgets.core.storage.ServicesOptInPreferences
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import kotlinx.coroutines.flow.Flow

class CustomServicesRepositoryImpl(
    private val servicesOptIn: ServicesOptInPreferences,
    private val gate: BackendGate,
    private val identitySync: BackendIdentitySync,
    private val tokenSync: FcmTokenSync,
    private val devices: BackendDeviceSession,
    private val diagnostics: AppDiagnostics,
    private val demo: DemoMode
) : CustomServicesRepository {

    /** The demo session reads as connected, so the social screens show its data; the stored choice is kept. */
    override fun observeEnabled(): Flow<Boolean> = gate.observeConnected()

    override suspend fun isEnabled(): Boolean = gate.isConnected()

    override suspend fun isChangeable(): Boolean = !demo.isActive()

    override suspend fun setEnabled(enabled: Boolean) {
        if (demo.isActive()) return
        if (!enabled) bestEffort { devices.unregisterCurrentDevice() }
        servicesOptIn.setCustomServicesEnabled(enabled)
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
            diagnostics.warn("CustomServices", "Device session sync failed", error)
        }
    }
}
