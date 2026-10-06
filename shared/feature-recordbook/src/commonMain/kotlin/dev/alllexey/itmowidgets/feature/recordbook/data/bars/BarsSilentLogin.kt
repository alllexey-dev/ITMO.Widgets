package dev.alllexey.itmowidgets.feature.recordbook.data.bars

/**
 * Re-issues a BARS authorization code from the ITMO.ID session the app's WebView already holds. The platform supplies
 * it (Android: the headless `BarsWebSilentLogin`).
 */
interface BarsSilentLogin {
    /** Null when ITMO.ID wants the user (session ended) or the flow did not finish in time. */
    suspend fun authorizationCode(state: String): String?
}
