package dev.alllexey.itmowidgets.core.url

/**
 * What a sign-in WebView may navigate to: any https page, so that ITMO.ID can
 * hand over to VK or another provider and come back. Which page may complete
 * a sign-in stays a separate, exact check in each flow.
 */
object HttpsNavigationPolicy {

    fun isNavigable(url: String): Boolean {
        val uri = StrictUri.parse(url) ?: return false
        return uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
    }
}
