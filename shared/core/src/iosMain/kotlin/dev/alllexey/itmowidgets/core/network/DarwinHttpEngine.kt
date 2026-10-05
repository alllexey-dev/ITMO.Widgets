package dev.alllexey.itmowidgets.core.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import platform.Foundation.NSHTTPCookieAcceptPolicy
import platform.Foundation.NSURLRequestReloadIgnoringLocalCacheData
import platform.Foundation.NSURLSessionConfiguration

/**
 * The Ktor engine of every iOS client (MyITMO, BARS, Backend): URLSession without cookie storage, cookie handling
 * or URL cache, as SP-15a/SP-15b measured. Cookies are read from responses with Ktor's `setCookie()` and sent as an
 * explicit `Cookie` header by the caller; nothing persists between requests or clients.
 *
 * The client still sets `followRedirects = false` where a redirect must surface (ML-03): the engine never follows,
 * Ktor's redirect plugin does. Never `usePreconfiguredSession`, which bypasses these settings.
 */
fun darwinHttpEngine(): HttpClientEngine = Darwin.create {
    configureSession { statelessSession() }
}

/** The URLSession settings of [darwinHttpEngine]. */
internal fun NSURLSessionConfiguration.statelessSession() {
    HTTPCookieStorage = null
    HTTPShouldSetCookies = false
    HTTPCookieAcceptPolicy = NSHTTPCookieAcceptPolicy.NSHTTPCookieAcceptPolicyNever
    URLCache = null
    requestCachePolicy = NSURLRequestReloadIgnoringLocalCacheData
}
