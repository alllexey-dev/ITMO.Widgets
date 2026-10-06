package dev.alllexey.itmowidgets.feature.web.ui

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.annotation.VisibleForTesting
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.ui.color
import dev.alllexey.itmowidgets.core.ui.navigation.closeScreen
import dev.alllexey.itmowidgets.designsystem.host.itmoComposeView
import dev.alllexey.itmowidgets.feature.web.domain.MyItmoWebPolicy
import kotlinx.coroutines.launch

/**
 * The overlay destination `my_itmo_web`: `MyItmoWebScreen` from `:shared:feature-account` around one WebView per view,
 * built in the `AndroidView` factory and destroyed when the composition releases it. The WebView is the state, so this
 * Fragment derives the screen's [MyItmoWebState] from its callbacks and keeps no ViewModel.
 *
 * Official browser session only: no native token injection, JavaScript bridge or console logging.
 */
open class MyItmoWebFragment : Fragment() {
    private var web: WebView? = null
    private var browserState: Bundle? = null
    private var browserBack: OnBackPressedCallback? = null
    private var failed = false
    private var lastTrustedUrl = MyItmoWebPolicy.HOME_URL
    private var screenState by mutableStateOf(MyItmoWebState.Loading)
    private val snackbars = SnackbarHostState()

    /** The page's WebView while the view shows it, for instrumented tests. */
    @get:VisibleForTesting
    val browser: WebView? get() = web

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        itmoComposeView {
            MyItmoWebScreen(
                state = screenState,
                onClose = { browserBack?.isEnabled = false; closeScreen() },
                onReload = ::loadPage,
                onOpenExternal = { openExternal(MyItmoWebPolicy.HOME_URL) },
                onRetry = ::loadPage,
                browser = { modifier ->
                    AndroidView(
                        factory = ::createBrowser,
                        modifier = modifier,
                        onRelease = ::releaseBrowser,
                        // The error page covers the slot, but only a hidden view keeps its touches and TalkBack away.
                        update = { it.isVisible = screenState != MyItmoWebState.Failed },
                    )
                },
                snackbarHostState = snackbars,
            )
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val page = web
                if (page != null && page.canGoBack()) {
                    failed = false
                    page.goBack()
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        }
        browserBack = callback
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)
        lastTrustedUrl = savedInstanceState?.getString(STATE_URL)?.takeIf(MyItmoWebPolicy::isInternal) ?: lastTrustedUrl
        failed = savedInstanceState?.getBoolean(STATE_ERROR) ?: failed
        savedInstanceState?.getBundle(STATE_BROWSER)?.let { browserState = it }
        // Set before the first composition, so creating the browser changes no state the screen has already read.
        screenState = if (failed) MyItmoWebState.Failed else MyItmoWebState.Loading
    }

    private fun createBrowser(context: Context): WebView = WebView(context).also { page ->
        web = page
        configureBrowser(page)
        onBrowserCreated(page)
        val saved = browserState
        if (!failed && (saved == null || page.restoreState(saved) == null)) loadPage()
    }

    private fun loadPage() {
        val page = web ?: return
        failed = false
        screenState = MyItmoWebState.Loading
        if (page.url == lastTrustedUrl) page.reload()
        else page.loadUrl(lastTrustedUrl)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureBrowser(page: WebView) = with(page) {
        setBackgroundColor(requireContext().color.surface)
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(this@with, false)
        }
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                interceptRequest(request)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                when (MyItmoWebPolicy.navigation(request.url.toString(), request.isForMainFrame, request.hasGesture())) {
                    MyItmoWebPolicy.Navigation.INTERNAL -> false
                    MyItmoWebPolicy.Navigation.EXTERNAL -> { openExternal(request.url.toString()); true }
                    MyItmoWebPolicy.Navigation.BLOCKED -> { if (request.isForMainFrame) showError(); true }
                }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                if (web !== view) return
                if (url == null || !MyItmoWebPolicy.isInternal(url)) {
                    view.stopLoading()
                    showError()
                    return
                }
                lastTrustedUrl = url
                // WebView can deliver HTTP failure before onPageStarted. Only an explicit
                // retry or Back clears that failure; a late callback must not hide the error.
                if (!failed) screenState = MyItmoWebState.Loading
            }

            override fun onPageFinished(view: WebView, url: String?) {
                if (web !== view || failed) return
                screenState = MyItmoWebState.Shown
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (web === view && request.isForMainFrame) showError()
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                if (web === view && request.isForMainFrame) showError()
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                if (web === view) showError()
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean = true

            override fun onProgressChanged(view: WebView, newProgress: Int) {
                if (web === view && !failed) {
                    screenState = if (newProgress < 100) MyItmoWebState.Loading else MyItmoWebState.Shown
                }
            }
        }
    }

    /** Debug hosts adjust the browser before its first load. */
    protected open fun onBrowserCreated(page: WebView) = Unit

    /** Debug hosts replace transport with synthetic HTML while retaining the real browser lifecycle. */
    protected open fun interceptRequest(request: WebResourceRequest): WebResourceResponse? = null

    private fun showError() {
        failed = true
        screenState = MyItmoWebState.Failed
    }

    private fun openExternal(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: ActivityNotFoundException) {
            if (view == null) return
            viewLifecycleOwner.lifecycleScope.launch {
                snackbars.showSnackbar(getString(R.string.link_open_failed), duration = SnackbarDuration.Long)
            }
        }
    }

    override fun onResume() { super.onResume(); web?.onResume() }
    override fun onPause() { web?.onPause(); super.onPause() }

    override fun onSaveInstanceState(outState: Bundle) {
        web?.let { page -> browserState = Bundle().also { page.saveState(it) } }
        outState.putBundle(STATE_BROWSER, browserState)
        outState.putString(STATE_URL, lastTrustedUrl)
        outState.putBoolean(STATE_ERROR, failed)
        super.onSaveInstanceState(outState)
    }

    /**
     * Saves the history and destroys [page] once: when the composition disposes the slot (the view lifecycle's
     * ON_DESTROY, before [onDestroyView]), or in [onDestroyView] if it has not. Never on a recomposition.
     */
    private fun releaseBrowser(page: WebView) {
        if (web !== page) return
        web = null
        browserState = Bundle().also { page.saveState(it) }
        page.stopLoading()
        page.webViewClient = WebViewClient()
        page.webChromeClient = null
        (page.parent as? ViewGroup)?.removeView(page)
        page.removeAllViews()
        page.destroy()
    }

    override fun onDestroyView() {
        web?.let(::releaseBrowser)
        browserBack?.remove()
        browserBack = null
        super.onDestroyView()
    }

    private companion object {
        const val STATE_BROWSER = "my_itmo_browser"
        const val STATE_URL = "my_itmo_url"
        const val STATE_ERROR = "my_itmo_error"
    }
}
