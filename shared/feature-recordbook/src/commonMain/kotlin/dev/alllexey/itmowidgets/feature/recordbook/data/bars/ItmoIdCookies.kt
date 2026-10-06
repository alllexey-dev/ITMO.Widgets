package dev.alllexey.itmowidgets.feature.recordbook.data.bars

/**
 * The ITMO.ID cookies the app's WebView holds, read and written for one URL at a time. The platform supplies it
 * (Android: `WebViewItmoIdCookies` over `CookieManager`); cookies are never logged, copied to files or kept in memory
 * beyond one request.
 */
interface ItmoIdCookies {
    /** The `Cookie` header the WebView would send to [url]; null or blank when it has none. */
    suspend fun cookieHeader(url: String): String?

    /** Saves a response's `Set-Cookie` values for [url], as the WebView would after loading it. */
    suspend fun store(url: String, setCookies: List<String>)
}
