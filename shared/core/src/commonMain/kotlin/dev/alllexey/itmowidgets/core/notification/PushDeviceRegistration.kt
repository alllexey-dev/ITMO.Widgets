package dev.alllexey.itmowidgets.core.notification

import dev.alllexey.itmowidgets.client.ClientVersion
import dev.alllexey.itmowidgets.client.device.DeviceApi
import dev.alllexey.itmowidgets.client.device.RegisterDeviceRequest
import dev.alllexey.itmowidgets.client.device.UnregisterDeviceRequest
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.session.BackendDeviceSession
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The push registration of this installation through Core 2.0 with the platform, the alerts answer and the app
 * version (`RegisterDeviceRequest`), the platform-neutral part of Android's `DefaultFcmTokenSync` and
 * `DefaultBackendDeviceSession`. iOS uses it; Android keeps its own until it moves here.
 *
 * Nothing reaches Backend in the demo, without the opt-in ([BackendGate.mayCallBackend]), without a signed-in ISU or
 * without a push token: an empty token is never registered. [sync] registers only when the token, the owner, the
 * alerts answer or the build ([version], the `X-App-Version` Backend records) differs from what Backend last
 * accepted, so it runs on every return to the foreground and registers once after an update.
 */
class PushDeviceRegistration(
    private val devices: DeviceApi,
    private val device: PushDevice,
    private val registrations: PushRegistrationPreferences,
    private val gate: BackendGate,
    private val demo: DemoMode,
    private val currentUser: CurrentUserProvider,
    private val version: ClientVersion,
) : BackendDeviceSession, FcmTokenSync {

    private val mutex = Mutex()

    override suspend fun sync() = mutex.withLock {
        val wanted = wanted() ?: return@withLock
        if (registrations.get() != wanted) register(wanted)
    }

    /** Registers whatever Backend holds, as sign-in and turning the services on ask; Backend keeps one row a token. */
    override suspend fun registerCurrentDevice() = mutex.withLock {
        val wanted = wanted() ?: return@withLock
        register(wanted)
    }

    /** Detaches the token Backend last accepted (or the current one) before sign-out or turning the services off. */
    override suspend fun unregisterCurrentDevice() = mutex.withLock {
        if (demo.isActive() || !gate.mayCallBackend()) return@withLock
        val token = registrations.get()?.token ?: device.token().orNullIfBlank() ?: return@withLock
        devices.unregisterCurrent(UnregisterDeviceRequest(token))
        registrations.set(null)
    }

    /** What Backend should hold for this installation; `null` when nothing may or can be registered. */
    private suspend fun wanted(): PushRegistration? {
        if (demo.isActive() || !gate.mayCallBackend()) return null
        val ownerIsu = currentUser.getCurrentUser()?.isu?.takeIf { it > 0 } ?: return null
        val token = device.token().orNullIfBlank() ?: return null
        return PushRegistration(token, ownerIsu, device.alertsAllowed(), version.headerValue)
    }

    private suspend fun register(registration: PushRegistration) {
        devices.register(
            RegisterDeviceRequest(
                fcmToken = registration.token,
                deviceName = device.name,
                platform = device.platform,
                alertsAllowed = registration.alertsAllowed,
                appVersion = device.appVersion,
            )
        )
        registrations.set(registration)
    }

    private fun String?.orNullIfBlank(): String? = this?.trim()?.takeIf(String::isNotEmpty)
}
