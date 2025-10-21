package dev.alllexey.itmowidgets.ui.web

import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.WebView
import androidx.fragment.app.Fragment
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import api.myitmo.model.TokenResponse
import dev.alllexey.itmowidgets.ItmoWidgetsApp
import dev.alllexey.itmowidgets.R

class WebFragment : Fragment(R.layout.fragment_web), WebViewListener {

    private lateinit var webViewManager: WebViewManager
    private lateinit var webView: WebView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        webView = view.findViewById(R.id.web_view)
        val swipeRefreshLayout: SwipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout_web)
        val appContainer = (requireActivity().application as ItmoWidgetsApp).appContainer

        swipeRefreshLayout.setOnRefreshListener {
            webView.reload()
        }

        swipeRefreshLayout.setOnChildScrollUpCallback { parent, child ->
            webView.scrollY > 0
        }

        view.post {
            webViewManager = WebViewManager(requireContext(), webView, swipeRefreshLayout, this, true)
            if (webView.url == null) {
                webViewManager.loadUrlWithAuth(appContainer)
            }
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
        appContainer.myItmoStorage.update(tokenResponse)
        Log.d(TAG, "Successfully updated tokens from WebFragment.")
    }

    companion object {
        private const val TAG = "WebFragment"
    }
}