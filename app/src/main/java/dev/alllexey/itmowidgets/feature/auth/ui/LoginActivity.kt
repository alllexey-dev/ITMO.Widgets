package dev.alllexey.itmowidgets.feature.auth.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.databinding.ActivityLoginBinding
import dev.alllexey.itmowidgets.feature.auth.domain.ItmoAuthUrlPolicy
import dev.alllexey.itmowidgets.feature.auth.presentation.InteractiveLoginEvent
import dev.alllexey.itmowidgets.feature.auth.presentation.InteractiveLoginUiState
import dev.alllexey.itmowidgets.feature.auth.presentation.InteractiveLoginViewModel
import dev.alllexey.itmowidgets.core.ui.resolve
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: InteractiveLoginViewModel by viewModels()
    private val authBridge = ItmoAuthBridge()
    private val interceptorScript by lazy {
        assets.open(TOKEN_INTERCEPTOR_ASSET).bufferedReader().use { it.readText() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.loginRoot) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = bars.left,
                top = bars.top,
                right = bars.right,
                bottom = bars.bottom
            )
            insets
        }

        binding.loginToolbar.setNavigationOnClickListener { finish() }
        binding.loginSwipeRefresh.setOnRefreshListener { binding.loginWebView.reload() }
        binding.loginSwipeRefresh.setOnChildScrollUpCallback { _, _ ->
            binding.loginWebView.scrollY > 0
        }
        binding.loginRetryButton.setOnClickListener {
            viewModel.clearError()
            loadCleanLogin()
        }

        configureWebView(binding.loginWebView)
        observeViewModel()
        if (savedInstanceState == null) {
            loadCleanLogin()
        }
    }

    override fun onDestroy() {
        binding.loginWebView.apply {
            removeJavascriptInterface(BRIDGE_NAME)
            stopLoading()
            loadUrl("about:blank")
            clearHistory()
            removeAllViews()
            destroy()
        }
        super.onDestroy()
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
        android.webkit.CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, false)
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url
                if (ItmoAuthUrlPolicy.isAllowed(url.toString())) return false
                openExternal(url)
                return true
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                val uri = url?.let(Uri::parse)
                if (uri == null || !ItmoAuthUrlPolicy.isAllowed(uri.toString())) {
                    view.stopLoading()
                    showBrowserError()
                    return
                }
                showPageLoading(true)
                if (ItmoAuthUrlPolicy.isTokenCallback(uri.toString())) {
                    view.evaluateJavascript(interceptorScript, null)
                }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                showPageLoading(false)
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame) showBrowserError()
            }
        }
    }

    private fun observeViewModel() {
        viewModel.uiState
            .flowWithLifecycle(lifecycle)
            .onEach(::render)
            .launchIn(lifecycleScope)
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
        binding.loginErrorContainer.isVisible = false
        binding.loginWebView.isVisible = true
        showPageLoading(true)
        binding.loginWebView.clearHistory()
        binding.loginWebView.clearCache(true)
        WebStorage.getInstance().deleteAllData()
        android.webkit.CookieManager.getInstance().removeAllCookies {
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                android.webkit.CookieManager.getInstance().flush()
                binding.loginWebView.loadUrl(ItmoAuthUrlPolicy.LOGIN_URL)
            }
        }
    }

    private fun render(state: InteractiveLoginUiState) {
        binding.loginCompletingProgress.isVisible = state.completingLogin
        state.error?.let { error ->
            binding.loginErrorText.text = error.resolve(this)
            binding.loginErrorContainer.isVisible = true
            binding.loginWebView.isVisible = false
        }
    }

    private fun showPageLoading(loading: Boolean) {
        binding.loginSwipeRefresh.isRefreshing = loading
    }

    private fun showBrowserError() {
        showPageLoading(false)
        binding.loginWebView.isVisible = false
        binding.loginErrorContainer.isVisible = true
    }

    private fun openExternal(uri: Uri) {
        if (uri.scheme != HTTPS_SCHEME) return
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }

    private inner class ItmoAuthBridge {

        @JavascriptInterface
        fun postTokens(tokenResponseJson: String) {
            runOnUiThread {
                val currentUri = binding.loginWebView.url?.let(Uri::parse)
                if (
                    currentUri != null &&
                    ItmoAuthUrlPolicy.isTokenCallback(currentUri.toString())
                ) {
                    viewModel.completeLogin(tokenResponseJson)
                }
            }
        }
    }

    private companion object {
        const val HTTPS_SCHEME = "https"
        const val BRIDGE_NAME = "ItmoAuthBridge"
        const val TOKEN_INTERCEPTOR_ASSET = "token_refresh_interceptor.js"
    }
}
