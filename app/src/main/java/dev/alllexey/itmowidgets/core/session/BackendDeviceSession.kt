package dev.alllexey.itmowidgets.core.session

import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.model.RegisterDeviceRequest
import dev.alllexey.itmowidgets.core.model.UnregisterDeviceRequest
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.core.storage.UtilityStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface BackendDeviceSession {

    suspend fun registerCurrentDevice()

    suspend fun unregisterCurrentDevice()
}

class DefaultBackendDeviceSession(
    private val settings: AppSettingsStorage,
    private val utilityStorage: UtilityStorage,
    private val widgetsApi: ItmoWidgetsApi,
    private val deviceName: String,
    private val currentUser: CurrentUserProvider
) : BackendDeviceSession {

    override suspend fun registerCurrentDevice() {
        if (!settings.getCustomServicesEnabled()) return
        val ownerIsu = currentUser.getCurrentUser()?.isu?.takeIf { it > 0 } ?: return
        val fcmToken = utilityStorage.getFirebaseToken()?.trim()?.takeIf(String::isNotEmpty)
            ?: return

        withContext(Dispatchers.IO) {
            val response = widgetsApi.registerDevice(
                RegisterDeviceRequest(
                    fcmToken = fcmToken,
                    deviceName = deviceName
                )
            )
            check(response.success) { "Device registration rejected" }
            utilityStorage.setRegisteredFirebaseToken(fcmToken, ownerIsu)
        }
    }

    override suspend fun unregisterCurrentDevice() {
        if (!settings.getCustomServicesEnabled()) return
        val fcmToken = utilityStorage.getFirebaseToken()?.trim()?.takeIf(String::isNotEmpty)
            ?: return

        withContext(Dispatchers.IO) {
            val response = widgetsApi.unregisterCurrentDevice(UnregisterDeviceRequest(fcmToken))
            check(response.success) { "Device unregistration rejected" }
            utilityStorage.setRegisteredFirebaseToken(null)
        }
    }
}
