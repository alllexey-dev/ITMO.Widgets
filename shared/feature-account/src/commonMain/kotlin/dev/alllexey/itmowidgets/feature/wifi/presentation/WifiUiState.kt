package dev.alllexey.itmowidgets.feature.wifi.presentation

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.feature.wifi.domain.ItmoWifi
import dev.alllexey.itmowidgets.feature.wifi.domain.WifiCredentials

/** The Wi-Fi sheet: the network, the credentials (the password hidden until shown) and where reading ISU stands. */
data class WifiUiState(
    val credentials: WifiCredentials? = null,
    val passwordShown: Boolean = false,
    val status: WifiStatus = WifiStatus.Loading,
) {
    val network: String get() = ItmoWifi.SSID
    val serverDomain: String get() = ItmoWifi.SERVER_DOMAIN
}

sealed interface WifiStatus {
    /** Nothing stored yet, ISU is being read. */
    data object Loading : WifiStatus

    /** The credentials are on screen; [refreshing] while ISU is read again. */
    data class Ready(val refreshing: Boolean = false) : WifiStatus

    /** ISU has no Wi-Fi password: it is issued on ISU's page. */
    data object NoPassword : WifiStatus

    /** ISU needs the user's sign-in, which the app cannot do; the page opens in the browser. */
    data object SignInRequired : WifiStatus

    /** Reading failed and nothing is stored. */
    data class Failed(val error: AppError) : WifiStatus
}
