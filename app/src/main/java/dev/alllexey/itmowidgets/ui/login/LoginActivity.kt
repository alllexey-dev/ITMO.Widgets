package dev.alllexey.itmowidgets.ui.login

import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import api.myitmo.model.TokenResponse
import dev.alllexey.itmowidgets.ItmoWidgetsApp
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.ui.web.WebViewListener
import dev.alllexey.itmowidgets.ui.web.WebViewManager

class LoginActivity : AppCompatActivity(), WebViewListener {

    private lateinit var webViewManager: WebViewManager
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(left = systemBars.left, top = systemBars.top, right = systemBars.right)
            insets
        }

        webView = findViewById(R.id.web_view)
        val swipeRefreshLayout: SwipeRefreshLayout = findViewById(R.id.swipe_refresh_layout_login)

        webViewManager = WebViewManager(this, webView, swipeRefreshLayout, this, false)

        swipeRefreshLayout.setOnRefreshListener {
            webView.reload()
        }

        swipeRefreshLayout.setOnChildScrollUpCallback { parent, child ->
            webView.scrollY > 0
        }

        webViewManager.loadUrlWithCleanState()
    }

    override fun onTokensReceived(tokensResponseString: String) {
        runOnUiThread {
            saveTokensToAppStorage(tokensResponseString)
            setResult(RESULT_OK)
            finish()
        }
    }

    private fun saveTokensToAppStorage(tokensResponseString: String) {
        val appContainer = (application as ItmoWidgetsApp).appContainer
        val tokenResponse = appContainer.myItmo.gson.fromJson(tokensResponseString, TokenResponse::class.java)
        appContainer.myItmoStorage.update(tokenResponse)
        Log.d(TAG, "Successfully updated tokens from LoginActivity.")
    }

    companion object {
        private const val TAG = "LoginActivity"
    }
}