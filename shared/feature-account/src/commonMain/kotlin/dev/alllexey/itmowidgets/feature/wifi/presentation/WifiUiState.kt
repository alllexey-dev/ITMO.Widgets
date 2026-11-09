package dev.alllexey.itmowidgets.feature.wifi.presentation

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.feature.wifi.domain.ItmoWifi
import dev.alllexey.itmowidgets.feature.wifi.domain.WifiCredentials

/** The Wi-Fi sheet: the network, where reading ISU stands and its credentials (the password hidden until shown). */
data class WifiUiState(
    val passwordShown: Boolean = false,
    val status: WifiStatus = WifiStatus.Loading,
) {
    val network: String get() = ItmoWifi.SSID
    val serverDomain: String get() = ItmoWifi.SERVER_DOMAIN

    /** The credentials of this read of ISU; none while it is in progress or after it failed. */
    val credentials: WifiCredentials? get() = (status as? WifiStatus.Ready)?.credentials
}

sealed interface WifiStatus {
    /** ISU is being read. */
    data object Loading : WifiStatus

    /** ISU answered with the credentials. */
    data class Ready(val credentials: WifiCredentials) : WifiStatus

    /** ISU has no Wi-Fi password: it is issued on ISU's page. */
    data object NoPassword : WifiStatus

    /** The phone has no ITMO.ID session for ISU; `Войти через ITMO.ID` signs in and the sheet starts again. */
    data object SignInRequired : WifiStatus

    /** Reading ISU failed; a retry reads it again. */
    data class Failed(val error: AppError) : WifiStatus
}
