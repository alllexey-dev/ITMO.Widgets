package dev.alllexey.itmowidgets.core.debug

/**
 * Debug-only check that the BARS session renews through ITMO.ID cookies in a fresh process, without a WebView.
 * It writes only the outcome to logcat: no cookies, codes, tokens or personal data, and nothing is saved.
 */
interface BarsSessionProbe {
    fun start()
}
