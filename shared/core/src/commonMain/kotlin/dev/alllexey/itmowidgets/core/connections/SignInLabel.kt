package dev.alllexey.itmowidgets.core.connections

import kotlinx.coroutines.flow.Flow

/**
 * «Выглядеть как обычный браузер» of «Устройства»: whether new ITMO.ID sign-ins (the app's own and every client's)
 * carry an ordinary browser's User-Agent, so id.itmo.ru/activity shows a browser, or an honest «ITMO Widgets ... bot»
 * label. On by default; a label never changes after its sign-in.
 */
interface SignInLabel {
    fun observeBrowserLike(): Flow<Boolean>

    suspend fun isBrowserLike(): Boolean

    suspend fun setBrowserLike(browserLike: Boolean)
}

/**
 * The User-Agent of the app's own ITMO.ID page: the WebView's own [webViewUserAgent] when [browserLike], else with
 * ` ITMO Widgets bot/<version>` appended, so id.itmo.ru shows «Android, Browser: ITMO Widgets bot/2.4». [appVersion]
 * keeps only its leading `x.y.z` (ua-parser's bot rule takes no suffix).
 */
fun appSignInUserAgent(webViewUserAgent: String, appVersion: String, browserLike: Boolean): String {
    if (browserLike) return webViewUserAgent
    val version = Regex("""^\d+(\.\d+){0,2}""").find(appVersion.trim())?.value ?: "1"
    return "$webViewUserAgent ITMO Widgets bot/$version"
}
