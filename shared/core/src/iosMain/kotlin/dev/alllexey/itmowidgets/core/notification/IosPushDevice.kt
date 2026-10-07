package dev.alllexey.itmowidgets.core.notification

import dev.alllexey.itmowidgets.client.device.DevicePlatform
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSBundle
import platform.UIKit.UIDevice
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter

/**
 * The iOS [PushDevice] of the app process. The push token comes from Swift ([updateToken]) once the app registers
 * with APNs and FCM (IO-13b); until then it is `null`, so nothing is registered. Each [alertsAllowed] reads the
 * notification settings and publishes the answer ([observeAlertsAllowed]) for the extensions' session snapshot.
 *
 * [readAlertsAllowed] is the system's answer; tests pass their own, since a test binary has no notification center.
 */
class IosPushDevice(
    override val appVersion: String?,
    private val readAlertsAllowed: suspend () -> Boolean = ::notificationSettingsAllowAlerts,
) : PushDevice {

    private val token = MutableStateFlow<String?>(null)
    private val alerts = MutableStateFlow(false)

    override val platform: DevicePlatform = DevicePlatform.IOS

    // "iPhone" or "iPad": UIDevice.name is the user's own name for the device.
    override val name: String by lazy { "Apple ${UIDevice.currentDevice.model}" }

    /** The FCM token of this installation, or `null` when it has none; blank counts as none. */
    fun updateToken(token: String?) {
        this.token.value = token?.trim()?.takeIf(String::isNotEmpty)
    }

    override suspend fun token(): String? = token.value

    override suspend fun alertsAllowed(): Boolean = readAlertsAllowed().also { alerts.value = it }

    /** The last answer of [alertsAllowed]; `false` before the first read. */
    fun observeAlertsAllowed(): StateFlow<Boolean> = alerts.asStateFlow()

    companion object {
        /** With the app's marketing version (`CFBundleShortVersionString`), as Android sends `VERSION_NAME`. */
        fun fromMainBundle(): IosPushDevice = IosPushDevice(
            appVersion = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
        )
    }
}

/** Alerts may show: authorized, provisional (quietly, in the notification centre) or ephemeral (App Clips). */
private suspend fun notificationSettingsAllowAlerts(): Boolean = suspendCancellableCoroutine { continuation ->
    UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
        val status = settings?.authorizationStatus
        continuation.resume(
            status == UNAuthorizationStatusAuthorized ||
                status == UNAuthorizationStatusProvisional ||
                status == UNAuthorizationStatusEphemeral
        )
    }
}
