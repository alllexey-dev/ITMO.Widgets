package dev.alllexey.itmowidgets.client.device

import dev.alllexey.itmowidgets.client.error.BackendException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Devices (routes under `/api/device`): the push token of this installation. Semantics are in Backend's
 * `notifications.md` contract.
 *
 * Both routes need an authenticated user and return Backend's confirmation text, which is not returned here. The
 * app registers on sign-in and on enabling the custom services, and unregisters on sign-out and on disabling them;
 * those holders check `DemoMode` and the opt-in, the client does not.
 */
interface DeviceApi {

    /**
     * `POST /api/device/register-device`: stores [RegisterDeviceRequest.fcmToken] for the authenticated user; a
     * token already known moves to that user. The optional fields are omitted when `null`: a Backend without them
     * (1.7.0) ignores them, a newer one applies its defaults (`ANDROID`, alerts allowed, no version).
     */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun register(request: RegisterDeviceRequest)

    /**
     * `DELETE /api/device/current` with a JSON body (the token never goes into the URL): removes the token only
     * when it belongs to the authenticated user and otherwise changes nothing. SP-15a showed that Ktor's Darwin
     * engine sends the body as OkHttp does.
     */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun unregisterCurrent(request: UnregisterDeviceRequest)
}
