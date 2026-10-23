package dev.alllexey.itmowidgets.core.connections

import kotlin.test.Test
import kotlin.test.assertEquals

/** The app's own ITMO.ID page: the WebView's UA, or with the honest bot suffix ua-parser reads (plan 2.2). */
class AppSignInUserAgentTest {
    private val webView = "Mozilla/5.0 (Linux; Android 16; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0 Mobile Safari/537.36"

    @Test
    fun browserLikeKeepsTheWebViewsOwnAgent() {
        assertEquals(webView, appSignInUserAgent(webView, "2.4.0", browserLike = true))
    }

    @Test
    fun theHonestLabelKeepsOnlyTheNumericVersion() {
        assertEquals("$webView ITMO Widgets bot/2.4.0", appSignInUserAgent(webView, "2.4.0-debug", browserLike = false))
        assertEquals("$webView ITMO Widgets bot/1", appSignInUserAgent(webView, "dev", browserLike = false))
    }
}
