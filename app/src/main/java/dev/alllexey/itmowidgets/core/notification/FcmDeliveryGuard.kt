package dev.alllexey.itmowidgets.core.notification

/**
 * Delayed messages cannot cross account changes or re-enable opted-out community
 * services. The recipient ISU is the account identity; token rotation inside the
 * same account must not drop a queued message.
 */
object FcmDeliveryGuard {
    fun canDeliver(recipientIsu: Int, currentIsu: Int?, signedIn: Boolean, enabled: Boolean): Boolean =
        enabled && signedIn && recipientIsu > 0 && recipientIsu == currentIsu
}
