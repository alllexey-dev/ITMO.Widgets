package dev.alllexey.itmowidgets.feature.weblogin.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserDescriptionTest {

    @Test fun `desktop browsers are told apart from the Chromium forks`() {
        assertEquals(BrowserDescription(Browser.CHROME, Platform.MACOS), describeUserAgent(
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"))
        assertEquals(BrowserDescription(Browser.EDGE, Platform.WINDOWS), describeUserAgent(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 Edg/128.0.0.0"))
        assertEquals(BrowserDescription(Browser.YANDEX, Platform.WINDOWS), describeUserAgent(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 YaBrowser/24.7.0.0 Safari/537.36"))
        assertEquals(BrowserDescription(Browser.OPERA, Platform.LINUX), describeUserAgent(
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36 OPR/113.0.0.0"))
        assertEquals(BrowserDescription(Browser.FIREFOX, Platform.LINUX), describeUserAgent(
            "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:130.0) Gecko/20100101 Firefox/130.0"))
        assertEquals(BrowserDescription(Browser.SAFARI, Platform.MACOS), describeUserAgent(
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.6 Safari/605.1.15"))
        assertEquals(BrowserDescription(Browser.CHROME, Platform.CHROME_OS), describeUserAgent(
            "Mozilla/5.0 (X11; CrOS x86_64 14541.0.0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"))
    }

    @Test fun `mobile systems win over the desktop words inside their strings`() {
        assertEquals(BrowserDescription(Browser.SAFARI, Platform.IOS), describeUserAgent(
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.6 Mobile/15E148 Safari/604.1"))
        assertEquals(BrowserDescription(Browser.CHROME, Platform.IPADOS), describeUserAgent(
            "Mozilla/5.0 (iPad; CPU OS 17_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/128.0.6613.98 Mobile/15E148 Safari/604.1"))
        assertEquals(BrowserDescription(Browser.SAMSUNG, Platform.ANDROID), describeUserAgent(
            "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/25.0 Chrome/121.0.0.0 Mobile Safari/537.36"))
        assertEquals(BrowserDescription(Browser.FIREFOX, Platform.IOS), describeUserAgent(
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) FxiOS/130.0 Mobile/15E148 Safari/605.1.15"))
    }

    @Test fun `unknown and missing agents stay unknown`() {
        assertEquals(BrowserDescription(null, null), describeUserAgent(null))
        assertEquals(BrowserDescription(null, null), describeUserAgent("  "))
        assertEquals(BrowserDescription(null, null), describeUserAgent("curl/8.7.1"))
        assertEquals(BrowserDescription(null, Platform.WINDOWS), describeUserAgent("SomeBot/1.0 (Windows NT 10.0)"))
    }
}
