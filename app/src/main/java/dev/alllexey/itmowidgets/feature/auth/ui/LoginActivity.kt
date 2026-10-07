package dev.alllexey.itmowidgets.feature.auth.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.isVisible
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.designsystem.host.ItmoComposeHost
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.auth.domain.ItmoAuthUrlPolicy
import dev.alllexey.itmowidgets.feature.auth.presentation.InteractiveLoginEvent
import dev.alllexey.itmowidgets.feature.auth.presentation.InteractiveLoginUiState
import dev.alllexey.itmowidgets.feature.auth.presentation.InteractiveLoginViewModel
import dev.alllexey.itmowidgets.feature.auth.presentation.LoginPage
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * The ITMO.ID sign-in page, kept by name for the manifest and `ActivityRoutes`. The chrome is `LoginScreen` from
 * `:shared:feature-account`; its slot holds a pull-to-refresh `SwipeRefreshLayout` around the WebView, built in code,
 * since Compose's pull-to-refresh cannot see a WebView's scroll. The WebView's security settings, the cookie and
 * storage wipe before every sign-in page, the token bridge and the token interceptor stay here.
 */
@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    private val viewModel: InteractiveLoginViewModel by viewModel()
    private val authBridge = ItmoAuthBridge()
    private val interceptorScript by lazy {
        assets.open(TOKEN_INTERCEPTOR_ASSET).bufferedReader().use { it.readText() }
    }

    /** The sign-in browser while the page shows; null before the first composition and after [releaseBrowser]. */
    @VisibleForTesting
    var browser: WebView? = null
        private set

    /** A fresh start loads the sign-in page; a recreated activity does not, as before the port. */
    private var loadOnCreate = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        loadOnCreate = savedInstanceState == null

        setContent {
            ItmoComposeHost.locals {
                ItmoTheme {
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    LoginScreen(
                        state = state,
                        onClose = ::finish,
                        onRetry = ::retry,
                        browser = { modifier ->
                            AndroidView(
                                factory = ::createBrowser,
                                modifier = modifier,
                                onRelease = { releaseBrowser() },
                                update = { layout -> render(layout, state) },
                            )
                        },
                        modifier = Modifier.systemBarsPadding(),
                    )
                }
            }
        }
        observeEvents()
    }

    override fun onDestroy() {
        releaseBrowser()
        super.onDestroy()
    }

    private fun createBrowser(context: Context): SwipeRefreshLayout {
        val page = WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
        configureWebView(page)
        browser = page
        val layout = SwipeRefreshLayout(context).apply {
            addView(page)
            setOnRefreshListener { page.reload() }
            setOnChildScrollUpCallback { _, _ -> page.scrollY > 0 }
        }
        if (loadOnCreate) {
            loadOnCreate = false
            loadCleanLogin()
        }
        return layout
    }

    /** Destroys the browser once, from the composition's release or from [onDestroy], whichever comes first. */
    private fun releaseBrowser() {
        val page = browser ?: return
        browser = null
        page.apply {
            removeJavascriptInterface(BRIDGE_NAME)
            stopLoading()
            loadUrl("about:blank")
            clearHistory()
            removeAllViews()
            destroy()
        }
    }

    private fun render(layout: SwipeRefreshLayout, state: InteractiveLoginUiState) {
        layout.isRefreshing = state.page == LoginPage.Loading
        // The error covers the browser in Compose; the view hides itself too, since a platform view's touches and
        // accessibility bypass Compose's drawing order.
        browser?.isVisible = !state.showsError
    }

    private fun retry() {
        viewModel.retry()
        loadCleanLogin()
    }

    @Suppress("SetJavaScriptEnabled")
    private fun configureWebView(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_NO_CACHE
        }
        webView.addJavascriptInterface(authBridge, BRIDGE_NAME)
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, false)
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url
                if (ItmoAuthUrlPolicy.isNavigable(url.toString())) return false
                openExternal(url)
                return true
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                val uri = url?.let(Uri::parse)
                if (uri == null || !ItmoAuthUrlPolicy.isNavigable(uri.toString())) {
                    view.stopLoading()
                    viewModel.onMainFrameError()
                    return
                }
                viewModel.onPageStarted()
                if (ItmoAuthUrlPolicy.isTokenCallback(uri.toString())) {
                    view.evaluateJavascript(interceptorScript, null)
                }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                viewModel.onPageFinished()
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame) viewModel.onMainFrameError()
            }
        }
    }

    private fun observeEvents() {
        viewModel.events
            .flowWithLifecycle(lifecycle)
            .onEach { event ->
                if (event is InteractiveLoginEvent.Completed) {
                    setResult(Activity.RESULT_OK)
                    finish()
                }
            }
            .launchIn(lifecycleScope)
    }

    private fun loadCleanLogin() {
        val page = browser ?: return
        page.clearHistory()
        page.clearCache(true)
        WebStorage.getInstance().deleteAllData()
        CookieManager.getInstance().removeAllCookies {
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                CookieManager.getInstance().flush()
                browser?.loadUrl(ItmoAuthUrlPolicy.LOGIN_URL)
            }
        }
    }

    private fun openExternal(uri: Uri) {
        if (uri.scheme != HTTPS_SCHEME) return
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }

    private inner class ItmoAuthBridge {

        @JavascriptInterface
        fun postTokens(tokenResponseJson: String) {
            runOnUiThread {
                viewModel.onTokensPosted(browser?.url.orEmpty(), tokenResponseJson)
            }
        }
    }

    private companion object {
        const val HTTPS_SCHEME = "https"
        const val BRIDGE_NAME = "ItmoAuthBridge"
        const val TOKEN_INTERCEPTOR_ASSET = "token_refresh_interceptor.js"
    }
}
