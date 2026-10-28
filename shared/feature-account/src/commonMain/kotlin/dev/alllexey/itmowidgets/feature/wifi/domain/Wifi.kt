package dev.alllexey.itmowidgets.feature.wifi.domain

import dev.alllexey.itmowidgets.core.result.AppResult

/**
 * The university's Wi-Fi for students and staff (docs/features/wifi.md): WPA2-Enterprise, EAP-PEAP with MSCHAPv2
 * inside, the user's ISU login and the Wi-Fi password from ISU. The RADIUS server shows a public (Let's Encrypt)
 * certificate of [SERVER_DOMAIN], so a device checks it against its own CA store and the domain.
 */
object ItmoWifi {
    const val SSID = "ITMO-Corp"
    const val SERVER_DOMAIN = "wifi.it.itmo.ru"

    /** ISU's «Настройки Wi-Fi» page, where the password is shown and changed; the browser asks to sign in. */
    const val ISU_PAGE_URL = "https://isu.ifmo.ru/pls/apex/f?p=2156:6"
}

/** The login and the password of [ItmoWifi.SSID]; never part of [toString]. */
data class WifiCredentials(val login: String, val password: String) {
    override fun toString(): String = "WifiCredentials(redacted)"
}

/** What ISU says about the user's Wi-Fi account. */
sealed interface WifiAccess {
    data class Ready(val credentials: WifiCredentials) : WifiAccess

    /** ISU has no active Wi-Fi password yet; the user gets one on ISU's page. */
    data object NoPassword : WifiAccess

    /** ISU cannot be signed in without the user: the phone holds no ITMO.ID session (a refresh-token sign-in). */
    data object SignInRequired : WifiAccess
}

interface WifiRepository {
    /** The credentials read last time for the signed-in user, or the demo's; null when none were read. */
    suspend fun cached(): WifiCredentials?

    /** Reads the account from ISU and keeps the credentials for [cached]. */
    suspend fun refresh(): AppResult<WifiAccess>
}
