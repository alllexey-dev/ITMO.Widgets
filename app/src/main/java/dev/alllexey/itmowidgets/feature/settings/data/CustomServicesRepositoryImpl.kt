package dev.alllexey.itmowidgets.feature.settings.data

import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.diagnostics.AppDiagnostics
import dev.alllexey.itmowidgets.core.notification.FcmTokenSync
import dev.alllexey.itmowidgets.core.session.BackendDeviceSession
import dev.alllexey.itmowidgets.core.session.BackendIdentitySync
import kotlinx.coroutines.CancellationException
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

class CustomServicesRepositoryImpl @Inject constructor(
    private val settings: AppSettingsStorage,
    private val identitySync: BackendIdentitySync,
    private val tokenSync: FcmTokenSync,
    private val devices: BackendDeviceSession,
    private val diagnostics: AppDiagnostics,
    private val demo: DemoMode
) : CustomServicesRepository {

    /** The demo session reads as connected, so the social screens show its data; the stored choice is kept. */
    override fun observeEnabled(): Flow<Boolean> {
        return combine(demo.observeActive(), settings.observeCustomServicesEnabled()) { demo, enabled -> demo || enabled }
            .distinctUntilChanged()
    }

    override suspend fun isEnabled(): Boolean {
        return demo.isActive() || settings.getCustomServicesEnabled()
    }

    override suspend fun isChangeable(): Boolean = !demo.isActive()

    override suspend fun setEnabled(enabled: Boolean) {
        if (demo.isActive()) return
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
            diagnostics.warn("CustomServices", "Device session sync failed", error)
        }
    }
}
