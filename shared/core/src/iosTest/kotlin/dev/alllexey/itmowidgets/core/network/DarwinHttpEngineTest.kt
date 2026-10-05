package dev.alllexey.itmowidgets.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import platform.Foundation.NSHTTPCookieAcceptPolicy
import platform.Foundation.NSURLRequestReloadIgnoringLocalCacheData
import platform.Foundation.NSURLSessionConfiguration

class DarwinHttpEngineTest {

    @Test
    fun theSessionKeepsNoCookiesAndNoCache() {
        val configuration = NSURLSessionConfiguration.defaultSessionConfiguration

        configuration.statelessSession()

        assertNull(configuration.HTTPCookieStorage)
        assertFalse(configuration.HTTPShouldSetCookies)
        assertEquals(NSHTTPCookieAcceptPolicy.NSHTTPCookieAcceptPolicyNever, configuration.HTTPCookieAcceptPolicy)
        assertNull(configuration.URLCache)
        assertEquals(NSURLRequestReloadIgnoringLocalCacheData, configuration.requestCachePolicy)
    }

    @Test
    fun createsTheEngine() {
        darwinHttpEngine().close()
    }
}
