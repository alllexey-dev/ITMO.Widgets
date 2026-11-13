package dev.alllexey.itmowidgets.feature.recordbook.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
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
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.url.StrictUri
import dev.alllexey.itmowidgets.databinding.ActivityLoginBinding
import dev.alllexey.itmowidgets.feature.recordbook.domain.lms.LmsScore
import dev.alllexey.itmowidgets.feature.recordbook.presentation.LmsLoginError
import dev.alllexey.itmowidgets.feature.recordbook.presentation.LmsLoginEvent
import dev.alllexey.itmowidgets.feature.recordbook.presentation.LmsLoginUiState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.LmsLoginViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.androidx.viewmodel.ext.android.viewModel

data class LmsLoginInput(val subject: String, val studyYear: String, val semester: Int)

/** A foreground LMS check; credentials and the fetched scores remain in memory for this screen only. */
class LmsLoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LmsLoginViewModel by viewModel()
    private var loadedAttempt = 0

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
        binding.loginToolbar.setTitle(R.string.recordbook_lms_title)
        binding.loginToolbar.setNavigationOnClickListener { finish() }
        binding.loginSwipeRefresh.isEnabled = false
        configureWebView()
        binding.loginRetryButton.setOnClickListener { retry() }

        viewModel.uiState.flowWithLifecycle(lifecycle).onEach(::renderState).launchIn(lifecycleScope)
        viewModel.events.flowWithLifecycle(lifecycle).onEach { event ->
            when (event) {
                is LmsLoginEvent.Completed -> {
                    setResult(RESULT_OK, Intent().putExtra(EXTRA_SCORES, encodeScores(event.scores)))
                    finish()
                }
            }
        }.launchIn(lifecycleScope)
    }

    private fun retry() {
        val clearCookies = (viewModel.uiState.value as? LmsLoginUiState.Error)?.clearCookiesOnRetry == true
        if (clearCookies) {
            CookieManager.getInstance().removeAllCookies {
                runOnUiThread { if (!isFinishing && !isDestroyed) viewModel.start() }
            }
        } else {
            viewModel.start()
        }
    }

    private fun renderState(state: LmsLoginUiState) {
        when (state) {
            LmsLoginUiState.Checking, LmsLoginUiState.Loading -> {
                binding.loginCompletingProgress.isVisible = true
                binding.loginErrorContainer.isVisible = false
                binding.loginWebView.isVisible = false
            }
            is LmsLoginUiState.Web -> {
                binding.loginCompletingProgress.isVisible = false
                binding.loginErrorContainer.isVisible = false
                binding.loginWebView.isVisible = true
                if (loadedAttempt != state.attempt) {
                    loadedAttempt = state.attempt
                    binding.loginWebView.loadUrl(viewModel.loginUrl)
                }
            }
            is LmsLoginUiState.Error -> {
                binding.loginCompletingProgress.isVisible = false
                binding.loginWebView.isVisible = false
                binding.loginErrorContainer.isVisible = true
                binding.loginErrorText.setText(errorMessage(state.reason))
            }
        }
    }

    private fun errorMessage(reason: LmsLoginError): Int = when (reason) {
        LmsLoginError.NETWORK_OR_UNKNOWN -> R.string.recordbook_lms_error
        LmsLoginError.WRONG_ACCOUNT -> R.string.recordbook_lms_wrong_account
        LmsLoginError.DEMO -> R.string.recordbook_lms_demo
        LmsLoginError.EMPTY_COURSES -> R.string.recordbook_lms_empty
        LmsLoginError.NO_MATCH -> R.string.recordbook_lms_no_match
        LmsLoginError.COURSE_LOAD_FAILED -> R.string.recordbook_lms_course_error
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
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
                val url = request.url.toString()
                if (!request.isForMainFrame) return !viewModel.isAllowedBrowserUrl(url)
                return viewModel.onNavigation(url)
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                if (url == null || viewModel.onNavigation(url)) view.stopLoading()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) viewModel.onWebError()
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame) viewModel.onWebError()
            }

            override fun onPageFinished(view: WebView, url: String?) {
                val parsed = url?.let(StrictUri::parse) ?: return
                if (parsed.scheme.equals("https", ignoreCase = true) &&
                    parsed.rawUserInfo == null &&
                    parsed.host == SSO_HOST &&
                    parsed.rawPath == SSO_AUTH_PATH
                ) {
                    // Keycloak's provider link is rendered asynchronously; leave the page visible if it is absent.
                    view.postDelayed({
                        val current = view.url?.let(StrictUri::parse)
                        if (!isFinishing && !isDestroyed &&
                            current?.rawUserInfo == null &&
                            current?.host == SSO_HOST &&
                            current.rawPath == SSO_AUTH_PATH
                        ) {
                            view.evaluateJavascript(
                                """(function(){const a=[...document.querySelectorAll('a')].find(x=>{try{const u=new URL(x.href);return u.protocol==='https:'&&u.host==='sso.tuxedo.se.ifmo.ru'&&u.pathname==='/realms/Newdex/broker/itmo-id/login'}catch(e){return false}});if(a)a.click()})()""",
                                null,
                            )
                        }
                    }, 1200)
                }
            }
        }
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

    @Serializable
    private data class ScorePayload(
        val name: String,
        val earned: Double? = null,
        val max: Double,
        val status: String? = null,
    )

    companion object {
        const val EXTRA_SUBJECT = LmsLoginViewModel.EXTRA_SUBJECT
        const val EXTRA_YEAR = LmsLoginViewModel.EXTRA_YEAR
        const val EXTRA_SEMESTER = LmsLoginViewModel.EXTRA_SEMESTER
        const val EXTRA_SCORES = "lms_scores"

        private const val SSO_HOST = "sso.tuxedo.se.ifmo.ru"
        private const val SSO_AUTH_PATH = "/realms/Newdex/protocol/openid-connect/auth"

        fun createIntent(context: Context, input: LmsLoginInput): Intent =
            Intent(context, LmsLoginActivity::class.java)
                .putExtra(EXTRA_SUBJECT, input.subject)
                .putExtra(EXTRA_YEAR, input.studyYear)
                .putExtra(EXTRA_SEMESTER, input.semester)

        fun encodeScores(scores: List<LmsScore>): String = Json.encodeToString(
            scores.map { ScorePayload(it.name, it.earned, it.max, it.verificationStatus) },
        )

        fun decodeScores(intent: Intent?): List<LmsScore>? {
            val raw = intent?.getStringExtra(EXTRA_SCORES) ?: return null
            return runCatching {
                Json.decodeFromString<List<ScorePayload>>(raw)
                    .map { LmsScore(it.name, it.earned, it.max, it.status) }
            }.getOrNull()
        }
    }
}

internal class LmsLoginContract : ActivityResultContract<LmsLoginInput, List<LmsScore>?>() {
    override fun createIntent(context: Context, input: LmsLoginInput): Intent =
        LmsLoginActivity.createIntent(context, input)

    override fun parseResult(resultCode: Int, intent: Intent?): List<LmsScore>? =
        if (resultCode == android.app.Activity.RESULT_OK) LmsLoginActivity.decodeScores(intent) else null
}
