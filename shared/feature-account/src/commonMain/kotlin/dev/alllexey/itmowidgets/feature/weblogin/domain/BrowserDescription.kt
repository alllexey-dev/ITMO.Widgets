package dev.alllexey.itmowidgets.feature.weblogin.domain

enum class Browser { YANDEX, EDGE, OPERA, SAMSUNG, FIREFOX, CHROME, SAFARI }

enum class Platform { WINDOWS, MACOS, LINUX, CHROME_OS, ANDROID, IOS, IPADOS }

/** What a person recognises in a User-Agent: the browser and the system it runs on, each null when unknown. */
data class BrowserDescription(val browser: Browser?, val platform: Platform?)

/**
 * Reads the common desktop and mobile User-Agent strings. Order matters: Chromium forks also
 * say `Chrome/` and `Safari/`, and iOS says `like Mac OS X`.
 */
fun describeUserAgent(userAgent: String?): BrowserDescription {
    val ua = userAgent?.trim().orEmpty()
    if (ua.isEmpty()) return BrowserDescription(null, null)
    return BrowserDescription(browserOf(ua), platformOf(ua))
}

private fun browserOf(ua: String): Browser? = when {
    "YaBrowser/" in ua || "YaSearchBrowser/" in ua -> Browser.YANDEX
    "Edg/" in ua || "EdgA/" in ua || "EdgiOS/" in ua || "Edge/" in ua -> Browser.EDGE
    "OPR/" in ua || "OPT/" in ua || "Opera" in ua -> Browser.OPERA
    "SamsungBrowser/" in ua -> Browser.SAMSUNG
    "Firefox/" in ua || "FxiOS/" in ua -> Browser.FIREFOX
    "Chrome/" in ua || "CriOS/" in ua || "Chromium/" in ua -> Browser.CHROME
    "Safari/" in ua && "Version/" in ua -> Browser.SAFARI
    else -> null
}

private fun platformOf(ua: String): Platform? = when {
    "Windows" in ua -> Platform.WINDOWS
    "iPhone" in ua || "iPod" in ua -> Platform.IOS
    "iPad" in ua -> Platform.IPADOS
    "Android" in ua -> Platform.ANDROID
    "CrOS" in ua -> Platform.CHROME_OS
    "Macintosh" in ua || "Mac OS X" in ua -> Platform.MACOS
    "Linux" in ua || "X11" in ua -> Platform.LINUX
    else -> null
}
