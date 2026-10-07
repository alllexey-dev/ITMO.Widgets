package dev.alllexey.itmowidgets.core.notification

import dev.alllexey.itmowidgets.client.device.DevicePlatform

/**
 * This installation as Backend's device registry sees it (`RegisterDeviceRequest`); each platform supplies its own.
 * [PushDeviceRegistration] reads it on every registration.
 */
interface PushDevice {
    val platform: DevicePlatform

    /** A model name without personal data ("Apple iPhone"), never the name the user gave the device. */
    val name: String

    /** The marketing version of the app, `null` when the bundle names none. */
    val appVersion: String?

    /** The push token, `null` while the installation has none (before the APNs and FCM registration). */
    suspend fun token(): String?

    /** Whether the user lets this installation show alerts now; Backend spends no sport attempt on a silent one. */
    suspend fun alertsAllowed(): Boolean
}
