package dev.alllexey.itmowidgets.feature.recordbook.data.bars

import android.content.Context
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsAuthPolicy
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Re-issues a BARS authorization code from the ITMO.ID session the app's WebView already holds. */
interface BarsSilentLogin {
    /** Null when ITMO.ID wants the user (session ended) or the flow did not finish in time. */
    suspend fun authorizationCode(): String?
}

/** Headless copy of the interactive flow: same official URLs, no JavaScript bridge, no token reading. */
class BarsWebSilentLogin @Inject constructor(
    @param:ApplicationContext private val context: Context
) : BarsSilentLogin {
    @Suppress("SetJavaScriptEnabled")
    override suspend fun authorizationCode(): String? = withContext(Dispatchers.Main.immediate) {
        val state = UUID.randomUUID().toString()
        val web = WebView(context)
        try {
            withTimeoutOrNull(TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    fun finish(code: String?) { if (continuation.isActive) continuation.resume(code) }
                    fun handle(url: String): Boolean {
                        if (BarsAuthPolicy.isCallback(url)) { finish(BarsAuthPolicy.authorizationCode(url, state)); return true }
                        if (BarsAuthPolicy.isAllowedPage(url)) return false
                        finish(null)
                        return true
                    }
                    web.settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = false
                        allowContentAccess = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        cacheMode = WebSettings.LOAD_NO_CACHE
                    }
                    CookieManager.getInstance().setAcceptThirdPartyCookies(web, false)
                    web.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                            request.isForMainFrame && handle(request.url.toString())
                        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                            if (url != null && handle(url)) view.stopLoading()
                        }
                        override fun onPageFinished(view: WebView, url: String?) {
                            // A rendered ITMO.ID page is a sign-in form: only the user can continue from here.
                            if (url != null && !BarsAuthPolicy.isCallback(url)) finish(null)
                        }
                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                            if (request.isForMainFrame) finish(null)
                        }
                        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                            if (request.isForMainFrame) finish(null)
                        }
                    }
                    web.loadUrl(BarsAuthPolicy.loginUrl(state))
                }
            }
        } finally {
            web.stopLoading()
            web.webViewClient = WebViewClient()
            web.destroy()
        }
    }

    private companion object { const val TIMEOUT_MS = 20_000L }
}
