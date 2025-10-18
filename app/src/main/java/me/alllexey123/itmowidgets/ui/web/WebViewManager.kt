package me.alllexey123.itmowidgets.ui.web

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import api.myitmo.storage.Storage
import me.alllexey123.itmowidgets.ItmoWidgetsApp
import java.io.IOException

class WebViewManager(
    private val context: Context,
    private val webView: WebView,
    private val swipeRefreshLayout: SwipeRefreshLayout,
    private val listener: WebViewListener,
    private val forceStorageCookies: Boolean,
) {

    private val siteUrl = "https://my.itmo.ru/"

    init {
        setupWebView()
    }

    private fun setupWebView() {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.addJavascriptInterface(WebAppInterface(), "AndroidApp")

        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                swipeRefreshLayout.isRefreshing = true
                injectInterceptorScript(view)
                if (forceStorageCookies) {
                    val appContainer = (context.applicationContext as ItmoWidgetsApp).appContainer
                    setAuthCookies(appContainer.storage)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                swipeRefreshLayout.isRefreshing = false
                if (view != null) {
                    injectCustomCss(view)
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                if (consoleMessage != null) {
                    Log.d(
                        TAG, "${consoleMessage.message()} -- From line " +
                                "${consoleMessage.lineNumber()} of ${consoleMessage.sourceId()}"
                    )
                }
                return true
            }
        }
    }

    fun loadUrlWithCleanState() {
        webView.clearHistory()
        webView.clearFormData()
        webView.clearCache(true)
        WebStorage.getInstance().deleteAllData()

        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies { didClear ->
            Log.d(TAG, "All cookies removed: $didClear. Loading clean session...")
            webView.loadUrl(siteUrl)
        }
        cookieManager.flush()
    }

    fun loadUrlWithAuth(storage: Storage) {
        setAuthCookies(storage)
        Log.d(TAG, "Auth cookies set. Loading authenticated session...")
        webView.loadUrl(siteUrl)
    }

    private fun setAuthCookies(storage: Storage) {
        val accessToken = storage.accessToken
        val accessTokenExpiration = storage.accessExpiresAt - System.currentTimeMillis()
        val refreshToken = storage.refreshToken
        val refreshTokenExpiration = storage.refreshExpiresAt
        val idToken = storage.idToken
        val idTokenExpiration = accessTokenExpiration

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.removeAllCookies(null)

        val authStrategyCookie = "auth.strategy=itmoId; domain=my.itmo.ru; path=/;"
        cookieManager.setCookie(siteUrl, authStrategyCookie)

        if (accessToken != null) {
            val accessTokenCookie = "auth._token.itmoId=Bearer%20$accessToken; domain=my.itmo.ru; path=/;"
            cookieManager.setCookie(siteUrl, accessTokenCookie)
            val accessExpireCookie = "auth._token_expiration.itmoId=$accessTokenExpiration; domain=my.itmo.ru; path=/;"
            cookieManager.setCookie(siteUrl, accessExpireCookie)
        }

        if (idToken != null) {
            val idTokenCookie = "auth._id_token.itmoId=$idToken; domain=my.itmo.ru; path=/;"
            cookieManager.setCookie(siteUrl, idTokenCookie)
            val idExpireCookie = "auth._id_token_expiration.itmoId=$idTokenExpiration; domain=my.itmo.ru; path=/;"
            cookieManager.setCookie(siteUrl, idExpireCookie)
        }

        if (refreshToken != null) {
            val refreshTokenCookie = "auth._refresh_token.itmoId=$refreshToken; domain=my.itmo.ru; path=/;"
            cookieManager.setCookie(siteUrl, refreshTokenCookie)
            val refreshExpireCookie = "auth._refresh_token_expiration.itmoId=$refreshTokenExpiration; domain=my.itmo.ru; path=/;"
            cookieManager.setCookie(siteUrl, refreshExpireCookie)
        }

        cookieManager.flush()
    }

    private fun injectCustomCss(view: WebView) {
        try {
            val inputStream = context.assets.open("custom_style.css")
            val buffer = inputStream.readBytes()
            inputStream.close()

            val encodedCss = Base64.encodeToString(buffer, Base64.NO_WRAP)

            val js = """
            (function() {
                var parent = document.getElementsByTagName('head').item(0);
                var style = document.createElement('style');
                style.type = 'text/css';
                style.innerHTML = window.atob('$encodedCss');
                parent.appendChild(style);
            })()
            """.trimIndent()

            view.evaluateJavascript(js, null)
            Log.d(TAG, "Custom CSS injected successfully.")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to inject custom CSS", e)
        }
    }

    private fun injectInterceptorScript(webView: WebView) {
        try {
            val inputStream = context.assets.open("token_refresh_interceptor.js")
            val script = inputStream.bufferedReader().use { it.readText() }
            webView.evaluateJavascript(script, null)
            Log.d(TAG, "Interceptor script injected successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inject interceptor script", e)
        }
    }

    inner class WebAppInterface {
        @JavascriptInterface
        fun postTokens(tokensResponseString: String) {
            listener.onTokensReceived(tokensResponseString)
        }
    }

    companion object {
        private const val TAG = "WebViewManager"
    }
}