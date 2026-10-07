package dev.alllexey.itmowidgets.feature.recordbook.data.bars

import dev.alllexey.itmoapi.bars.auth.BarsLogin
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The iOS [BarsSilentLogin] (11 row 13): Android's headless `BarsWebSilentLogin` as a hidden `WKWebView` on the
 * shared WebKit store, which Swift runs ([HiddenBarsWebView]). The official authorization URL of the `bars` client
 * loads with ITMO.ID's SSO cookies; only the exact callback with the same `state` yields a code ([BarsLogin]'s checks),
 * any other page stops the flow, and a rendered ITMO.ID page means the user must sign in. Every run then copies the
 * cookies ITMO.ID may have re-issued ([ItmoIdCookieExport]). The code is never logged.
 */
class WebViewBarsSilentLogin internal constructor(
    private val web: HiddenBarsWebView,
    private val login: BarsLogin,
    private val export: ItmoIdCookieExport,
    private val dispatchers: AppDispatchers,
    private val timeout: Duration,
) : BarsSilentLogin {

    constructor(web: HiddenBarsWebView, login: BarsLogin, export: ItmoIdCookieExport, dispatchers: AppDispatchers) :
        this(web, login, export, dispatchers, TIMEOUT)

    override suspend fun authorizationCode(state: String): String? = try {
        withContext(dispatchers.main) {
            withTimeoutOrNull(timeout) { callbackUrl(login.loginUrl(state)) }
        }?.let { url -> login.extractCode(url, state) }
    } finally {
        withContext(NonCancellable) { export.run() }
    }

    private suspend fun callbackUrl(url: String): String? = suspendCancellableCoroutine { continuation ->
        val load = web.loadHiddenBarsPage(url, BarsWebNavigation(login)) { callback ->
            if (continuation.isActive) continuation.resume(callback)
        }
        continuation.invokeOnCancellation { load.cancel() }
    }

    private companion object {
        /** As Android's `BarsWebSilentLogin`. */
        val TIMEOUT = 20.seconds
    }
}
