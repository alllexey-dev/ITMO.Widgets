package dev.alllexey.itmowidgets.feature.recordbook.data.bars

import android.webkit.CookieManager
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import javax.inject.Inject
import kotlinx.coroutines.withContext

/** The ITMO.ID cookies the app's WebView holds, read and written for one URL at a time. */
interface ItmoIdCookies {
    /** The `Cookie` header the WebView would send to [url]; null or blank when it has none. */
    suspend fun cookieHeader(url: String): String?

    /** Saves a response's `Set-Cookie` values for [url], as the WebView would after loading it. */
    suspend fun store(url: String, setCookies: List<String>)
}

/**
 * Cookies stay inside [CookieManager]: they are never logged, copied to files or kept in memory beyond one request.
 * [CookieManager] is used on the main thread, like the WebView that owns it; `flush()` writes to disk off it.
 */
class WebViewItmoIdCookies @Inject constructor(private val dispatchers: AppDispatchers) : ItmoIdCookies {
    override suspend fun cookieHeader(url: String): String? = withContext(dispatchers.main) {
        CookieManager.getInstance().getCookie(url)
    }

    override suspend fun store(url: String, setCookies: List<String>) {
        if (setCookies.isEmpty()) return
        val manager = withContext(dispatchers.main) {
            CookieManager.getInstance().also { manager -> setCookies.forEach { manager.setCookie(url, it) } }
        }
        withContext(dispatchers.io) { manager.flush() }
    }
}
