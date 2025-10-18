package me.alllexey123.itmowidgets.ui.web

import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.WebView
import androidx.fragment.app.Fragment
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import api.myitmo.model.TokenResponse
import me.alllexey123.itmowidgets.ItmoWidgetsApp
import me.alllexey123.itmowidgets.R

class WebFragment : Fragment(R.layout.fragment_web), WebViewListener {

    private lateinit var webViewManager: WebViewManager
    private lateinit var webView: WebView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        webView = view.findViewById(R.id.web_view)
        val swipeRefreshLayout: SwipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout_web)
        val appContainer = (requireActivity().application as ItmoWidgetsApp).appContainer

        webViewManager = WebViewManager(requireContext(), webView, swipeRefreshLayout, this)

        swipeRefreshLayout.setOnRefreshListener {
            webView.reload()
        }

        if (webView.url == null) {
            webViewManager.loadUrlWithAuth(appContainer.storage)
        }
    }

    override fun onTokensReceived(tokensResponseString: String) {
        activity?.runOnUiThread {
            saveTokensToAppStorage(tokensResponseString)
        }
    }

    private fun saveTokensToAppStorage(tokensResponseString: String) {
        val appContainer = (requireActivity().application as ItmoWidgetsApp).appContainer
        val tokenResponse = appContainer.myItmo.gson.fromJson(tokensResponseString, TokenResponse::class.java)
        appContainer.storage.update(tokenResponse)
        Log.d(TAG, "Successfully updated tokens from WebFragment.")
    }

    companion object {
        private const val TAG = "WebFragment"
    }
}