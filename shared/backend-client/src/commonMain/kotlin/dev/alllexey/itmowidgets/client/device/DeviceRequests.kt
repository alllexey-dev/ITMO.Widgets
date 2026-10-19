package dev.alllexey.itmowidgets.client.device

import dev.alllexey.itmowidgets.client.json.StrictEnumSerializer
import kotlinx.serialization.Serializable

/**
 * One installation's push registration. [fcmToken] keeps the name released clients send (also for an APNs-backed
 * Firebase token on iOS). The optional fields are new in 2.3 and omitted when `null`:
 * absent means [DevicePlatform.ANDROID], alerts allowed, an unknown app version and no muted category.
 * `alertsAllowed = false` tells Backend that this device shows no alerts, so it does not spend sport attempts on it.
 * [mutedPushCategories] are the push categories the user switched off on this device (`SPORT`, `FRIENDS`,
 * `CONNECTIONS`, the channel ids in upper case): Backend skips this device for them (the sport queue pushes excepted).
 */
@Serializable
data class RegisterDeviceRequest(
    val fcmToken: String,
    val deviceName: String,
    val platform: DevicePlatform? = null,
    val alertsAllowed: Boolean? = null,
    val appVersion: String? = null,
    val mutedPushCategories: List<String>? = null,
) {
    override fun toString(): String =
        "RegisterDeviceRequest(fcmToken=<redacted>, deviceName=$deviceName, platform=$platform, " +
            "alertsAllowed=$alertsAllowed, appVersion=$appVersion, mutedPushCategories=$mutedPushCategories)"
}

/** The push registration to detach from the authenticated user, as on sign-out. */
@Serializable
data class UnregisterDeviceRequest(val fcmToken: String) {
    override fun toString(): String = "UnregisterDeviceRequest(fcmToken=<redacted>)"
}

/**
 * The client platform of a device registration and of the advertised versions (`GET /api/app/version-info`).
 * Request-only, so strict; the values are case-sensitive on the wire.
 */
@Serializable(with = DevicePlatformSerializer::class)
enum class DevicePlatform { ANDROID, IOS }

internal object DevicePlatformSerializer :
    StrictEnumSerializer<DevicePlatform>("DevicePlatform", DevicePlatform.entries)
