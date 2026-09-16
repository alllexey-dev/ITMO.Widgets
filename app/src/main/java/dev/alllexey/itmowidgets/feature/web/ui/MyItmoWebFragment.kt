package dev.alllexey.itmowidgets.feature.web.ui

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
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
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.ui.navigation.closeScreen
import dev.alllexey.itmowidgets.core.util.color
import dev.alllexey.itmowidgets.databinding.FragmentMyItmoWebBinding
import dev.alllexey.itmowidgets.feature.web.domain.MyItmoWebPolicy

/** Official browser session only: no native token injection, JavaScript bridge or console logging. */
open class MyItmoWebFragment : Fragment() {
    private var _binding: FragmentMyItmoWebBinding? = null
    private val binding get() = _binding!!
    private var browserState: Bundle? = null
    private var browserBack: OnBackPressedCallback? = null
    private var failed = false
    private var lastTrustedUrl = MyItmoWebPolicy.HOME_URL

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMyItmoWebBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.setNavigationOnClickListener { browserBack?.isEnabled = false; closeScreen() }
        binding.toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.web_reload -> { loadPage(); true }
                R.id.web_external -> { openExternal(MyItmoWebPolicy.HOME_URL); true }
                else -> false
            }
        }
        binding.stateAction.setOnClickListener { loadPage() }
        configureBrowser()
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) {
                    failed = false
                    binding.webView.goBack()
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
        val savedBrowser = savedInstanceState?.getBundle(STATE_BROWSER) ?: browserState
        if (failed) showError()
        else if (savedBrowser == null || binding.webView.restoreState(savedBrowser) == null) loadPage()
    }

    private fun loadPage() {
        failed = false
        showLoading()
        if (binding.webView.url == lastTrustedUrl) binding.webView.reload()
        else binding.webView.loadUrl(lastTrustedUrl)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureBrowser() = with(binding.webView) {
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
                if (_binding?.webView !== view) return
                if (url == null || !MyItmoWebPolicy.isInternal(url)) {
                    view.stopLoading()
                    showError()
                    return
                }
                lastTrustedUrl = url
                binding.toolbar.subtitle = url.toUri().host
                // WebView can deliver HTTP failure before onPageStarted. Only an explicit
                // retry or Back clears that failure; a late callback must not hide the error.
                if (!failed) showLoading()
            }

            override fun onPageFinished(view: WebView, url: String?) {
                if (_binding?.webView !== view || failed) return
                binding.loading.visibility = View.INVISIBLE
                binding.webView.isVisible = true
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (_binding?.webView === view && request.isForMainFrame) showError()
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                if (_binding?.webView === view && request.isForMainFrame) showError()
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                if (_binding?.webView === view) showError()
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean = true

            override fun onProgressChanged(view: WebView, newProgress: Int) {
                if (_binding?.webView === view && !failed) {
                    binding.loading.visibility = if (newProgress < 100) View.VISIBLE else View.INVISIBLE
                }
            }
        }
    }

    /** Debug hosts replace transport with synthetic HTML while retaining the real browser lifecycle. */
    protected open fun interceptRequest(request: WebResourceRequest): WebResourceResponse? = null

    private fun showLoading() = with(binding) {
        loading.visibility = View.VISIBLE
        stateContainer.isVisible = false
        webView.isVisible = true
    }

    private fun showError() {
        failed = true
        _binding?.apply {
            loading.visibility = View.INVISIBLE
            webView.isVisible = false
            stateContainer.isVisible = true
        }
    }

    private fun openExternal(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: ActivityNotFoundException) {
            _binding?.let { Snackbar.make(it.root, R.string.link_open_failed, Snackbar.LENGTH_LONG).show() }
        }
    }

    override fun onResume() { super.onResume(); binding.webView.onResume() }
    override fun onPause() { binding.webView.onPause(); super.onPause() }

    override fun onSaveInstanceState(outState: Bundle) {
        _binding?.webView?.let { web -> browserState = Bundle().also { web.saveState(it) } }
        outState.putBundle(STATE_BROWSER, browserState)
        outState.putString(STATE_URL, lastTrustedUrl)
        outState.putBoolean(STATE_ERROR, failed)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        val web = binding.webView
        browserState = Bundle().also { web.saveState(it) }
        web.stopLoading()
        web.webViewClient = WebViewClient()
        web.webChromeClient = null
        (web.parent as? ViewGroup)?.removeView(web)
        web.removeAllViews()
        web.destroy()
        browserBack?.remove()
        browserBack = null
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val STATE_BROWSER = "my_itmo_browser"
        const val STATE_URL = "my_itmo_url"
        const val STATE_ERROR = "my_itmo_error"
    }
}
