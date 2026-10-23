package dev.alllexey.itmowidgets.feature.connections.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dev.alllexey.itmowidgets.core.url.HttpsNavigationPolicy
import dev.alllexey.itmowidgets.designsystem.components.bars.AppTopBar
import dev.alllexey.itmowidgets.designsystem.components.bars.AppTopBarAction
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_close
import dev.alllexey.itmowidgets.shared.feature.account.AccountStrings
import org.jetbrains.compose.resources.painterResource

/**
 * The official ITMO.ID page for a client, full screen (UX spec section 5.3). The WebView runs in its own `androidx.webkit`
 * profile [PROFILE], created empty and deleted when the page goes, so neither the app's cookies reach the client's
 * sign-in nor the client's reach the app (plan 5.4). Every main-frame navigation goes to [onNavigation]; the
 * callback it claims is never loaded. Without `MULTI_PROFILE` there is no fallback: [onUnsupported].
 */
@Composable
internal fun ClientSignInPage(
    url: String,
    userAgent: String,
    title: String,
    code: String?,
    onNavigation: (String) -> Boolean,
    onClose: () -> Unit,
    onUnsupported: () -> Unit,
) {
    val supported = remember { WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) }
    LaunchedEffect(supported) { if (!supported) onUnsupported() }
    if (!supported) return
    val navigation by rememberUpdatedState(onNavigation)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler(onBack = onClose)
        Column(Modifier.fillMaxSize().background(ItmoTheme.colorScheme.surface).testTag(PairingTestTags.SIGN_IN)) {
            AppTopBar(
                title,
                modifier = Modifier.statusBarsPadding(),
                navigation = { AppTopBarAction(painterResource(Res.drawable.ic_close), text(CoreStrings.common_close), onClose) },
                subtitle = text(AccountStrings.pair_sign_in_host),
            )
            code?.let { CodeStrip(it) }
            AndroidView(
                factory = { context -> isolatedWebView(context, userAgent) { navigation(it) }.also { it.loadUrl(url) } },
                modifier = Modifier.fillMaxWidth().weight(1f).navigationBarsPadding(),
                onRelease = ::release,
            )
        }
    }
}

/** On a renewal the code to compare stays on top of the page. */
@Composable
private fun CodeStrip(code: String) = Row(
    Modifier
        .fillMaxWidth()
        .padding(horizontal = ItmoTheme.spacing.screenMargin, vertical = ItmoTheme.spacing.related)
        .background(ItmoTheme.colorScheme.surfaceContainerHigh, ItmoTheme.shapes.medium)
        .padding(ItmoTheme.spacing.content),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(text(AccountStrings.pair_code_title), style = ItmoTheme.typography.bodyMedium, color = ItmoTheme.colorScheme.onSurface)
    Text(code, style = ItmoTheme.typography.titleLarge, fontFamily = FontFamily.Monospace, color = ItmoTheme.colorScheme.onSurface)
}

@SuppressLint("SetJavaScriptEnabled")
private fun isolatedWebView(context: Context, userAgent: String, onNavigation: (String) -> Boolean): WebView {
    // A profile left by a killed process still holds that sign-in's cookies: start empty.
    runCatching { ProfileStore.getInstance().deleteProfile(PROFILE) }
    ProfileStore.getInstance().getOrCreateProfile(PROFILE)
    return WebView(context).apply {
        WebViewCompat.setProfile(this, PROFILE)
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_NO_CACHE
            userAgentString = userAgent
        }
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                val target = request.url.toString()
                return onNavigation(target) || !HttpsNavigationPolicy.isNavigable(target)
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // A redirect the client hook did not see still never loads the callback page.
                if (url != null && onNavigation(url)) view.stopLoading()
            }
        }
    }
}

private fun release(page: WebView) {
    page.stopLoading()
    page.loadUrl("about:blank")
    page.clearHistory()
    page.destroy()
    runCatching { ProfileStore.getInstance().deleteProfile(PROFILE) }
}

/** The one isolated profile; deleted after each sign-in. */
private const val PROFILE = "itmo-client-sign-in"
