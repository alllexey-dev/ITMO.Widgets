package dev.alllexey.itmowidgets.feature.recordbook.ui

import android.app.Activity
import android.graphics.Bitmap
import android.os.Bundle
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
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
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.ui.messageRes
import dev.alllexey.itmowidgets.databinding.ActivityLoginBinding
import dev.alllexey.itmowidgets.feature.recordbook.presentation.BarsLoginViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** No JavaScript bridge or token interception: only the exact HTTPS OAuth callback is consumed. */
@AndroidEntryPoint
class BarsLoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private val viewModel: BarsLoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.loginRoot) { view, insets ->
            val edges = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.updatePadding(left = edges.left, top = edges.top, right = edges.right, bottom = edges.bottom)
            insets
        }
        binding.loginToolbar.setTitle(R.string.recordbook_bars_login)
        binding.loginToolbar.setNavigationOnClickListener { finish() }
        binding.loginSwipeRefresh.isEnabled = false
        configureBrowser()
        binding.loginRetryButton.setOnClickListener {
            viewModel.retry()
            // Retry also permits replacing a stale SSO account, without changing application tokens.
            CookieManager.getInstance().removeAllCookies {
                runOnUiThread { if (!isFinishing && !isDestroyed) loadLogin() }
            }
        }
        viewModel.uiState.flowWithLifecycle(lifecycle).onEach { state ->
            binding.loginCompletingProgress.isVisible = state.completing
            binding.loginWebView.isVisible = !state.completing && state.error == null
            binding.loginErrorContainer.isVisible = state.error != null
            state.error?.let {
                binding.loginErrorText.setText(if (it == AppError.Forbidden) R.string.recordbook_bars_wrong_account else it.messageRes())
            }
        }.launchIn(lifecycleScope)
        viewModel.events.flowWithLifecycle(lifecycle).onEach {
            setResult(Activity.RESULT_OK)
            finish()
        }.launchIn(lifecycleScope)
        if (!viewModel.uiState.value.completing) loadLogin()
    }

    @Suppress("SetJavaScriptEnabled")
    private fun configureBrowser() {
        binding.loginWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_NO_CACHE
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(binding.loginWebView, false)
        binding.loginWebView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return !viewModel.isNavigable(request.url.toString())
                return handleUrl(request.url.toString())
            }
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                if (url == null || handleUrl(url)) view.stopLoading()
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) showBrowserError()
            }
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame) showBrowserError()
            }
        }
    }

    private fun handleUrl(url: String): Boolean {
        if (viewModel.isCallback(url)) {
            viewModel.complete(url)
            return true
        }
        if (viewModel.isNavigable(url)) return false
        showBrowserError()
        return true
    }

    private fun loadLogin() {
        binding.loginErrorContainer.isVisible = false
        binding.loginWebView.isVisible = true
        binding.loginWebView.loadUrl(viewModel.loginUrl)
    }

    private fun showBrowserError() {
        binding.loginWebView.isVisible = false
        binding.loginErrorContainer.isVisible = true
        binding.loginErrorText.setText(R.string.auth_web_error)
    }

    override fun onDestroy() {
        binding.loginWebView.apply {
            stopLoading()
            webViewClient = WebViewClient()
            loadUrl("about:blank")
            clearHistory()
            removeAllViews()
            destroy()
        }
        super.onDestroy()
    }
}
