package dev.alllexey.itmowidgets.core.session

import dev.alllexey.itmowidgets.client.device.DeviceApi
import dev.alllexey.itmowidgets.client.device.DevicePlatform
import dev.alllexey.itmowidgets.client.device.RegisterDeviceRequest
import dev.alllexey.itmowidgets.client.device.UnregisterDeviceRequest
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.storage.UtilityStorage
import kotlinx.coroutines.withContext

/**
 * The push registration of this installation through Core 2.0. It names the platform and leaves `alertsAllowed`
 * absent: Backend reads that as allowed, which is what every Android row meant before 2.3. Whether the user allowed
 * notifications on this device never decides it.
 */
class DefaultBackendDeviceSession(
    private val gate: BackendGate,
    private val utilityStorage: UtilityStorage,
    private val devices: DeviceApi,
    private val deviceName: String,
    private val currentUser: CurrentUserProvider,
    private val demo: DemoMode,
    private val dispatchers: AppDispatchers
) : BackendDeviceSession {

    override suspend fun registerCurrentDevice() {
        if (demo.isActive() || !gate.mayCallBackend()) return
        val ownerIsu = currentUser.getCurrentUser()?.isu?.takeIf { it > 0 } ?: return
        val fcmToken = utilityStorage.getFirebaseToken()?.trim()?.takeIf(String::isNotEmpty)
            ?: return

        withContext(dispatchers.io) {
            devices.register(
                RegisterDeviceRequest(
                    fcmToken = fcmToken,
                    deviceName = deviceName,
                    platform = DevicePlatform.ANDROID
                )
            )
            utilityStorage.setRegisteredFirebaseToken(fcmToken, ownerIsu)
        }
    }

    override suspend fun unregisterCurrentDevice() {
        if (demo.isActive() || !gate.mayCallBackend()) return
        val fcmToken = utilityStorage.getFirebaseToken()?.trim()?.takeIf(String::isNotEmpty)
            ?: return

        withContext(dispatchers.io) {
            devices.unregisterCurrent(UnregisterDeviceRequest(fcmToken))
            utilityStorage.setRegisteredFirebaseToken(null)
        }
    }
}
