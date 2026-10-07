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
import androidx.compose.runtime.Composable
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
 * The overlay destination `my_itmo_web`: [MyItmoWebContent] over one [MyItmoBrowser], whose WebView is built in the
 * `AndroidView` factory and destroyed when the composition releases it. The WebView is the state, so the browser
 * derives the screen's [MyItmoWebState] from its callbacks and there is no ViewModel. This Fragment keeps its
 * lifecycle, Back and saved state; the Compose shell's entry hosts the same browser.
 *
 * Official browser session only: no native token injection, JavaScript bridge or console logging.
 */
open class MyItmoWebFragment : Fragment() {
    private val snackbars = SnackbarHostState()
    private val myItmo = MyItmoBrowser(
        openExternal = ::openExternal,
        onBrowserCreated = { onBrowserCreated(it) },
        interceptRequest = { interceptRequest(it) },
    )
    private var browserBack: OnBackPressedCallback? = null

    /** The page's WebView while the view shows it, for instrumented tests. */
    @get:VisibleForTesting
    val browser: WebView? get() = myItmo.page

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        itmoComposeView {
            MyItmoWebContent(
                browser = myItmo,
                onClose = { browserBack?.isEnabled = false; closeScreen() },
                snackbarHostState = snackbars,
            )
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!myItmo.goBack()) {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        }
        browserBack = callback
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)
        // Before the first composition, so creating the browser changes no state the screen has already read.
        myItmo.restore(savedInstanceState)
    }

    /** Debug hosts adjust the browser before its first load. */
    protected open fun onBrowserCreated(page: WebView) = Unit

    /** Debug hosts replace transport with synthetic HTML while retaining the real browser lifecycle. */
    protected open fun interceptRequest(request: WebResourceRequest): WebResourceResponse? = null

    private fun openExternal(url: String) {
        openExternalPage(requireContext(), url) {
            if (view == null) return@openExternalPage
            viewLifecycleOwner.lifecycleScope.launch {
                snackbars.showSnackbar(getString(R.string.link_open_failed), duration = SnackbarDuration.Long)
            }
        }
    }

    override fun onResume() { super.onResume(); myItmo.onResume() }
    override fun onPause() { myItmo.onPause(); super.onPause() }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putAll(myItmo.save())
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        myItmo.releaseShown()
        browserBack?.remove()
        browserBack = null
        super.onDestroyView()
    }
}

/**
 * `MyItmoWebScreen` over [browser]: the bar's subtitle is the host of the last official page, the slot is the
 * browser's WebView, hidden while the error page covers it. Both hosts call it (`MyItmoWebFragment` and the Compose
 * shell's `MY_ITMO_WEB` entry).
 */
@Composable
fun MyItmoWebContent(browser: MyItmoBrowser, onClose: () -> Unit, snackbarHostState: SnackbarHostState) {
    MyItmoWebScreen(
        state = browser.screenState,
        host = browser.lastTrustedUrl.toUri().host,
        onClose = onClose,
        onReload = browser::loadPage,
        onOpenExternal = browser::openHome,
        onRetry = browser::loadPage,
        browser = { modifier ->
            AndroidView(
                factory = browser::create,
                modifier = modifier,
                onRelease = browser::release,
                // The error page covers the slot, but only a hidden view keeps its touches and TalkBack away.
                update = { it.isVisible = browser.screenState != MyItmoWebState.Failed },
            )
        },
        snackbarHostState = snackbarHostState,
    )
}

/** Opens [url] in another app; [onFailed] when none can. */
fun openExternalPage(context: Context, url: String, onFailed: () -> Unit) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        onFailed()
    }
}

/**
 * One My ITMO browser: at most one WebView at a time, the last official page, the failure and the saved history, and
 * the screen state derived from the WebView's callbacks. A host creates the WebView through [create] and releases it
 * through [release], forwards resume and pause, Back through [goBack], and saves and restores through [save] and
 * [restore]. [openExternal] opens a page outside the official hosts; [onBrowserCreated] and [interceptRequest] are the
 * debug hosts' hooks.
 */
class MyItmoBrowser(
    private val openExternal: (url: String) -> Unit,
    private val onBrowserCreated: (WebView) -> Unit = {},
    private val interceptRequest: (WebResourceRequest) -> WebResourceResponse? = { null },
) {
    private var web: WebView? = null
    private var browserState: Bundle? = null
    private var failed = false

    /** The last official page the browser started; its host is the bar's subtitle, as in 2.2. */
    var lastTrustedUrl by mutableStateOf(MyItmoWebPolicy.HOME_URL)
        private set
    var screenState by mutableStateOf(MyItmoWebState.Loading)
        private set

    /** The WebView while a host shows it. */
    val page: WebView? get() = web

    /**
     * Restores what [save] wrote, if anything, and shows the failure or the loading line again; call it before the
     * first composition of a host's view creates the WebView.
     */
    fun restore(state: Bundle?) {
        lastTrustedUrl = state?.getString(STATE_URL)?.takeIf(MyItmoWebPolicy::isInternal) ?: lastTrustedUrl
        failed = state?.getBoolean(STATE_ERROR, failed) ?: failed
        state?.getBundle(STATE_BROWSER)?.let { browserState = it }
        screenState = if (failed) MyItmoWebState.Failed else MyItmoWebState.Loading
    }

    /** The history of the shown WebView, or the one saved when it was released, with the page and the failure. */
    fun save(): Bundle {
        web?.let { page -> browserState = Bundle().also { page.saveState(it) } }
        return Bundle().apply {
            putBundle(STATE_BROWSER, browserState)
            putString(STATE_URL, lastTrustedUrl)
            putBoolean(STATE_ERROR, failed)
        }
    }

    fun create(context: Context): WebView = WebView(context).also { page ->
        web = page
        configure(page, context)
        onBrowserCreated(page)
        val saved = browserState
        if (!failed && (saved == null || page.restoreState(saved) == null)) loadPage()
    }

    fun loadPage() {
        val page = web ?: return
        failed = false
        screenState = MyItmoWebState.Loading
        if (page.url == lastTrustedUrl) page.reload()
        else page.loadUrl(lastTrustedUrl)
    }

    /** Opens the home page outside the app, for the menu's "open in the browser" item. */
    fun openHome() = openExternal(MyItmoWebPolicy.HOME_URL)

    /** One step back in the browser's history; false when there is none and Back leaves the screen. */
    fun goBack(): Boolean {
        val page = web
        if (page == null || !page.canGoBack()) return false
        failed = false
        page.goBack()
        return true
    }

    fun onResume() { web?.onResume() }
    fun onPause() { web?.onPause() }

    /**
     * Saves the history and destroys [page] once: when the composition disposes the slot (the view lifecycle's
     * ON_DESTROY, before the host's view goes), or through [releaseShown] if it has not. Never on a recomposition.
     */
    fun release(page: WebView) {
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

    /** Releases the shown WebView, if the composition has not. */
    fun releaseShown() {
        web?.let(::release)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configure(page: WebView, context: Context) = with(page) {
        setBackgroundColor(context.color.surface)
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

    private fun showError() {
        failed = true
        screenState = MyItmoWebState.Failed
    }

    private companion object {
        const val STATE_BROWSER = "my_itmo_browser"
        const val STATE_URL = "my_itmo_url"
        const val STATE_ERROR = "my_itmo_error"
    }
}
