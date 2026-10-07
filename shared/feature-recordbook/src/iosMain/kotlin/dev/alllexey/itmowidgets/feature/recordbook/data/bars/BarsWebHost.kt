package dev.alllexey.itmowidgets.feature.recordbook.data.bars

import dev.alllexey.itmoapi.bars.auth.BarsLogin

/**
 * What the iOS BARS session needs from WebKit, which only Swift reaches: the cookies of `WKWebsiteDataStore.default()`
 * and a hidden `WKWebView` on that store. The app's `IosPlatform` implements it once; Kotlin calls it on the main
 * thread.
 */
interface BarsWebHost : WebKitCookieSource, HiddenBarsWebView

/** The cookies the app's WebViews hold. */
fun interface WebKitCookieSource {

    /** Hands [completion] every cookie of `WKWebsiteDataStore.default()`; calls it once, on the main thread. */
    fun webKitCookies(completion: (List<WebKitCookie>) -> Unit)
}

/** A `WKWebView` that is never shown, on `WKWebsiteDataStore.default()`: it signs in with the ITMO.ID session. */
fun interface HiddenBarsWebView {

    /**
     * Loads [url] and asks [navigation] about every main-frame URL, server redirects included; other frames load only
     * over https. Calls [completion] once, on the main thread: with the callback URL when [navigation] answers
     * [BarsWebStep.CALLBACK] (that navigation is cancelled, so the callback never loads), or with null when a page
     * finished loading (ITMO.ID wants the user), a load failed or [navigation] answered [BarsWebStep.STOP]. The
     * returned load stops the view; after [BarsWebLoad.cancel] [completion] is not called.
     */
    fun loadHiddenBarsPage(url: String, navigation: BarsWebNavigation, completion: (String?) -> Unit): BarsWebLoad
}

/** One [HiddenBarsWebView.loadHiddenBarsPage]. */
interface BarsWebLoad {

    /** Stops the view and releases it; safe to call more than once and from any thread. */
    fun cancel()
}

/** What a hidden BARS sign-in does with a main-frame URL. */
enum class BarsWebStep {
    /** An ITMO.ID page or a redirect on the way: let it load. Not `LOAD`, which Objective-C reserves (`+load`). */
    ALLOW,

    /** The exact BARS callback: cancel the navigation and hand the URL over. */
    CALLBACK,

    /** Anything else: cancel and give up. */
    STOP,
}

/** Android's `BarsWebSilentLogin.handle` over [BarsLogin]'s strict checks (ADR 0012). */
class BarsWebNavigation internal constructor(private val login: BarsLogin) {

    fun step(url: String): BarsWebStep = when {
        login.isCallback(url) -> BarsWebStep.CALLBACK
        login.isAllowedPage(url) -> BarsWebStep.ALLOW
        else -> BarsWebStep.STOP
    }
}

/**
 * One `HTTPCookie` of WebKit as Swift copies it. [domain] is WebKit's: a leading dot for a `Domain` cookie, none for a
 * host-only one; [expiresAtEpochMillis] is null for a session cookie. The value never appears in [toString].
 */
class WebKitCookie(
    val name: String,
    val value: String,
    val domain: String,
    val path: String,
    val secure: Boolean,
    val expiresAtEpochMillis: Long?,
) {
    override fun toString(): String = "WebKitCookie($name @$domain$path)"
}
